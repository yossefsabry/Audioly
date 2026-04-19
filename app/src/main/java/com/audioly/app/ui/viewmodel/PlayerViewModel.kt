package com.audioly.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioly.app.data.cache.SubtitleCacheManager
import com.audioly.app.data.preferences.UserPreferences
import com.audioly.app.data.preferences.UserPreferencesRepository
import com.audioly.app.network.AppHttpClient
import com.audioly.app.player.PlayerRepository
import com.audioly.app.player.QueueItem
import com.audioly.app.player.RepeatMode
import com.audioly.app.player.SubtitleCue
import com.audioly.app.player.SubtitleManager
import com.audioly.app.player.VttParser
import com.audioly.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * ViewModel for PlayerScreen. Manages subtitle loading/parsing, speed/subtitle
 * menu state, and delegates playback commands to PlayerRepository.
 */
class PlayerViewModel(
    val playerRepository: PlayerRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val subtitleCacheManager: SubtitleCacheManager,
    private val youTubeExtractor: com.audioly.app.extraction.YouTubeExtractor? = null,
    private val trackRepository: com.audioly.app.data.repository.TrackRepository? = null,
) : ViewModel() {

    val playerState = playerRepository.state
    val subtitleTracks = playerRepository.subtitleTracks
    val subtitleContent = playerRepository.subtitleContent

    val prefs: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    // Queue state (delegated from PlayerRepository)
    val queue = playerRepository.queue
    val queueIndex = playerRepository.queueIndex
    val repeatMode = playerRepository.repeatMode
    val shuffleEnabled = playerRepository.shuffleEnabled

    // Subtitle management per video
    private var subtitleManager = SubtitleManager()

    private val _subtitleCues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    val subtitleCues: StateFlow<List<SubtitleCue>> = _subtitleCues.asStateFlow()

    private val _activeCueIndex = MutableStateFlow(-1)
    val activeCueIndex: StateFlow<Int> = _activeCueIndex.asStateFlow()

    val showSubtitles: StateFlow<Boolean> = combine(
        _subtitleCues,
        playerState,
    ) { cues, state ->
        cues.isNotEmpty() && state.selectedSubtitleLanguage.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Watch for video changes — reset subtitle manager
        viewModelScope.launch {
            playerState.collectLatest { state ->
                val videoId = state.videoId
                if (videoId != null && videoId != currentVideoId) {
                    currentVideoId = videoId
                    subtitleManager = SubtitleManager()
                    _subtitleCues.value = emptyList()
                    _activeCueIndex.value = -1
                }
                // Update active cue index on position changes
                val idx = subtitleManager.activeIndex(state.positionMs)
                if (idx != _activeCueIndex.value) {
                    _activeCueIndex.value = idx
                    playerRepository.setSubtitleIndex(idx)
                }
            }
        }

        // Auto-select subtitle language when tracks arrive
        viewModelScope.launch {
            combine(subtitleTracks, playerState, prefs) { tracks, state, prefs ->
                Triple(tracks, state, prefs)
            }.collectLatest { (tracks, state, prefs) ->
                if (tracks.isNotEmpty() && state.selectedSubtitleLanguage.isEmpty()) {
                    val preferred = prefs.preferredSubtitleLanguage
                    val match = if (preferred.isNotEmpty()) {
                        tracks.firstOrNull { it.languageCode == preferred }
                    } else null
                    playerRepository.setSubtitleLanguage(
                        match?.languageCode ?: tracks.first().languageCode
                    )
                }
            }
        }

        // Parse VTT when content or language changes
        viewModelScope.launch {
            combine(playerState, subtitleContent) { state, contentMap ->
                state.selectedSubtitleLanguage to contentMap
            }.collectLatest { (lang, contentMap) ->
                val vtt = contentMap[lang]
                if (vtt != null) {
                    val cues = withContext(Dispatchers.Default) { VttParser.parse(vtt) }
                    _subtitleCues.value = cues
                    subtitleManager.load(cues)
                } else {
                    _subtitleCues.value = emptyList()
                    subtitleManager.load(emptyList())
                }
            }
        }

        // Download VTT when language changes
        viewModelScope.launch {
            playerState.collectLatest { state ->
                val lang = state.selectedSubtitleLanguage
                if (lang.isBlank()) return@collectLatest
                if (subtitleContent.value.containsKey(lang)) return@collectLatest

                val videoId = state.videoId ?: return@collectLatest
                val track = subtitleTracks.value.firstOrNull { it.languageCode == lang }
                    ?: return@collectLatest

                // Try disk cache first
                val cached = try {
                    withContext(Dispatchers.IO) { subtitleCacheManager.load(videoId, lang) }
                } catch (_: Exception) { null }

                if (cached != null) {
                    playerRepository.addSubtitleContent(lang, cached)
                    return@collectLatest
                }

                // Download from URL
                val content = downloadVttContent(track.url) ?: return@collectLatest

                // Cache for future use
                try {
                    withContext(Dispatchers.IO) {
                        subtitleCacheManager.save(
                            videoId = videoId,
                            languageCode = lang,
                            languageName = track.languageName,
                            format = track.format,
                            isAutoGenerated = track.isAutoGenerated,
                            content = content,
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to cache subtitle: ${e.message}")
                }

                playerRepository.addSubtitleContent(lang, content)
            }
        }

        // Handle queue advance requests (tracks that need extraction)
        viewModelScope.launch {
            playerRepository.queueAdvanceRequests.collect { item ->
                handleQueueExtraction(item)
            }
        }

        // Periodically save playback position for resume
        viewModelScope.launch {
            while (true) {
                delay(POSITION_SAVE_INTERVAL_MS)
                saveCurrentPosition()
            }
        }
    }

    private var currentVideoId: String? = null

    // ─── Playback commands (delegate to PlayerRepository) ─────────────────────

    fun togglePlayPause() = playerRepository.togglePlayPause()
    fun seekTo(positionMs: Long) = playerRepository.seekTo(positionMs)
    fun skipForward(intervalMs: Long) = playerRepository.skipForward(intervalMs)
    fun skipBack(intervalMs: Long) = playerRepository.skipBack(intervalMs)
    fun setSpeed(speed: Float) = playerRepository.setSpeed(speed)
    fun setSubtitleLanguage(languageCode: String) = playerRepository.setSubtitleLanguage(languageCode)

    // ─── Queue commands ──────────────────────────────────────────────────────

    fun skipToNext() = playerRepository.skipToNext()
    fun skipToPrevious() = playerRepository.skipToPrevious()
    fun toggleRepeatMode() = playerRepository.toggleRepeatMode()
    fun toggleShuffle() = playerRepository.toggleShuffle()
    fun removeFromQueue(index: Int) = playerRepository.removeFromQueue(index)

    /**
     * Save current playback position to DB for resume.
     * Skips save if near start (< 5s) or near end (last 5% or < 5s remaining).
     */
    private suspend fun saveCurrentPosition() {
        val repo = trackRepository ?: return
        val state = playerState.value
        val videoId = state.videoId ?: return
        val posMs = state.positionMs
        val durMs = state.durationMs
        // Don't save trivial positions or near-end positions (treat as "completed")
        if (posMs < 5_000L) {
            repo.saveLastPosition(videoId, 0L)
            return
        }
        if (durMs > 0 && (durMs - posMs) < (durMs * 0.05).toLong().coerceAtLeast(5_000L)) {
            repo.saveLastPosition(videoId, 0L)
            return
        }
        try {
            repo.saveLastPosition(videoId, posMs)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to save position: ${e.message}")
        }
    }

    /** Save position when leaving the player. Call from onCleared or screen exit. */
    override fun onCleared() {
        // Fire-and-forget: save final position
        val repo = trackRepository
        val state = playerState.value
        val videoId = state.videoId
        if (repo != null && videoId != null && state.positionMs > 5_000L) {
            // Can't use viewModelScope (it's cancelled), use the repository's scope pattern
            // instead save synchronously via a scope we create briefly
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
                try {
                    val durMs = state.durationMs
                    val posMs = state.positionMs
                    val nearEnd = durMs > 0 && (durMs - posMs) < (durMs * 0.05).toLong().coerceAtLeast(5_000L)
                    repo.saveLastPosition(videoId, if (nearEnd) 0L else posMs)
                } catch (_: Exception) { /* best effort */ }
            }
        }
        super.onCleared()
    }

    private suspend fun handleQueueExtraction(item: QueueItem) {
        val extractor = youTubeExtractor ?: return
        try {
            val url = "https://www.youtube.com/watch?v=${item.videoId}"
            AppLogger.i(TAG, "Queue advance: extracting ${item.videoId}")
            val result = extractor.extract(url)
            if (result is com.audioly.app.extraction.ExtractionResult.Success) {
                val info = result.streamInfo
                try {
                    trackRepository?.upsertFromExtraction(info)
                    trackRepository?.setAudioStreamUrl(info.videoId, info.audioStreamUrl)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to save track to DB", e)
                }
                playerRepository.clearSubtitles()
                playerRepository.setSubtitleTracks(info.subtitleTracks)
                playerRepository.load(
                    audioUrl = info.audioStreamUrl,
                    videoId = info.videoId,
                    title = info.title,
                    uploader = info.uploader,
                    thumbnailUrl = info.thumbnailUrl,
                    durationMs = info.durationSeconds * 1000L,
                )
            } else {
                AppLogger.w(TAG, "Queue advance extraction failed for ${item.videoId}")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Queue advance error", e)
        }
    }

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val POSITION_SAVE_INTERVAL_MS = 10_000L

        private suspend fun downloadVttContent(url: String): String? =
            suspendCancellableCoroutine { cont ->
                val request = okhttp3.Request.Builder().url(url).build()
                val call = AppHttpClient.subtitleClient.newCall(request)

                cont.invokeOnCancellation { call.cancel() }

                call.enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: IOException) {
                        if (!cont.isCancelled) {
                            AppLogger.w(TAG, "Failed to download subtitle: ${e.message}")
                        }
                        cont.resumeWith(Result.success(null))
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        val body = response.use {
                            if (it.isSuccessful) it.body?.string() else null
                        }
                        cont.resumeWith(Result.success(body))
                    }
                })
            }
    }
}
