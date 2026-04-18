package com.audioly.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioly.app.data.cache.SubtitleCacheManager
import com.audioly.app.data.preferences.UserPreferences
import com.audioly.app.data.preferences.UserPreferencesRepository
import com.audioly.app.network.AppHttpClient
import com.audioly.app.player.PlayerRepository
import com.audioly.app.player.SubtitleCue
import com.audioly.app.player.SubtitleManager
import com.audioly.app.player.VttParser
import com.audioly.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
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
) : ViewModel() {

    val playerState = playerRepository.state
    val subtitleTracks = playerRepository.subtitleTracks
    val subtitleContent = playerRepository.subtitleContent

    val prefs: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

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
    }

    private var currentVideoId: String? = null

    // ─── Playback commands (delegate to PlayerRepository) ─────────────────────

    fun togglePlayPause() = playerRepository.togglePlayPause()
    fun seekTo(positionMs: Long) = playerRepository.seekTo(positionMs)
    fun skipForward(intervalMs: Long) = playerRepository.skipForward(intervalMs)
    fun skipBack(intervalMs: Long) = playerRepository.skipBack(intervalMs)
    fun setSpeed(speed: Float) = playerRepository.setSpeed(speed)
    fun setSubtitleLanguage(languageCode: String) = playerRepository.setSubtitleLanguage(languageCode)

    companion object {
        private const val TAG = "PlayerViewModel"

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
