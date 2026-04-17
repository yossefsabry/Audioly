package com.audioly.app.player

/**
 * Parses WebVTT subtitle files into a list of [SubtitleCue].
 *
 * Handles:
 * - Standard `HH:MM:SS.mmm` and `MM:SS.mmm` timestamp formats
 * - Multi-line cue text
 * - Blank-line-separated cue blocks
 * - Optional cue identifiers (numeric or string IDs before the timestamp line)
 * - VTT tag stripping (<c>, <b>, <i>, <u>, timestamps inside cues)
 */
object VttParser {

    private val TIMESTAMP_ARROW = Regex(
        """(\d{1,2}:)?(\d{2}):(\d{2})\.(\d{3})\s*-->\s*(\d{1,2}:)?(\d{2}):(\d{2})\.(\d{3})""",
    )

    private val TAG_REGEX = Regex("""<[^>]+>""")

    private val BLOCK_SEPARATOR = Regex("""\r?\n\r?\n+""")

    fun parse(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        // Split into blocks separated by one or more blank lines
        val blocks = content.split(BLOCK_SEPARATOR)

        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.isEmpty()) continue

            // Find the line that contains the '-->' arrow
            val arrowIndex = lines.indexOfFirst { TIMESTAMP_ARROW.containsMatchIn(it) }
            if (arrowIndex < 0) continue

            val match = TIMESTAMP_ARROW.find(lines[arrowIndex]) ?: continue
            val startMs = parseTimestamp(match, startGroup = true)
            val endMs = parseTimestamp(match, startGroup = false)

            // Remaining lines after the timestamp line are the cue text
            val textLines = lines.drop(arrowIndex + 1)
            val text = textLines
                .joinToString("\n")
                .let { TAG_REGEX.replace(it, "") }
                .trim()

            if (text.isNotEmpty()) {
                cues += SubtitleCue(startMs, endMs, text)
            }
        }

        return cues.sortedBy { it.startMs }
    }

    // ─── Timestamp parsing ────────────────────────────────────────────────────

    /**
     * Extracts start (groups 1-4) or end (groups 5-8) timestamp from a match.
     * Groups: optional-hours, minutes, seconds, milliseconds.
     */
    private fun parseTimestamp(match: MatchResult, startGroup: Boolean): Long {
        val offset = if (startGroup) 0 else 4
        val hours = match.groupValues[1 + offset].trimEnd(':').toLongOrNull() ?: 0L
        val minutes = match.groupValues[2 + offset].toLongOrNull() ?: 0L
        val seconds = match.groupValues[3 + offset].toLongOrNull() ?: 0L
        val millis = match.groupValues[4 + offset].toLongOrNull() ?: 0L
        return (hours * 3600 + minutes * 60 + seconds) * 1000L + millis
    }
}
