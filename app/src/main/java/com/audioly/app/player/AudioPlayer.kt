package com.audioly.app.player

import android.content.Context
import com.audioly.app.util.AppLogger
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
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
) {

    internal val exoPlayer: ExoPlayer = buildExoPlayer(context, audioCacheManager)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var currentMeta = Meta()

    @Volatile
    private var released = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = updateState()
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

    fun load(
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

    fun play() { if (!released) { exoPlayer.play(); updateState() } }
    fun pause() { if (!released) { exoPlayer.pause(); updateState() } }
    fun togglePlayPause() { if (!released) { if (exoPlayer.isPlaying) pause() else play() } }

    fun seekTo(positionMs: Long) {
        if (released) return
        exoPlayer.seekTo(positionMs)
        updateState()
    }

    fun skipForward(intervalMs: Long) {
        if (released) return
        val duration = exoPlayer.duration
        if (duration == C.TIME_UNSET) return
        seekTo((exoPlayer.currentPosition + intervalMs).coerceAtMost(duration))
    }
    fun skipBack(intervalMs: Long) {
        if (released) return
        seekTo((exoPlayer.currentPosition - intervalMs).coerceAtLeast(0L))
    }

    fun setSpeed(speed: Float) {
        if (released) return
        exoPlayer.setPlaybackSpeed(speed)
        updateState()
    }

    fun setSubtitleLanguage(languageCode: String) {
        if (released) return
        _state.update { it.copy(selectedSubtitleLanguage = languageCode) }
    }

    fun setSubtitleIndex(index: Int) {
        if (released) return
        _state.update { it.copy(currentSubtitleIndex = index) }
    }

    // ─── Position polling ─────────────────────────────────────────────────────

    /** Called periodically (e.g. 250 ms) by the service to keep position fresh. */
    fun tick() {
        if (!released && exoPlayer.isPlaying) updateState()
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private fun updateState() {
        if (released) return
        val meta = currentMeta
        val duration = exoPlayer.duration.coerceAtLeast(0L)
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
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cacheManager.cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            .setHandleAudioBecomingNoisy(true)
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
