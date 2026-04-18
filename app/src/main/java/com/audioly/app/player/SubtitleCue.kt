package com.audioly.app.player

/**
 * A single subtitle cue parsed from a VTT or SRT file.
 */
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
) {
    fun isActiveAt(positionMs: Long): Boolean = positionMs >= startMs && positionMs < endMs
}
