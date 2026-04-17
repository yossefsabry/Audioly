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

    val exoPlayer: ExoPlayer = buildExoPlayer(context, audioCacheManager)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var currentMeta = Meta()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = updateState()
            override fun onIsPlayingChanged(isPlaying: Boolean) = updateState()
            override fun onIsLoadingChanged(isLoading: Boolean) = updateState()

            override fun onPlayerError(error: PlaybackException) {
                AppLogger.e(TAG, "Playback error: ${error.errorCodeName}", error)
                _state.value = _state.value.copy(
                    error = "Playback error: ${error.localizedMessage ?: error.errorCodeName}",
                    isBuffering = false,
                    isPlaying = false,
                )
            }
        })
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
        try {
            AppLogger.i(TAG, "Loading audio: $videoId — $title")
            currentMeta = Meta(videoId, title, uploader, thumbnailUrl, durationMs)
            // Clear any previous error
            _state.value = _state.value.copy(error = null)
            val mediaItem = MediaItem.Builder()
                .setUri(audioUrl)
                .setMediaId(videoId)
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            updateState()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load audio: $videoId", e)
            _state.value = _state.value.copy(
                videoId = videoId,
                title = title,
                uploader = uploader,
                thumbnailUrl = thumbnailUrl,
                error = "Failed to load: ${e.message}",
                isBuffering = false,
            )
        }
    }

    fun play() { exoPlayer.play(); updateState() }
    fun pause() { exoPlayer.pause(); updateState() }
    fun togglePlayPause() { if (exoPlayer.isPlaying) pause() else play() }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        updateState()
    }

    fun skipForward(intervalMs: Long) = seekTo((exoPlayer.currentPosition + intervalMs).coerceAtMost(exoPlayer.duration.coerceAtLeast(0L)))
    fun skipBack(intervalMs: Long) = seekTo((exoPlayer.currentPosition - intervalMs).coerceAtLeast(0L))

    fun setSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
        updateState()
    }

    fun setSubtitleLanguage(languageCode: String) {
        _state.value = _state.value.copy(selectedSubtitleLanguage = languageCode)
    }

    fun setSubtitleIndex(index: Int) {
        _state.value = _state.value.copy(currentSubtitleIndex = index)
    }

    // ─── Position polling ─────────────────────────────────────────────────────

    /** Called periodically (e.g. 250 ms) by the service to keep position fresh. */
    fun tick() {
        if (exoPlayer.isPlaying) updateState()
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private fun updateState() {
        _state.value = PlayerState(
            videoId = currentMeta.videoId,
            title = currentMeta.title,
            uploader = currentMeta.uploader,
            thumbnailUrl = currentMeta.thumbnailUrl,
            durationMs = exoPlayer.duration.coerceAtLeast(0L),
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            isPlaying = exoPlayer.isPlaying,
            isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
            playbackSpeed = exoPlayer.playbackParameters.speed,
            selectedSubtitleLanguage = _state.value.selectedSubtitleLanguage,
            currentSubtitleIndex = _state.value.currentSubtitleIndex,
            error = _state.value.error,
        )
    }

    fun release() = exoPlayer.release()

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
