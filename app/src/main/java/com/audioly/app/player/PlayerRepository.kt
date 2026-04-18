package com.audioly.app.player

import com.audioly.app.extraction.SubtitleTrack
import com.audioly.app.util.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
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
class PlayerRepository(
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    @Volatile
    private var playerRef: PlayerHandle? = null

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
    private var lastLoad: PendingLoad? = null
    private var pendingResumePositionMs: Long? = null

    // ─── Called by AudioService binder ────────────────────────────────────────

    @Synchronized
    fun attach(player: PlayerHandle) {
        val queuedLoad = pendingLoad
        val keepFallbackState = queuedLoad != null || (player.state.value.isEmpty && !_fallbackState.value.isEmpty)
        playerRef = player
        _activePlayerState.value = if (keepFallbackState) _fallbackState else player.state
        // Replay any queued load before switching UI to the live player state.
        queuedLoad?.let { p ->
            AppLogger.d(TAG, "Replaying pending load for ${p.videoId}")
            player.load(p.audioUrl, p.videoId, p.title, p.uploader, p.thumbnailUrl, p.durationMs)
            pendingResumePositionMs?.let { positionMs ->
                if (positionMs > 0L) player.seekTo(positionMs)
                pendingResumePositionMs = null
            }
            pendingLoad = null
        }
        _activePlayerState.value = player.state
    }

    @Synchronized
    fun detach() {
        AppLogger.d(TAG, "Player detached")
        playerRef?.state?.value?.let { liveState ->
            _fallbackState.value = liveState.copy(
                isPlaying = false,
                isBuffering = false,
            )
        }
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
        val request = PendingLoad(audioUrl, videoId, title, uploader, thumbnailUrl, durationMs)
        lastLoad = request
        val player = playerRef
        if (player != null) {
            player.load(audioUrl, videoId, title, uploader, thumbnailUrl, durationMs)
        } else {
            // Queue for replay when service binds
            AppLogger.d(TAG, "Queuing load for $videoId (service not bound yet)")
            pendingLoad = request
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

    @Synchronized
    fun play() {
        val player = playerRef
        if (player != null) {
            if (player.state.value.isEmpty && lastLoad != null) {
                resumeLastLoad(player)
                return
            }
            player.play()
        } else {
            queueResumeForAttach()
        }
    }

    @Synchronized
    fun pause() { playerRef?.pause() }

    @Synchronized
    fun togglePlayPause() {
        val player = playerRef
        if (player != null) {
            if (player.state.value.isEmpty && lastLoad != null) {
                resumeLastLoad(player)
                return
            }
            player.togglePlayPause()
        } else {
            queueResumeForAttach()
        }
    }

    @Synchronized fun seekTo(positionMs: Long) { playerRef?.seekTo(positionMs) }
    @Synchronized fun skipForward(intervalMs: Long = 15_000L) { playerRef?.skipForward(intervalMs) }
    @Synchronized fun skipBack(intervalMs: Long = 15_000L) { playerRef?.skipBack(intervalMs) }
    @Synchronized fun setSpeed(speed: Float) { playerRef?.setSpeed(speed) }
    @Synchronized fun setSubtitleLanguage(languageCode: String) { playerRef?.setSubtitleLanguage(languageCode) }
    @Synchronized fun setSubtitleIndex(index: Int) { playerRef?.setSubtitleIndex(index) }

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

    @Synchronized
    private fun queueResumeForAttach() {
        val request = lastLoad ?: return
        AppLogger.d(TAG, "Queueing detached resume for ${request.videoId}")
        pendingLoad = request
        pendingResumePositionMs = _fallbackState.value.positionMs.takeIf { it > 0L }
        _fallbackState.value = PlayerState(
            videoId = request.videoId,
            title = request.title,
            uploader = request.uploader,
            thumbnailUrl = request.thumbnailUrl,
            durationMs = request.durationMs,
            isBuffering = true,
            positionMs = _fallbackState.value.positionMs,
        )
    }

    private fun resumeLastLoad(player: PlayerHandle) {
        val request = lastLoad ?: return
        val resumePositionMs = _fallbackState.value.positionMs.takeIf { it > 0L }
        AppLogger.d(TAG, "Replaying last load directly for ${request.videoId}")
        player.load(
            audioUrl = request.audioUrl,
            videoId = request.videoId,
            title = request.title,
            uploader = request.uploader,
            thumbnailUrl = request.thumbnailUrl,
            durationMs = request.durationMs,
        )
        if (resumePositionMs != null) {
            player.seekTo(resumePositionMs)
        }
        _activePlayerState.value = player.state
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
