package com.audioly.app.player

import com.audioly.app.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owned by [AudiolyApp], wired to the bound [AudioService].
 * Compose ViewModels observe [state] and call commands through this object.
 *
 * Handles the race condition where load() is called before the AudioService
 * is bound by queuing the pending load and replaying it on attach().
 */
class PlayerRepository {

    private var playerRef: AudioPlayer? = null

    // Fallback state for when player isn't attached yet
    private val _fallbackState = MutableStateFlow(PlayerState())

    val state: StateFlow<PlayerState>
        get() = playerRef?.state ?: _fallbackState.asStateFlow()

    // Pending load request (queued when playerRef is null)
    private var pendingLoad: PendingLoad? = null

    // ─── Called by AudioService binder ────────────────────────────────────────

    fun attach(player: AudioPlayer) {
        playerRef = player
        // Replay any queued load
        pendingLoad?.let { p ->
            AppLogger.d(TAG, "Replaying pending load for ${p.videoId}")
            player.load(p.audioUrl, p.videoId, p.title, p.uploader, p.thumbnailUrl, p.durationMs)
            pendingLoad = null
        }
    }

    fun detach() {
        AppLogger.d(TAG, "Player detached")
        playerRef = null
    }

    // ─── Commands ─────────────────────────────────────────────────────────────

    fun load(
        audioUrl: String,
        videoId: String,
        title: String,
        uploader: String,
        thumbnailUrl: String,
        durationMs: Long,
    ) {
        val player = playerRef
        if (player != null) {
            player.load(audioUrl, videoId, title, uploader, thumbnailUrl, durationMs)
        } else {
            // Queue for replay when service binds
            AppLogger.d(TAG, "Queuing load for $videoId (service not bound yet)")
            pendingLoad = PendingLoad(audioUrl, videoId, title, uploader, thumbnailUrl, durationMs)
            // Update fallback state so the UI shows something
            _fallbackState.value = PlayerState(
                videoId = videoId,
                title = title,
                uploader = uploader,
                thumbnailUrl = thumbnailUrl,
                durationMs = durationMs,
                isBuffering = true,
            )
        }
    }

    fun play() = playerRef?.play()
    fun pause() = playerRef?.pause()
    fun togglePlayPause() = playerRef?.togglePlayPause()
    fun seekTo(positionMs: Long) = playerRef?.seekTo(positionMs)
    fun skipForward(intervalMs: Long = 15_000L) = playerRef?.skipForward(intervalMs)
    fun skipBack(intervalMs: Long = 15_000L) = playerRef?.skipBack(intervalMs)
    fun setSpeed(speed: Float) = playerRef?.setSpeed(speed)
    fun setSubtitleLanguage(languageCode: String) = playerRef?.setSubtitleLanguage(languageCode)
    fun setSubtitleIndex(index: Int) = playerRef?.setSubtitleIndex(index)

    private data class PendingLoad(
        val audioUrl: String,
        val videoId: String,
        val title: String,
        val uploader: String,
        val thumbnailUrl: String,
        val durationMs: Long,
    )

    private companion object {
        const val TAG = "PlayerRepository"
    }
}
