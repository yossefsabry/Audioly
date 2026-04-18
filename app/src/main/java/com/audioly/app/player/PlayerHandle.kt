package com.audioly.app.player

import kotlinx.coroutines.flow.StateFlow

interface PlayerHandle {
    val state: StateFlow<PlayerState>

    fun load(
        audioUrl: String,
        videoId: String,
        title: String,
        uploader: String,
        thumbnailUrl: String,
        durationMs: Long,
    )

    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun skipForward(intervalMs: Long)
    fun skipBack(intervalMs: Long)
    fun setSpeed(speed: Float)
    fun setSubtitleLanguage(languageCode: String)
    fun setSubtitleIndex(index: Int)
}
