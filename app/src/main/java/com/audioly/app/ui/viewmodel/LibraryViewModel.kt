package com.audioly.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioly.app.data.cache.SubtitleCacheManager
import com.audioly.app.data.model.Track
import com.audioly.app.data.preferences.UserPreferencesRepository
import com.audioly.app.data.repository.CacheRepository
import com.audioly.app.data.repository.PlaylistRepository
import com.audioly.app.data.repository.TrackRepository
import com.audioly.app.extraction.ExtractionResult
import com.audioly.app.extraction.SubtitleTrack
import com.audioly.app.extraction.YouTubeExtractor
import com.audioly.app.player.PlayerRepository
import com.audioly.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ViewModel for LibraryScreen. Manages tab state and playback launching
 * with proper concurrency guards (replaces integer semaphore).
 */
class LibraryViewModel(
    val trackRepository: TrackRepository,
    val cacheRepository: CacheRepository,
    val playlistRepository: PlaylistRepository,
    private val playerRepository: PlayerRepository,
    private val youTubeExtractor: YouTubeExtractor,
    private val preferencesRepository: UserPreferencesRepository,
    private val subtitleCacheManager: SubtitleCacheManager,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val isLaunching = AtomicBoolean(false)

    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun playTrack(track: Track) {
        if (!isLaunching.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                // Try cache first
                if (tryPlayFromCache(track.videoId)) return@launch

                // Fall back to extraction
                val url = "https://www.youtube.com/watch?v=${track.videoId}"
                AppLogger.i(TAG, "Library: extracting ${track.videoId}")
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
                        _events.emit(LibraryEvent.NavigateToPlayer(info.videoId))
                    }
                    is ExtractionResult.Failure.NetworkError ->
                        _events.emit(LibraryEvent.ShowSnackbar("Network error. Check your connection."))
                    is ExtractionResult.Failure ->
                        _events.emit(LibraryEvent.ShowSnackbar("Could not load this track right now"))
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Unexpected error during library playback", e)
                _events.emit(LibraryEvent.ShowSnackbar("Something went wrong: ${e.message ?: "unknown error"}"))
            } finally {
                isLaunching.set(false)
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

            AppLogger.i(TAG, "Playing from cache: $videoId")
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
            viewModelScope.launch {
                try {
                    val tracks = youTubeExtractor.fetchSubtitles(videoId)
                    if (tracks.isNotEmpty()) {
                        val hadEnglish = playerRepository.subtitleTracks.value
                            .any { it.languageCode.startsWith("en") }
                        playerRepository.setSubtitleTracks(tracks)
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
            _events.emit(LibraryEvent.NavigateToPlayer(videoId))
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

    companion object {
        private const val TAG = "LibraryViewModel"
    }
}

sealed interface LibraryEvent {
    data class NavigateToPlayer(val videoId: String) : LibraryEvent
    data class ShowSnackbar(val message: String) : LibraryEvent
}
