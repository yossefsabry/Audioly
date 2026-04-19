package com.audioly.shared.player

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
        """(\d{1,2}:)?(\d{2}):(\d{2})[\.,](\d{3})\s*-->\s*(\d{1,2}:)?(\d{2}):(\d{2})[\.,](\d{3})""",
    )

    private val TAG_REGEX = Regex("""<[^>]+>""")
    private val WEBVTT_HEADER = Regex("""^WEBVTT.*$""", RegexOption.MULTILINE)

    private val BLOCK_SEPARATOR = Regex("""\r?\n\r?\n+""")

    fun parse(content: String): List<SubtitleCue> {
        if (content.isBlank()) return emptyList()

        val normalizedContent = content
            .replace(WEBVTT_HEADER, "")
            .replace("\r\n", "\n")

        val cues = mutableListOf<SubtitleCue>()
        val blocks = normalizedContent.split(BLOCK_SEPARATOR)

        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.isEmpty()) continue

            val arrowIndex = lines.indexOfFirst { TIMESTAMP_ARROW.containsMatchIn(it) }
            if (arrowIndex < 0) continue

            val match = TIMESTAMP_ARROW.find(lines[arrowIndex]) ?: continue
            val startMs = parseTimestamp(match, startGroup = true)
            val endMs = parseTimestamp(match, startGroup = false)

            val textLines = lines.drop(arrowIndex + 1)
            val text = textLines
                .joinToString("\n")
                .let { TAG_REGEX.replace(it, "") }
                .trim()

            if (text.isNotEmpty() && endMs > startMs) {
                cues += SubtitleCue(startMs, endMs, text)
            }
        }

        return cues.sortedBy { it.startMs }
    }

    private fun parseTimestamp(match: MatchResult, startGroup: Boolean): Long {
        val offset = if (startGroup) 0 else 4
        val hours = match.groupValues[1 + offset].trimEnd(':').toLongOrNull() ?: 0L
        val minutes = match.groupValues[2 + offset].toLongOrNull() ?: 0L
        val seconds = match.groupValues[3 + offset].toLongOrNull() ?: 0L
        val millis = match.groupValues[4 + offset].toLongOrNull() ?: 0L
        return (hours * 3600 + minutes * 60 + seconds) * 1000L + millis
    }
}
