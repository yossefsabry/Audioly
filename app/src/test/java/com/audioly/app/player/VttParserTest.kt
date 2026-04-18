package com.audioly.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun `parse returns correct cue count`() {
        val cues = VttParser.parse(basicVtt)
        assertEquals(3, cues.size)
    }

    @Test
    fun `timestamps are parsed correctly`() {
        val cues = VttParser.parse(basicVtt)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(3_500L, cues[0].endMs)
        assertEquals(5_000L, cues[1].startMs)
        assertEquals(60_000L, cues[2].startMs)
    }

    @Test
    fun `cue text is extracted`() {
        val cues = VttParser.parse(basicVtt)
        assertEquals("Hello world", cues[0].text)
    }

    @Test
    fun `multi-line cue text is joined`() {
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
    fun `html tags are stripped`() {
        val vtt = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
            <b>Bold</b> and <i>italic</i>
        """.trimIndent()
        val cues = VttParser.parse(vtt)
        assertEquals("Bold and italic", cues[0].text)
    }

    @Test
    fun `srt timestamps are parsed correctly`() {
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
    fun `empty content returns empty list`() {
        assertEquals(emptyList<SubtitleCue>(), VttParser.parse(""))
    }

    @Test
    fun `no subtitles block returns empty`() {
        assertEquals(emptyList<SubtitleCue>(), VttParser.parse("WEBVTT\n\n"))
    }

    // ─── SubtitleManager ──────────────────────────────────────────────────────

    @Test
    fun `activeIndex returns -1 before first cue`() {
        val mgr = SubtitleManager(VttParser.parse(basicVtt))
        assertEquals(-1, mgr.activeIndex(0L))
    }

    @Test
    fun `activeIndex finds cue in range`() {
        val mgr = SubtitleManager(VttParser.parse(basicVtt))
        assertEquals(0, mgr.activeIndex(2_000L))
    }

    @Test
    fun `activeIndex returns -1 between cues`() {
        val mgr = SubtitleManager(VttParser.parse(basicVtt))
        assertEquals(-1, mgr.activeIndex(4_000L))
    }

    @Test
    fun `activeCue returns cue`() {
        val mgr = SubtitleManager(VttParser.parse(basicVtt))
        val cue = mgr.activeCue(6_000L)
        assertNotNull(cue)
        assertEquals("Second line", cue!!.text)
    }

    @Test
    fun `activeCue returns null outside range`() {
        val mgr = SubtitleManager(VttParser.parse(basicVtt))
        assertNull(mgr.activeCue(10_000L))
    }

    @Test
    fun `SubtitleCue isActiveAt works`() {
        val cue = SubtitleCue(1000L, 3000L, "hi")
        assert(cue.isActiveAt(2000L))
        assert(!cue.isActiveAt(500L))
        assert(!cue.isActiveAt(3000L))
    }
}
