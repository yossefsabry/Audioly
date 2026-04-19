package com.audioly.app.player

import com.audioly.app.extraction.SubtitleTrack
import com.audioly.app.util.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owned by [AudiolyApp], wired to the bound [AudioService].
 * Compose ViewModels observe [state] and call commands through this object.
 *
 * Handles the race condition where load() is called before the AudioService
 * is bound by queuing the pending load and replaying it on attach().
 *
 * Also manages the playback queue: a list of [QueueItem]s with a current index,
 * shuffle mode, and repeat mode.
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

    // ─── Queue state ─────────────────────────────────────────────────────────

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    /** Emitted when the queue wants to advance to a track that needs extraction. */
    private val _queueAdvanceRequests = MutableSharedFlow<QueueItem>(extraBufferCapacity = 4)
    val queueAdvanceRequests: SharedFlow<QueueItem> = _queueAdvanceRequests.asSharedFlow()

    // Shuffle order: maps logical order index → actual queue index
    private var shuffleOrder: List<Int> = emptyList()

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

    // ─── Queue operations ────────────────────────────────────────────────────

    /** Replace queue, set current index, start playing item at [startIndex]. */
    fun setQueue(items: List<QueueItem>, startIndex: Int = 0) {
        _queue.value = items
        _queueIndex.value = if (items.isEmpty()) -1 else startIndex.coerceIn(0, items.lastIndex)
        regenerateShuffleOrder()
    }

    /** Append a single item to the end of the queue. */
    fun addToQueue(item: QueueItem) {
        _queue.update { it + item }
        if (_queueIndex.value < 0) _queueIndex.value = 0
        regenerateShuffleOrder()
    }

    /** Insert item right after the currently playing track ("play next"). */
    fun playNext(item: QueueItem) {
        _queue.update { current ->
            val insertAt = (_queueIndex.value + 1).coerceIn(0, current.size)
            current.toMutableList().apply { add(insertAt, item) }
        }
        regenerateShuffleOrder()
    }

    /** Remove item at [index] from queue. */
    fun removeFromQueue(index: Int) {
        _queue.update { current ->
            if (index !in current.indices) return@update current
            current.toMutableList().apply { removeAt(index) }
        }
        // Adjust current index
        val curIdx = _queueIndex.value
        if (index < curIdx) _queueIndex.value = curIdx - 1
        else if (index == curIdx && curIdx >= _queue.value.size) {
            _queueIndex.value = (_queue.value.size - 1).coerceAtLeast(-1)
        }
        regenerateShuffleOrder()
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _queueIndex.value = -1
        shuffleOrder = emptyList()
    }

    fun toggleRepeatMode() {
        _repeatMode.update {
            when (it) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
    }

    fun toggleShuffle() {
        _shuffleEnabled.update { !it }
        regenerateShuffleOrder()
    }

    /**
     * Called by AudioService/AudioPlayer when a track finishes (STATE_ENDED).
     * Advances to the next queue item based on repeat/shuffle mode.
     */
    fun onTrackCompleted() {
        val q = _queue.value
        if (q.isEmpty()) return

        val repeat = _repeatMode.value
        val curIdx = _queueIndex.value

        when (repeat) {
            RepeatMode.ONE -> {
                // Replay same track
                val item = q.getOrNull(curIdx) ?: return
                requestPlayQueueItem(item, curIdx)
            }
            RepeatMode.ALL -> {
                val nextIdx = nextQueueIndex(curIdx, q.size, wrap = true) ?: return
                _queueIndex.value = nextIdx
                requestPlayQueueItem(q[nextIdx], nextIdx)
            }
            RepeatMode.OFF -> {
                val nextIdx = nextQueueIndex(curIdx, q.size, wrap = false)
                if (nextIdx == null) {
                    AppLogger.d(TAG, "Queue ended (no repeat)")
                    return
                }
                _queueIndex.value = nextIdx
                requestPlayQueueItem(q[nextIdx], nextIdx)
            }
        }
    }

    /** Skip to next in queue. */
    fun skipToNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        val nextIdx = nextQueueIndex(_queueIndex.value, q.size, wrap = _repeatMode.value != RepeatMode.OFF)
            ?: return
        _queueIndex.value = nextIdx
        requestPlayQueueItem(q[nextIdx], nextIdx)
    }

    /** Skip to previous in queue. */
    fun skipToPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return
        // If position > 3s, restart current track
        val pos = state.value.positionMs
        if (pos > 3_000L) {
            seekTo(0L)
            return
        }
        val prevIdx = previousQueueIndex(_queueIndex.value, q.size, wrap = _repeatMode.value != RepeatMode.OFF)
            ?: return
        _queueIndex.value = prevIdx
        requestPlayQueueItem(q[prevIdx], prevIdx)
    }

    private fun requestPlayQueueItem(item: QueueItem, @Suppress("UNUSED_PARAMETER") index: Int) {
        val audioUrl = item.audioUrl
        if (audioUrl != null) {
            // Can play directly
            clearSubtitles()
            load(
                audioUrl = audioUrl,
                videoId = item.videoId,
                title = item.title,
                uploader = item.uploader,
                thumbnailUrl = item.thumbnailUrl,
                durationMs = item.durationSeconds * 1000L,
            )
        } else {
            // Need extraction — emit request for ViewModel to handle
            scope.launch { _queueAdvanceRequests.emit(item) }
        }
    }

    private fun nextQueueIndex(current: Int, size: Int, wrap: Boolean): Int? {
        if (size == 0) return null
        if (_shuffleEnabled.value && shuffleOrder.isNotEmpty()) {
            val logicalIdx = shuffleOrder.indexOf(current)
            val nextLogical = logicalIdx + 1
            return if (nextLogical < shuffleOrder.size) shuffleOrder[nextLogical]
            else if (wrap) {
                regenerateShuffleOrder()
                // Skip index 0 which is always the current track — avoid replaying it
                shuffleOrder.getOrNull(1) ?: shuffleOrder.firstOrNull()
            }
            else null
        }
        val next = current + 1
        return if (next < size) next else if (wrap) 0 else null
    }

    private fun previousQueueIndex(current: Int, size: Int, wrap: Boolean): Int? {
        if (size == 0) return null
        if (_shuffleEnabled.value && shuffleOrder.isNotEmpty()) {
            val logicalIdx = shuffleOrder.indexOf(current)
            val prevLogical = logicalIdx - 1
            return if (prevLogical >= 0) shuffleOrder[prevLogical]
            else if (wrap) shuffleOrder.lastOrNull()
            else null
        }
        val prev = current - 1
        return if (prev >= 0) prev else if (wrap) size - 1 else null
    }

    private fun regenerateShuffleOrder() {
        val q = _queue.value
        if (q.isEmpty()) {
            shuffleOrder = emptyList()
            return
        }
        val curIdx = _queueIndex.value
        val indices = q.indices.toMutableList()
        // Remove current so it's always first
        indices.remove(curIdx)
        indices.shuffle()
        shuffleOrder = if (curIdx in q.indices) listOf(curIdx) + indices else indices
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

enum class RepeatMode { OFF, ONE, ALL }
