package com.audioly.shared.player

import com.audioly.shared.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.*
import platform.darwin.dispatch_get_main_queue

/**
 * iOS AudioPlayer using AVFoundation AVPlayer.
 * Implements [PlayerHandle] for the shared PlayerRepository.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioPlayer : PlayerHandle {

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var avPlayer: AVPlayer? = null
    private var timeObserver: Any? = null

    override fun load(
        audioUrl: String,
        videoId: String,
        title: String,
        uploader: String,
        thumbnailUrl: String,
        durationMs: Long,
    ) {
        AppLogger.d(TAG, "Loading: $videoId")

        // Release previous player
        release()

        _state.value = PlayerState(
            videoId = videoId,
            title = title,
            uploader = uploader,
            thumbnailUrl = thumbnailUrl,
            durationMs = durationMs,
            isBuffering = true,
        )

        val url = NSURL.URLWithString(audioUrl) ?: run {
            _state.value = _state.value.copy(
                error = "Invalid audio URL",
                isBuffering = false,
            )
            return
        }

        val playerItem = AVPlayerItem(uRL = url)
        val player = AVPlayer(playerItem = playerItem)
        avPlayer = player

        // Observe playback position
        val interval = CMTimeMakeWithSeconds(0.5, 600)
        timeObserver = player.addPeriodicTimeObserverForInterval(
            interval = interval,
            queue = dispatch_get_main_queue()
        ) { time ->
            val positionMs = (CMTimeGetSeconds(time) * 1000).toLong()
            _state.value = _state.value.copy(
                positionMs = positionMs,
                isBuffering = false,
                isPlaying = player.rate > 0f,
            )
        }

        player.play()
    }

    override fun play() {
        avPlayer?.play()
        _state.value = _state.value.copy(isPlaying = true)
    }

    override fun pause() {
        avPlayer?.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    override fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        val time = CMTimeMakeWithSeconds(positionMs.toDouble() / 1000.0, 600)
        avPlayer?.seekToTime(time)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    override fun skipForward(intervalMs: Long) {
        val current = _state.value.positionMs
        val target = (current + intervalMs).coerceAtMost(_state.value.durationMs)
        seekTo(target)
    }

    override fun skipBack(intervalMs: Long) {
        val current = _state.value.positionMs
        val target = (current - intervalMs).coerceAtLeast(0L)
        seekTo(target)
    }

    override fun setSpeed(speed: Float) {
        avPlayer?.rate = speed
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    override fun setSubtitleLanguage(languageCode: String) {
        _state.value = _state.value.copy(selectedSubtitleLanguage = languageCode)
    }

    override fun setSubtitleIndex(index: Int) {
        _state.value = _state.value.copy(currentSubtitleIndex = index)
    }

    private fun release() {
        timeObserver?.let { observer ->
            avPlayer?.removeTimeObserver(observer)
        }
        timeObserver = null
        avPlayer?.pause()
        avPlayer = null
    }

    private companion object {
        const val TAG = "IosAudioPlayer"
    }
}
