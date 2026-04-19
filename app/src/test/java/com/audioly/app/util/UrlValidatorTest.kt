package com.audioly.app.util

import com.audioly.shared.util.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class UrlValidatorTest {

    // --- Valid YouTube URLs ---
    @Test fun `full youtube watch url extracts video id`() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }
    @Test fun `youtube without www extracts video id`() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("https://youtube.com/watch?v=dQw4w9WgXcQ"))
    }
    @Test fun `mobile youtube url extracts video id`() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
    }
    @Test fun `music youtube url extracts video id`() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
    }
    @Test fun `short youtu be url extracts video id`() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
    }
    @Test fun `short url with extra params still extracts video id`() {
        // youtu.be short links only have video id as path, no sub-path
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
    }
    @Test fun `url with trailing whitespace is trimmed`() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("  https://youtu.be/dQw4w9WgXcQ  "))
    }
    @Test fun `http scheme accepted`() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("http://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    // --- Invalid URLs ---
    @Test fun `non youtube host returns null`() {
        assertNull(UrlValidator.extractVideoId("https://vimeo.com/watch?v=dQw4w9WgXcQ"))
    }
    @Test fun `missing video id param returns null`() {
        assertNull(UrlValidator.extractVideoId("https://www.youtube.com/watch"))
    }
    @Test fun `youtube homepage returns null`() {
        assertNull(UrlValidator.extractVideoId("https://www.youtube.com/"))
    }
    @Test fun `ftp scheme returns null`() {
        assertNull(UrlValidator.extractVideoId("ftp://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }
    @Test fun `random text returns null`() {
        assertNull(UrlValidator.extractVideoId("not a url at all"))
    }
    @Test fun `empty string returns null`() {
        assertNull(UrlValidator.extractVideoId(""))
    }
    @Test fun `blank string returns null`() {
        assertNull(UrlValidator.extractVideoId("   "))
    }

    // --- isValid ---
    @Test fun `isValid true for valid url`() {
        assertTrue(UrlValidator.isValid("https://youtu.be/dQw4w9WgXcQ"))
    }
    @Test fun `isValid false for invalid url`() {
        assertFalse(UrlValidator.isValid("https://twitter.com/something"))
    }

    // --- canonicalUrl ---
    @Test fun `canonical url builds correctly`() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlValidator.canonicalUrl("dQw4w9WgXcQ")
        )
    }

    // --- Path-based YouTube URLs ---
    @Test fun `shorts url extracts video id`() {
        assertEquals("abc123", UrlValidator.extractVideoId("https://www.youtube.com/shorts/abc123"))
    }
    @Test fun `embed url extracts video id`() {
        assertEquals("abc123", UrlValidator.extractVideoId("https://www.youtube.com/embed/abc123"))
    }
    @Test fun `v path url extracts video id`() {
        assertEquals("abc123", UrlValidator.extractVideoId("https://www.youtube.com/v/abc123"))
    }
    @Test fun `live url extracts video id`() {
        assertEquals("abc123", UrlValidator.extractVideoId("https://www.youtube.com/live/abc123"))
    }
    @Test fun `shorts url with trailing slash`() {
        assertEquals("abc123", UrlValidator.extractVideoId("https://www.youtube.com/shorts/abc123/"))
    }
    @Test fun `watch url with extra params extracts video id`() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30"))
    }
}
