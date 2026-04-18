package com.audioly.app.player

/**
 * Synchronizes a list of [SubtitleCue] to a playback position.
 *
 * Uses binary search for O(log n) cue lookup — suitable for calling on every
 * position tick without allocating.
 */
class SubtitleManager(cues: List<SubtitleCue> = emptyList()) {

    var cues: List<SubtitleCue> = cues
        private set

    val isEmpty: Boolean get() = cues.isEmpty()

    /** Replace the cue list (e.g. when the user switches subtitle language). */
    fun load(newCues: List<SubtitleCue>) {
        cues = newCues
    }

    /**
     * Returns the index of the first cue active at [positionMs], or -1.
     * If multiple cues overlap, the first one in display order is returned.
     */
    fun activeIndex(positionMs: Long): Int {
        if (cues.isEmpty()) return -1
        // Binary search for a starting point
        var lo = 0
        var hi = cues.size - 1
        var candidate = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cue = cues[mid]
            when {
                positionMs < cue.startMs -> hi = mid - 1
                positionMs >= cue.endMs -> lo = mid + 1
                else -> { candidate = mid; hi = mid - 1 } // keep searching left for earliest overlapping cue
            }
        }
        return candidate
    }

    /** Returns the active cue or null. */
    fun activeCue(positionMs: Long): SubtitleCue? {
        val idx = activeIndex(positionMs)
        return if (idx >= 0) cues[idx] else null
    }
}
