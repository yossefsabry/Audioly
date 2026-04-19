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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    /**
     * Set to true when the user explicitly selects "Off" for subtitles.
     * Cleared on video change so auto-select fires for the new video.
     */
    private var userDisabledSubtitles = false

    private var currentVideoId: String? = null

    /** Tracks in-flight subtitle downloads to prevent duplicate concurrent requests. */
    private val downloadingLangs = mutableSetOf<String>()

    val showSubtitles: StateFlow<Boolean> = combine(
        _subtitleCues,
        playerState,
    ) { cues, state ->
        cues.isNotEmpty() && state.selectedSubtitleLanguage.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True when subtitles are available (tracks extracted) even if not yet downloaded. */
    val hasSubtitleTracks: StateFlow<Boolean> = subtitleTracks
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Watch for video changes — reset subtitle manager and user preference
        viewModelScope.launch {
            playerState
                .map { it.videoId }
                .distinctUntilChanged()
                .collect { videoId ->
                    if (videoId != null && videoId != currentVideoId) {
                        currentVideoId = videoId
                        userDisabledSubtitles = false
                        subtitleManager = SubtitleManager()
                        _subtitleCues.value = emptyList()
                        _activeCueIndex.value = -1
                    }
                }
        }

        // Update active cue index on position changes (lightweight — no I/O)
        viewModelScope.launch {
            playerState.collect { state ->
                val idx = subtitleManager.activeIndex(state.positionMs)
                if (idx != _activeCueIndex.value) {
                    _activeCueIndex.value = idx
                    playerRepository.setSubtitleIndex(idx)
                }
            }
        }

        // Auto-select subtitle language when tracks first arrive for a new video.
        // Uses distinctUntilChanged to only fire when tracks or language actually change,
        // preventing the constant re-triggering that was overriding user's "Off" choice.
        viewModelScope.launch {
            combine(
                subtitleTracks,
                playerState.map { it.videoId to it.selectedSubtitleLanguage }.distinctUntilChanged(),
                prefs,
            ) { tracks, (_, lang), prefs ->
                Triple(tracks, lang, prefs)
            }.distinctUntilChanged()
                .collect { (tracks, lang, prefs) ->
                    if (tracks.isNotEmpty() && lang.isEmpty() && !userDisabledSubtitles) {
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

        // Parse VTT when content or selected language changes.
        // Use distinctUntilChanged on the language to avoid re-parsing on every position tick.
        viewModelScope.launch {
            combine(
                playerState.map { it.selectedSubtitleLanguage }.distinctUntilChanged(),
                subtitleContent,
            ) { lang, contentMap ->
                lang to contentMap
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

        // Download VTT when selected language changes.
        // KEY FIX: Use distinctUntilChanged on (videoId, lang) so position ticks
        // don't cancel the download via collectLatest.
        viewModelScope.launch {
            playerState
                .map { state -> state.videoId to state.selectedSubtitleLanguage }
                .distinctUntilChanged()
                .collectLatest { (videoId, lang) ->
                    if (lang.isBlank() || videoId == null) return@collectLatest
                    if (subtitleContent.value.containsKey(lang)) return@collectLatest

                    val track = subtitleTracks.value.firstOrNull { it.languageCode == lang }
                        ?: return@collectLatest

                    downloadAndCacheSubtitle(videoId, lang, track)
                }
        }

        // Eagerly pre-fetch the first subtitle track when tracks arrive (parallel with audio).
        viewModelScope.launch {
            combine(
                subtitleTracks,
                playerState.map { it.videoId }.distinctUntilChanged(),
            ) { tracks, videoId ->
                tracks to videoId
            }.distinctUntilChanged()
                .collect { (tracks, videoId) ->
                    if (tracks.isEmpty() || videoId == null) return@collect
                    val firstTrack = tracks.first()
                    if (subtitleContent.value.containsKey(firstTrack.languageCode)) return@collect
                    // Pre-fetch in background so it's ready when auto-select picks it
                    downloadAndCacheSubtitle(videoId, firstTrack.languageCode, firstTrack)
                }
        }

        // Handle queue advance requests (tracks that need extraction)
        viewModelScope.launch {
            playerRepository.queueAdvanceRequests.collectLatest { item ->
                handleQueueExtraction(item)
            }
        }

        // Periodically save playback position for resume (only when playing)
        viewModelScope.launch {
            while (true) {
                delay(POSITION_SAVE_INTERVAL_MS)
                if (playerState.value.isPlaying) {
                    saveCurrentPosition()
                }
            }
        }
    }

    // ─── Subtitle download helper ────────────────────────────────────────────

    private suspend fun downloadAndCacheSubtitle(
        videoId: String,
        lang: String,
        track: com.audioly.app.extraction.SubtitleTrack,
    ) {
        // Deduplicate: skip if already downloading this language
        synchronized(downloadingLangs) {
            if (!downloadingLangs.add(lang)) return
        }
        try {
            // Try disk cache first
            val cached = try {
                withContext(Dispatchers.IO) { subtitleCacheManager.load(videoId, lang) }
            } catch (_: Exception) { null }

            if (cached != null) {
                playerRepository.addSubtitleContent(lang, cached)
                return
            }

            // Download from URL
            if (track.url.isBlank()) {
                AppLogger.w(TAG, "Subtitle track has no URL for $lang — skipping download")
                return
            }
            AppLogger.d(TAG, "Downloading subtitle: $lang for $videoId — URL: ${track.url}")
            val content = downloadVttContent(track.url)
            if (content == null) {
                AppLogger.w(TAG, "Subtitle download returned null for $lang")
                return
            }

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
        } finally {
            synchronized(downloadingLangs) { downloadingLangs.remove(lang) }
        }
    }

    // ─── Playback commands (delegate to PlayerRepository) ─────────────────────

    fun togglePlayPause() = playerRepository.togglePlayPause()
    fun seekTo(positionMs: Long) = playerRepository.seekTo(positionMs)
    fun skipForward(intervalMs: Long) = playerRepository.skipForward(intervalMs)
    fun skipBack(intervalMs: Long) = playerRepository.skipBack(intervalMs)
    fun setSpeed(speed: Float) = playerRepository.setSpeed(speed)

    /**
     * Set subtitle language. Empty string means "Off".
     * Tracks user's explicit "Off" choice to prevent auto-select from overriding it.
     */
    fun setSubtitleLanguage(languageCode: String) {
        if (languageCode.isEmpty()) {
            userDisabledSubtitles = true
        }
        playerRepository.setSubtitleLanguage(languageCode)
    }

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

        private const val SUBTITLE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private suspend fun downloadVttContent(url: String): String? =
            suspendCancellableCoroutine { cont ->
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", SUBTITLE_USER_AGENT)
                    .build()
                val call = AppHttpClient.subtitleClient.newCall(request)

                cont.invokeOnCancellation { call.cancel() }

                call.enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: IOException) {
                        if (cont.isCancelled) return
                        AppLogger.w(TAG, "Subtitle download failed: ${e.message} — URL: $url")
                        cont.resumeWith(Result.success(null))
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        if (cont.isCancelled) {
                            response.close()
                            return
                        }
                        val body = try {
                            response.use {
                                if (it.isSuccessful) {
                                    it.body?.string()?.takeIf { s -> s.isNotBlank() }
                                } else {
                                    AppLogger.w(TAG, "Subtitle HTTP ${it.code} for URL: $url")
                                    null
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "Subtitle response read failed: ${e.message} — URL: $url")
                            null
                        }
                        cont.resumeWith(Result.success(body))
                    }
                })
            }
    }
}
