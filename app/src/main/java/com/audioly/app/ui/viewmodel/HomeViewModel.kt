package com.audioly.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioly.app.data.cache.SubtitleCacheManager
import com.audioly.app.data.model.Track
import com.audioly.app.data.preferences.UserPreferencesRepository
import com.audioly.app.data.repository.CacheRepository
import com.audioly.app.data.repository.TrackRepository
import com.audioly.app.extraction.ExtractionResult
import com.audioly.app.extraction.SubtitleTrack
import com.audioly.app.extraction.YouTubeExtractor
import com.audioly.app.player.PlayerRepository
import com.audioly.app.util.AppLogger
import com.audioly.app.util.UrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for HomeScreen. Owns URL input state, extraction lifecycle,
 * recent history observation, and cache status computation.
 */
class HomeViewModel(
    private val trackRepository: TrackRepository,
    private val cacheRepository: CacheRepository,
    private val playerRepository: PlayerRepository,
    private val youTubeExtractor: YouTubeExtractor,
    private val preferencesRepository: UserPreferencesRepository,
    private val subtitleCacheManager: SubtitleCacheManager,
) : ViewModel() {

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _urlError = MutableStateFlow<String?>(null)
    val urlError: StateFlow<String?> = _urlError.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _lastFailedVideoId = MutableStateFlow<String?>(null)
    val lastFailedVideoId: StateFlow<String?> = _lastFailedVideoId.asStateFlow()

    /** Recent history with cache status pre-computed off main thread. */
    val historyWithCache: StateFlow<List<Pair<Track, Boolean>>> =
        combine(
            trackRepository.observeHistory(limit = 10),
            cacheRepository.cacheVersion,
        ) { history, _ ->
            withContext(Dispatchers.IO) {
                history.map { track ->
                    track to cacheRepository.getAudioStatus(track.videoId).hasCache
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // One-shot events
    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun updateUrl(url: String) {
        _urlInput.value = url
        _urlError.value = null
    }

    fun submitUrl(url: String) {
        val videoId = UrlValidator.extractVideoId(url)
        if (videoId == null) {
            _urlError.value = if (url.isBlank()) "Enter a YouTube URL" else "Not a valid YouTube URL"
            return
        }
        _urlError.value = null
        playVideoId(videoId)
    }

    fun playTrack(track: Track) {
        playVideoId(track.videoId)
    }

    fun handleShareIntent(url: String) {
        _urlInput.value = url
        submitUrl(url)
    }

    /** Retry the last failed extraction. */
    fun retry() {
        val videoId = _lastFailedVideoId.value ?: return
        _lastFailedVideoId.value = null
        playVideoId(videoId)
    }

    private fun playVideoId(videoId: String) {
        if (_isExtracting.value) {
            AppLogger.w(TAG, "Extraction already in progress, ignoring")
            return
        }
        viewModelScope.launch {
            if (tryPlayFromCache(videoId)) return@launch

            _isExtracting.value = true
            _lastFailedVideoId.value = null
            try {
                val url = "https://www.youtube.com/watch?v=$videoId"
                AppLogger.i(TAG, "Extracting from videoId: $videoId")
                when (val result = youTubeExtractor.extract(url)) {
                    is ExtractionResult.Success -> {
                        val info = result.streamInfo
                        try {
                            trackRepository.upsertFromExtraction(info)
                            trackRepository.setAudioStreamUrl(info.videoId, info.audioStreamUrl)
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
                        applyDefaultSpeed()
                        resumeSavedPosition(info.videoId)
                        _events.emit(HomeEvent.NavigateToPlayer(info.videoId))
                    }
                    is ExtractionResult.Failure.InvalidUrl ->
                        _events.emit(HomeEvent.ShowSnackbar("Not a valid YouTube URL"))
                    is ExtractionResult.Failure.VideoUnavailable -> {
                        _lastFailedVideoId.value = videoId
                        _events.emit(HomeEvent.ShowSnackbar("Video unavailable"))
                    }
                    is ExtractionResult.Failure.AgeRestricted ->
                        _events.emit(HomeEvent.ShowSnackbar("Age-restricted video - cannot play"))
                    is ExtractionResult.Failure.NetworkError -> {
                        _lastFailedVideoId.value = videoId
                        _events.emit(HomeEvent.ShowSnackbar("Network error. Check your connection."))
                    }
                    is ExtractionResult.Failure.ExtractionFailed -> {
                        _lastFailedVideoId.value = videoId
                        _events.emit(HomeEvent.ShowSnackbar("Could not load this track right now"))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Unexpected error during extraction", e)
                _lastFailedVideoId.value = videoId
                _events.emit(HomeEvent.ShowSnackbar("Something went wrong: ${e.message ?: "unknown error"}"))
            } finally {
                _isExtracting.value = false
            }
        }
    }

    private suspend fun tryPlayFromCache(videoId: String): Boolean {
        try {
            val cacheStatus = cacheRepository.getAudioStatus(videoId)
            if (!cacheStatus.isFullyCached) return false
            val track = trackRepository.getById(videoId) ?: return false
            val audioUrl = track.audioStreamUrl
            if (audioUrl.isNullOrBlank()) return false

            AppLogger.i(TAG, "Playing from cache: $videoId (${cacheStatus.cachedBytes} bytes)")
            playerRepository.clearSubtitles()

            // Restore cached subtitle tracks so the picker + auto-select work offline
            val cachedSubs = withContext(Dispatchers.IO) {
                subtitleCacheManager.getAllForVideo(videoId)
            }
            if (cachedSubs.isNotEmpty()) {
                playerRepository.setSubtitleTracks(cachedSubs.map { entity ->
                    SubtitleTrack(
                        languageCode = entity.languageCode,
                        languageName = entity.languageName,
                        isAutoGenerated = entity.isAutoGenerated,
                        url = "",  // not needed — content is disk-cached
                        format = entity.format,
                    )
                })
            }

            // Always fetch full subtitle track list from YouTube in background.
            // Cached subtitles may only include a subset (e.g. Korean but not English).
            // When the fetch returns English (auto-translated), switch to it if preferred.
            viewModelScope.launch {
                try {
                    val tracks = youTubeExtractor.fetchSubtitles(videoId)
                    if (tracks.isNotEmpty()) {
                        val hadEnglish = playerRepository.subtitleTracks.value
                            .any { it.languageCode.startsWith("en") }
                        playerRepository.setSubtitleTracks(tracks)
                        // If English just became available, select it as the preferred default
                        if (!hadEnglish) {
                            val englishTrack = tracks.firstOrNull { it.languageCode.startsWith("en") }
                            if (englishTrack != null) {
                                playerRepository.setSubtitleLanguage(englishTrack.languageCode)
                            }
                        }
                        AppLogger.i(TAG, "Fetched ${tracks.size} subtitle tracks for cached video $videoId")
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Background subtitle fetch failed: ${e.message}")
                }
            }

            playerRepository.load(
                audioUrl = audioUrl,
                videoId = videoId,
                title = track.title,
                uploader = track.uploader,
                thumbnailUrl = track.thumbnailUrl,
                durationMs = track.durationSeconds * 1000L,
            )
            applyDefaultSpeed()
            resumeSavedPosition(videoId)
            _events.emit(HomeEvent.NavigateToPlayer(videoId))
            return true
        } catch (e: Exception) {
            AppLogger.w(TAG, "Cache playback failed: ${e.message}")
            return false
        }
    }

    private suspend fun applyDefaultSpeed() {
        try {
            val speed = preferencesRepository.preferences.first().playbackSpeed
            if (speed != 1.0f) playerRepository.setSpeed(speed)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to apply default speed: ${e.message}")
        }
    }

    private suspend fun resumeSavedPosition(videoId: String) {
        try {
            val posMs = trackRepository.getLastPosition(videoId)
            if (posMs > 5_000L) {
                playerRepository.seekTo(posMs)
                AppLogger.d(TAG, "Resumed position for $videoId at ${posMs}ms")
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to resume position: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}

sealed interface HomeEvent {
    data class NavigateToPlayer(val videoId: String) : HomeEvent
    data class ShowSnackbar(val message: String) : HomeEvent
}
