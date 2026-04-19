package com.audioly.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlValidatorTest {

    // --- Valid YouTube URLs ---
    @Test fun full_youtube_watch_url_extracts_video_id() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }
    @Test fun youtube_without_www_extracts_video_id() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("https://youtube.com/watch?v=dQw4w9WgXcQ"))
    }
    @Test fun mobile_youtube_url_extracts_video_id() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
    }
    @Test fun music_youtube_url_extracts_video_id() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
    }
    @Test fun short_youtu_be_url_extracts_video_id() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
    }
    @Test fun url_with_trailing_whitespace_is_trimmed() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("  https://youtu.be/dQw4w9WgXcQ  "))
    }
    @Test fun http_scheme_accepted() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("http://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    // --- Invalid URLs ---
    @Test fun non_youtube_host_returns_null() {
        assertNull(UrlValidator.extractVideoId("https://vimeo.com/watch?v=dQw4w9WgXcQ"))
    }
    @Test fun missing_video_id_param_returns_null() {
        assertNull(UrlValidator.extractVideoId("https://www.youtube.com/watch"))
    }
    @Test fun youtube_homepage_returns_null() {
        assertNull(UrlValidator.extractVideoId("https://www.youtube.com/"))
    }
    @Test fun ftp_scheme_returns_null() {
        assertNull(UrlValidator.extractVideoId("ftp://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }
    @Test fun random_text_returns_null() {
        assertNull(UrlValidator.extractVideoId("not a url at all"))
    }
    @Test fun empty_string_returns_null() {
        assertNull(UrlValidator.extractVideoId(""))
    }
    @Test fun blank_string_returns_null() {
        assertNull(UrlValidator.extractVideoId("   "))
    }

    // --- isValid ---
    @Test fun isValid_true_for_valid_url() {
        assertTrue(UrlValidator.isValid("https://youtu.be/dQw4w9WgXcQ"))
    }
    @Test fun isValid_false_for_invalid_url() {
        assertFalse(UrlValidator.isValid("https://twitter.com/something"))
    }

    // --- canonicalUrl ---
    @Test fun canonical_url_builds_correctly() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlValidator.canonicalUrl("dQw4w9WgXcQ")
        )
    }

    // --- Path-based YouTube URLs ---
    @Test fun shorts_url_extracts_video_id() {
        assertEquals("abc123", UrlValidator.extractVideoId("https://www.youtube.com/shorts/abc123"))
    }
    @Test fun embed_url_extracts_video_id() {
        assertEquals("abc123", UrlValidator.extractVideoId("https://www.youtube.com/embed/abc123"))
    }
    @Test fun v_path_url_extracts_video_id() {
        assertEquals("abc123", UrlValidator.extractVideoId("https://www.youtube.com/v/abc123"))
    }
    @Test fun live_url_extracts_video_id() {
        assertEquals("abc123", UrlValidator.extractVideoId("https://www.youtube.com/live/abc123"))
    }
    @Test fun shorts_url_with_trailing_slash() {
        assertEquals("abc123", UrlValidator.extractVideoId("https://www.youtube.com/shorts/abc123/"))
    }
    @Test fun watch_url_with_extra_params() {
        assertEquals("dQw4w9WgXcQ", UrlValidator.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30"))
    }
}
