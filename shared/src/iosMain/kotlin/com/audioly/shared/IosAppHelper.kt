package com.audioly.shared

import com.audioly.shared.extraction.IosStreamExtractor
import com.audioly.shared.extraction.SearchResult
import com.audioly.shared.extraction.StreamInfo
import com.audioly.shared.player.IosAudioPlayer
import com.audioly.shared.player.PlayerRepository
import com.audioly.shared.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Helper class exposed to Swift that hides Kotlin coroutine internals.
 * Swift code should only interact with this class, not raw KMP types.
 */
class IosAppHelper {

    val playerRepository = PlayerRepository()
    private val audioPlayer = IosAudioPlayer()
    private val streamExtractor = IosStreamExtractor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        playerRepository.attach(audioPlayer)
    }

    /** Observe player state changes. Callback fires on main thread. */
    fun observePlayerState(callback: (PlayerState) -> Unit) {
        playerRepository.state
            .onEach { callback(it) }
            .launchIn(scope)
    }

    /** Search YouTube. Results delivered via callback on main thread. */
    fun search(query: String, callback: (List<SearchResult>) -> Unit) {
        scope.launch {
            val results = streamExtractor.search(query)
            callback(results)
        }
    }

    /** Search suggestions. Results delivered via callback on main thread. */
    fun searchSuggestions(query: String, callback: (List<String>) -> Unit) {
        scope.launch {
            val results = streamExtractor.searchSuggestions(query)
            callback(results)
        }
    }

    /** Extract and play a YouTube video by ID or URL. */
    fun extractAndPlay(videoIdOrUrl: String, callback: (Boolean) -> Unit) {
        scope.launch {
            val result = streamExtractor.extractStream(videoIdOrUrl)
            when (result) {
                is com.audioly.shared.extraction.ExtractionResult.Success -> {
                    val info = result.streamInfo
                    playerRepository.load(
                        audioUrl = info.audioStreamUrl,
                        videoId = info.videoId,
                        title = info.title,
                        uploader = info.uploader,
                        thumbnailUrl = info.thumbnailUrl,
                        durationMs = info.durationSeconds * 1000L,
                    )
                    callback(true)
                }
                else -> callback(false)
            }
        }
    }

    fun play() = playerRepository.play()
    fun pause() = playerRepository.pause()
    fun togglePlayPause() = playerRepository.togglePlayPause()
    fun seekTo(positionMs: Long) = playerRepository.seekTo(positionMs)
    fun skipForward() = playerRepository.skipForward()
    fun skipBack() = playerRepository.skipBack()
    fun setSpeed(speed: Float) = playerRepository.setSpeed(speed)
}
