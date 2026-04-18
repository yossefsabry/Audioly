package com.audioly.app.ui.playback

import androidx.compose.material3.SnackbarHostState
import com.audioly.app.AudiolyApp
import com.audioly.app.extraction.ExtractionResult
import com.audioly.app.util.AppLogger
import com.audioly.app.util.UrlValidator
import kotlinx.coroutines.flow.first

private const val TAG = "PlaybackLaunchers"

/**
 * Validates the URL, extracts video ID, then delegates to [launchPlaybackFromVideoId].
 * Shows error messages for invalid URLs.
 */
suspend fun launchPlaybackFromUrl(
    url: String,
    app: AudiolyApp,
    onNavigateToPlayer: (String) -> Unit,
    setError: (String?) -> Unit,
    setExtracting: (Boolean) -> Unit,
    isCurrentlyIdle: Boolean,
    snackbarHostState: SnackbarHostState,
) {
    val videoId = UrlValidator.extractVideoId(url)
    if (videoId == null) {
        setError(if (url.isBlank()) "Enter a YouTube URL" else "Not a valid YouTube URL")
        return
    }
    setError(null)
    if (!isCurrentlyIdle) {
        AppLogger.w(TAG, "Extraction already in progress, ignoring duplicate request")
        return
    }

    launchPlaybackFromVideoId(
        videoId = videoId,
        app = app,
        onNavigateToPlayer = onNavigateToPlayer,
        setExtracting = setExtracting,
        isCurrentlyIdle = isCurrentlyIdle,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Attempts cache playback first, then falls back to extraction.
 * Handles all error cases with snackbar messages.
 */
suspend fun launchPlaybackFromVideoId(
    videoId: String,
    app: AudiolyApp,
    onNavigateToPlayer: (String) -> Unit,
    setExtracting: (Boolean) -> Unit,
    isCurrentlyIdle: Boolean,
    snackbarHostState: SnackbarHostState,
) {
    if (!isCurrentlyIdle) return

    if (tryPlayFromCache(videoId, app, onNavigateToPlayer)) {
        return
    }

    setExtracting(true)
    try {
        val url = "https://www.youtube.com/watch?v=$videoId"
        AppLogger.i(TAG, "Extracting from videoId: $videoId")
        when (val result = app.youTubeExtractor.extract(url)) {
            is ExtractionResult.Success -> {
                val info = result.streamInfo
                try {
                    app.trackRepository.upsertFromExtraction(info)
                    app.trackRepository.setAudioStreamUrl(info.videoId, info.audioStreamUrl)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to save track to DB", e)
                }
                app.playerRepository.clearSubtitles()
                app.playerRepository.setSubtitleTracks(info.subtitleTracks)
                app.playerRepository.load(
                    audioUrl = info.audioStreamUrl,
                    videoId = info.videoId,
                    title = info.title,
                    uploader = info.uploader,
                    thumbnailUrl = info.thumbnailUrl,
                    durationMs = info.durationSeconds * 1000L,
                )
                applyDefaultSpeed(app)
                onNavigateToPlayer(info.videoId)
            }

            is ExtractionResult.Failure.InvalidUrl ->
                snackbarHostState.showSnackbar("Not a valid YouTube URL")

            is ExtractionResult.Failure.VideoUnavailable ->
                snackbarHostState.showSnackbar("Video unavailable")

            is ExtractionResult.Failure.AgeRestricted ->
                snackbarHostState.showSnackbar("Age-restricted video - cannot play")

            is ExtractionResult.Failure.NetworkError ->
                snackbarHostState.showSnackbar("Network error. Check your connection.")

            is ExtractionResult.Failure.ExtractionFailed ->
                snackbarHostState.showSnackbar("Could not load this track right now")
        }
    } catch (e: Exception) {
        AppLogger.e(TAG, "Unexpected error during extraction", e)
        snackbarHostState.showSnackbar("Something went wrong: ${e.message ?: "unknown error"}")
    } finally {
        setExtracting(false)
    }
}

private suspend fun tryPlayFromCache(
    videoId: String,
    app: AudiolyApp,
    onNavigateToPlayer: (String) -> Unit,
): Boolean {
    try {
        val cacheStatus = app.cacheRepository.getAudioStatus(videoId)
        if (!cacheStatus.isFullyCached) {
            AppLogger.d(
                TAG,
                "tryPlayFromCache($videoId): fullyCached=false, hasCache=${cacheStatus.hasCache}, cachedBytes=${cacheStatus.cachedBytes} - falling back to extraction",
            )
            return false
        }
        val track = app.trackRepository.getById(videoId)
        if (track == null) {
            AppLogger.d(TAG, "tryPlayFromCache($videoId): fully cached but no DB record")
            return false
        }
        val audioUrl = track.audioStreamUrl
        if (audioUrl.isNullOrBlank()) {
            AppLogger.d(TAG, "tryPlayFromCache($videoId): fully cached but no stored audioUrl")
            return false
        }

        AppLogger.i(TAG, "Playing from cache: $videoId (${cacheStatus.cachedBytes} bytes)")
        app.playerRepository.clearSubtitles()
        app.playerRepository.load(
            audioUrl = audioUrl,
            videoId = videoId,
            title = track.title,
            uploader = track.uploader,
            thumbnailUrl = track.thumbnailUrl,
            durationMs = track.durationSeconds * 1000L,
        )
        applyDefaultSpeed(app)
        onNavigateToPlayer(videoId)
        return true
    } catch (e: Exception) {
        AppLogger.w(TAG, "Cache playback failed, falling back to extraction: ${e.message}")
        return false
    }
}

/** Apply user's default playback speed preference to the player after loading a new track. */
private suspend fun applyDefaultSpeed(app: AudiolyApp) {
    try {
        val speed = app.preferencesRepository.preferences.first().playbackSpeed
        if (speed != 1.0f) {
            app.playerRepository.setSpeed(speed)
        }
    } catch (e: Exception) {
        AppLogger.w(TAG, "Failed to apply default speed: ${e.message}")
    }
}
