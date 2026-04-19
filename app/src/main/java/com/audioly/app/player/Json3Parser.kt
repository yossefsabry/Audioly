package com.audioly.app.player

import com.audioly.app.util.AppLogger
import org.json.JSONObject

/**
 * Parses YouTube's json3 subtitle format into a VTT string.
 *
 * YouTube's timedtext API only returns **translated** subtitle content reliably
 * in json3 format (not VTT). When we request auto-translated subtitles
 * (e.g. `&tlang=en&fmt=json3`), YouTube returns:
 *
 * ```json
 * {
 *   "events": [
 *     { "tStartMs": 1740, "dDurationMs": 2980, "segs": [{"utf8": "translated text"}] },
 *     ...
 *   ]
 * }
 * ```
 *
 * This parser converts that to standard WebVTT so the rest of the subtitle
 * pipeline (VttParser, SubtitleView, caching) works unchanged.
 */
object Json3Parser {

    private const val TAG = "Json3Parser"

    /**
     * Converts a YouTube json3 subtitle response to WebVTT format string.
     * Returns null if the input cannot be parsed or contains no valid cues.
     */
    fun toVtt(json3Content: String): String? {
        return try {
            val json = JSONObject(json3Content)
            val events = json.optJSONArray("events") ?: run {
                AppLogger.w(TAG, "json3 response has no 'events' array")
                return null
            }

            val sb = StringBuilder("WEBVTT\n\n")
            var cueCount = 0

            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue

                val startMs = event.optLong("tStartMs", -1)
                if (startMs < 0) continue

                val durationMs = event.optLong("dDurationMs", 0)
                val endMs = startMs + durationMs
                if (endMs <= startMs) continue

                val segs = event.optJSONArray("segs") ?: continue
                val text = buildString {
                    for (j in 0 until segs.length()) {
                        val seg = segs.optJSONObject(j) ?: continue
                        append(seg.optString("utf8", ""))
                    }
                }.trim()

                if (text.isEmpty() || text == "\n") continue

                cueCount++
                sb.append(formatTimestamp(startMs))
                sb.append(" --> ")
                sb.append(formatTimestamp(endMs))
                sb.append("\n")
                sb.append(text)
                sb.append("\n\n")
            }

            if (cueCount == 0) {
                AppLogger.w(TAG, "json3 parsed but produced 0 valid cues")
                return null
            }

            AppLogger.d(TAG, "Converted json3 → VTT: $cueCount cues")
            sb.toString()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to parse json3: ${e.message}")
            null
        }
    }

    private fun formatTimestamp(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1_000
        val mil = ms % 1_000
        return "%02d:%02d:%02d.%03d".format(h, m, s, mil)
    }
}
