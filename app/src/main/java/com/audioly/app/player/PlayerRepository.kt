package com.audioly.app.player

import com.audioly.app.extraction.SubtitleTrack
import com.audioly.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Owned by [AudiolyApp], wired to the bound [AudioService].
 * Compose ViewModels observe [state] and call commands through this object.
 *
 * Handles the race condition where load() is called before the AudioService
 * is bound by queuing the pending load and replaying it on attach().
 */
class PlayerRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var playerRef: AudioPlayer? = null

    // Fallback state for when player isn't attached yet
    private val _fallbackState = MutableStateFlow(PlayerState())

    // Holds the currently-active player's StateFlow (or fallback).
    // flatMapLatest ensures Compose always observes the correct upstream.
    private val _activePlayerState = MutableStateFlow<StateFlow<PlayerState>>(_fallbackState)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<PlayerState> = _activePlayerState
        .flatMapLatest { it }
        .stateIn(scope, SharingStarted.Eagerly, PlayerState())

    // ─── Subtitle state ──────────────────────────────────────────────────────

    /** Available subtitle track metadata (URLs, not content). Set after extraction. */
    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks.asStateFlow()

    /** Downloaded VTT content keyed by language code. Populated on demand. */
    private val _subtitleContent = MutableStateFlow<Map<String, String>>(emptyMap())
    val subtitleContent: StateFlow<Map<String, String>> = _subtitleContent.asStateFlow()

    // Pending load request (queued when playerRef is null)
    // Guarded by @Synchronized on attach() and load() to prevent races
    // between service binder thread and Main thread
    private var pendingLoad: PendingLoad? = null

    // ─── Called by AudioService binder ────────────────────────────────────────

    @Synchronized
    fun attach(player: AudioPlayer) {
        playerRef = player
        _activePlayerState.value = player.state
        // Replay any queued load
        pendingLoad?.let { p ->
            AppLogger.d(TAG, "Replaying pending load for ${p.videoId}")
            player.load(p.audioUrl, p.videoId, p.title, p.uploader, p.thumbnailUrl, p.durationMs)
            pendingLoad = null
        }
    }

    @Synchronized
    fun detach() {
        AppLogger.d(TAG, "Player detached")
        playerRef = null
        _activePlayerState.value = _fallbackState
    }

    // ─── Commands ─────────────────────────────────────────────────────────────

    @Synchronized
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

    // ─── Subtitle data ───────────────────────────────────────────────────────

    fun setSubtitleTracks(tracks: List<SubtitleTrack>) {
        _subtitleTracks.value = tracks
    }

    fun addSubtitleContent(languageCode: String, vttContent: String) {
        _subtitleContent.update { it + (languageCode to vttContent) }
    }

    /** Clear all subtitle state (call when loading a new video). */
    fun clearSubtitles() {
        _subtitleTracks.value = emptyList()
        _subtitleContent.value = emptyMap()
        // Also clear player-side selection so auto-select fires for the next video
        playerRef?.setSubtitleLanguage("")
        playerRef?.setSubtitleIndex(-1)
    }

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
