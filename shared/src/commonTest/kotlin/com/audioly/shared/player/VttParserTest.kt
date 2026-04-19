package com.audioly.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VttParserTest {

    private val basicVtt = """
        WEBVTT

        1
        00:00:01.000 --> 00:00:03.500
        Hello world

        2
        00:00:05.000 --> 00:00:07.000
        Second line

        3
        00:01:00.000 --> 00:01:02.000
        Minute mark
    """.trimIndent()

    @Test
    fun parse_returns_correct_cue_count() {
        val cues = VttParser.parse(basicVtt)
        assertEquals(3, cues.size)
    }

    @Test
    fun timestamps_are_parsed_correctly() {
        val cues = VttParser.parse(basicVtt)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(3_500L, cues[0].endMs)
        assertEquals(5_000L, cues[1].startMs)
        assertEquals(60_000L, cues[2].startMs)
    }

    @Test
    fun cue_text_is_extracted() {
        val cues = VttParser.parse(basicVtt)
        assertEquals("Hello world", cues[0].text)
    }

    @Test
    fun multi_line_cue_text_is_joined() {
        val vtt = """
            WEBVTT

            00:00:01.000 --> 00:00:03.000
            Line one
            Line two
        """.trimIndent()
        val cues = VttParser.parse(vtt)
        assertEquals("Line one\nLine two", cues[0].text)
    }

    @Test
    fun html_tags_are_stripped() {
        val vtt = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
            <b>Bold</b> and <i>italic</i>
        """.trimIndent()
        val cues = VttParser.parse(vtt)
        assertEquals("Bold and italic", cues[0].text)
    }

    @Test
    fun srt_timestamps_are_parsed() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,500
            Hello from srt
        """.trimIndent()

        val cues = VttParser.parse(srt)
        assertEquals(1, cues.size)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(2_500L, cues[0].endMs)
        assertEquals("Hello from srt", cues[0].text)
    }

    @Test
    fun empty_content_returns_empty_list() {
        assertEquals(emptyList(), VttParser.parse(""))
    }

    @Test
    fun no_subtitles_block_returns_empty() {
        assertEquals(emptyList(), VttParser.parse("WEBVTT\n\n"))
    }

    // ─── SubtitleManager ──────────────────────────────────────────────────────

    @Test
    fun activeIndex_returns_minus1_before_first_cue() {
        val mgr = SubtitleManager(VttParser.parse(basicVtt))
        assertEquals(-1, mgr.activeIndex(0L))
    }

    @Test
    fun activeIndex_finds_cue_in_range() {
        val mgr = SubtitleManager(VttParser.parse(basicVtt))
        assertEquals(0, mgr.activeIndex(2_000L))
    }

    @Test
    fun activeIndex_returns_minus1_between_cues() {
        val mgr = SubtitleManager(VttParser.parse(basicVtt))
        assertEquals(-1, mgr.activeIndex(4_000L))
    }

    @Test
    fun activeCue_returns_cue() {
        val mgr = SubtitleManager(VttParser.parse(basicVtt))
        val cue = mgr.activeCue(6_000L)
        assertNotNull(cue)
        assertEquals("Second line", cue.text)
    }

    @Test
    fun activeCue_returns_null_outside_range() {
        val mgr = SubtitleManager(VttParser.parse(basicVtt))
        assertNull(mgr.activeCue(10_000L))
    }

    @Test
    fun subtitleCue_isActiveAt_works() {
        val cue = SubtitleCue(1000L, 3000L, "hi")
        assertEquals(true, cue.isActiveAt(2000L))
        assertEquals(false, cue.isActiveAt(500L))
        assertEquals(false, cue.isActiveAt(3000L))
    }
}
