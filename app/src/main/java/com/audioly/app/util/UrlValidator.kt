package com.audioly.app.util

import java.net.URI

/**
 * Validates and normalizes YouTube URLs, extracting the video ID.
 *
 * Supported formats:
 *  - https://www.youtube.com/watch?v=VIDEO_ID
 *  - https://m.youtube.com/watch?v=VIDEO_ID
 *  - https://music.youtube.com/watch?v=VIDEO_ID
 *  - https://youtu.be/VIDEO_ID
 *  - https://youtube.com/watch?v=VIDEO_ID (no www)
 */
object UrlValidator {

    private val YOUTUBE_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com"
    )
    private val SHORT_HOSTS = setOf("youtu.be", "www.youtu.be")

    /** Returns video ID if URL is a valid YouTube watch URL, null otherwise. */
    fun extractVideoId(url: String): String? {
        val trimmed = url.trim()
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null

            val host = uri.host?.lowercase() ?: return null

            when {
                host in YOUTUBE_HOSTS -> {
                    if (uri.path == "/watch") {
                        uri.query?.split("&")
                            ?.firstOrNull { it.startsWith("v=") }
                            ?.removePrefix("v=")
                            ?.takeIf { it.isNotBlank() }
                    } else null
                }
                host in SHORT_HOSTS -> {
                    uri.path?.removePrefix("/")?.takeIf { it.isNotBlank() && !it.contains("/") }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns normalized canonical URL for a video ID.
     * e.g. "dQw4w9WgXcQ" → "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
     */
    fun canonicalUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    /** True if [url] resolves to a valid YouTube video. */
    fun isValid(url: String): Boolean = extractVideoId(url) != null
}
