package com.audioly.shared.player

/**
 * Immutable snapshot of player state.
 * Emitted as a [kotlinx.coroutines.flow.StateFlow] by [PlayerRepository].
 */
data class PlayerState(
    val videoId: String? = null,
    val title: String = "",
    val uploader: String = "",
    val thumbnailUrl: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val cachedBytes: Long = 0L,
    val cacheContentLength: Long = 0L,
    val hasCachedAudio: Boolean = false,
    val isFullyCached: Boolean = false,
    val selectedSubtitleLanguage: String = "",
    val currentSubtitleIndex: Int = -1,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = videoId == null
    val progressFraction: Float
        get() = if (durationMs > 0L) positionMs.toFloat() / durationMs else 0f
    val bufferedFraction: Float
        get() = if (durationMs > 0L) bufferedPositionMs.toFloat() / durationMs else 0f
}
