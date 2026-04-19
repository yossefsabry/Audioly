package com.audioly.app.player

import android.content.Context
import com.audioly.app.util.AppLogger
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.audioly.app.data.cache.AudioCacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Thin wrapper around [ExoPlayer].
 *
 * Created once inside [AudioService] and released when the service is destroyed.
 * Exposes player state as a [StateFlow] so the UI can react without holding a
 * direct reference to ExoPlayer.
 */
class AudioPlayer(
    context: Context,
    private val audioCacheManager: AudioCacheManager,
    /** Called on the main thread when a track reaches STATE_ENDED. */
    private val onTrackEnded: (() -> Unit)? = null,
) : PlayerHandle {

    internal val exoPlayer: ExoPlayer = buildExoPlayer(context, audioCacheManager)

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var currentMeta = Meta()

    @Volatile
    private var released = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateState()
            if (playbackState == Player.STATE_ENDED && !released) {
                AppLogger.d(TAG, "Track ended, notifying callback")
                onTrackEnded?.invoke()
            }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateState()
        override fun onIsLoadingChanged(isLoading: Boolean) = updateState()

        override fun onPlayerError(error: PlaybackException) {
            if (released) return
            AppLogger.e(TAG, "Playback error: ${error.errorCodeName}", error)
            _state.update {
                it.copy(
                    error = "Playback error: ${error.localizedMessage ?: error.errorCodeName}",
                    isBuffering = false,
                    isPlaying = false,
                )
            }
        }
    }

    init {
        exoPlayer.addListener(listener)
    }

    // ─── Playback control ─────────────────────────────────────────────────────

    override fun load(
        audioUrl: String,
        videoId: String,
        title: String,
        uploader: String,
        thumbnailUrl: String,
        durationMs: Long,
    ) {
        if (released) {
            AppLogger.w(TAG, "load() called after release, ignoring")
            return
        }
        try {
            AppLogger.i(TAG, "Loading audio: $videoId — $title")
            currentMeta = Meta(videoId, title, uploader, thumbnailUrl, durationMs)
            // Clear previous error and subtitle selection so auto-select fires for new video
            _state.update {
                it.copy(
                    error = null,
                    selectedSubtitleLanguage = "",
                    currentSubtitleIndex = -1,
                )
            }
            val mediaItem = MediaItem.Builder()
                .setUri(audioUrl)
                .setMediaId(videoId)
                .setCustomCacheKey(videoId)
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            updateState()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load audio: $videoId", e)
            _state.update {
                it.copy(
                    videoId = videoId,
                    title = title,
                    uploader = uploader,
                    thumbnailUrl = thumbnailUrl,
                    error = "Failed to load: ${e.message}",
                    isBuffering = false,
                )
            }
        }
    }

    override fun play() { if (!released) { exoPlayer.play(); updateState() } }
    override fun pause() { if (!released) { exoPlayer.pause(); updateState() } }
    override fun togglePlayPause() { if (!released) { if (exoPlayer.isPlaying) pause() else play() } }

    override fun seekTo(positionMs: Long) {
        if (released) return
        exoPlayer.seekTo(positionMs)
        updateState()
    }

    override fun skipForward(intervalMs: Long) {
        if (released) return
        val duration = exoPlayer.duration
        if (duration == C.TIME_UNSET) return
        seekTo((exoPlayer.currentPosition + intervalMs).coerceAtMost(duration))
    }
    override fun skipBack(intervalMs: Long) {
        if (released) return
        seekTo((exoPlayer.currentPosition - intervalMs).coerceAtLeast(0L))
    }

    override fun setSpeed(speed: Float) {
        if (released) return
        exoPlayer.setPlaybackSpeed(speed)
        updateState()
    }

    override fun setSubtitleLanguage(languageCode: String) {
        if (released) return
        _state.update { it.copy(selectedSubtitleLanguage = languageCode) }
    }

    override fun setSubtitleIndex(index: Int) {
        if (released) return
        _state.update { it.copy(currentSubtitleIndex = index) }
    }

    // ─── Position polling ─────────────────────────────────────────────────────

    /** Called periodically (e.g. 250 ms) by the service to keep position fresh. */
    fun tick() {
        if (!released && (exoPlayer.isPlaying || exoPlayer.isLoading || exoPlayer.playbackState == Player.STATE_BUFFERING)) {
            updateState()
        }
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private fun updateState() {
        if (released) return
        val meta = currentMeta
        val cacheStatus = audioCacheManager.noteCacheProgress(meta.videoId)
        val duration = exoPlayer.duration.takeIf { it > 0L } ?: meta.durationMs
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val buffered = exoPlayer.bufferedPosition.coerceAtLeast(0L)
        val playing = exoPlayer.isPlaying
        val buffering = exoPlayer.playbackState == Player.STATE_BUFFERING
        val speed = exoPlayer.playbackParameters.speed
        _state.update { prev ->
            PlayerState(
                videoId = meta.videoId,
                title = meta.title,
                uploader = meta.uploader,
                thumbnailUrl = meta.thumbnailUrl,
                durationMs = duration,
                positionMs = position,
                bufferedPositionMs = buffered,
                isPlaying = playing,
                isBuffering = buffering,
                playbackSpeed = speed,
                cachedBytes = cacheStatus.cachedBytes,
                cacheContentLength = cacheStatus.contentLength,
                hasCachedAudio = cacheStatus.hasCache,
                isFullyCached = cacheStatus.isFullyCached,
                selectedSubtitleLanguage = prev.selectedSubtitleLanguage,
                currentSubtitleIndex = prev.currentSubtitleIndex,
                error = prev.error,
            )
        }
    }

    fun release() {
        released = true
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    private fun buildExoPlayer(context: Context, cacheManager: AudioCacheManager): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheManager.dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    private data class Meta(
        val videoId: String? = null,
        val title: String = "",
        val uploader: String = "",
        val thumbnailUrl: String = "",
        val durationMs: Long = 0L,
    )

    companion object {
        private const val TAG = "AudioPlayer"
    }
}
