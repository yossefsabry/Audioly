package com.audioly.shared.util

/**
 * Validates and normalizes YouTube URLs, extracting the video ID.
 * Pure Kotlin — no java.net.URI dependency.
 *
 * Supported formats:
 *  - https://www.youtube.com/watch?v=VIDEO_ID
 *  - https://m.youtube.com/watch?v=VIDEO_ID
 *  - https://music.youtube.com/watch?v=VIDEO_ID
 *  - https://youtu.be/VIDEO_ID
 *  - https://youtube.com/watch?v=VIDEO_ID
 *  - https://www.youtube.com/shorts/VIDEO_ID
 *  - https://www.youtube.com/embed/VIDEO_ID
 *  - https://www.youtube.com/v/VIDEO_ID
 *  - https://www.youtube.com/live/VIDEO_ID
 */
object UrlValidator {

    private val YOUTUBE_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
    )
    private val SHORT_HOSTS = setOf("youtu.be", "www.youtu.be")

    private val PATH_ID_PREFIXES = setOf("/shorts/", "/embed/", "/v/", "/live/")

    /**
     * Regex to parse a URL into scheme, host, path, query.
     * Groups: 1=scheme, 2=host, 3=path (optional), 4=query (optional)
     */
    private val URL_REGEX = Regex(
        """^(https?)://([^/?#]+)(/[^?#]*)?(?:\?([^#]*))?""",
        RegexOption.IGNORE_CASE,
    )

    /** Returns video ID if URL is a valid YouTube watch URL, null otherwise. */
    fun extractVideoId(url: String): String? {
        val trimmed = url.trim()
        val match = URL_REGEX.find(trimmed) ?: return null

        val host = match.groupValues[2].lowercase()
        val path = match.groupValues[3].ifEmpty { "/" }
        val query = match.groupValues[4]

        return when {
            host in YOUTUBE_HOSTS -> {
                when {
                    // Standard /watch?v=ID
                    path == "/watch" -> {
                        query.split("&")
                            .firstOrNull { it.startsWith("v=") }
                            ?.removePrefix("v=")
                            ?.takeIf { it.isNotBlank() }
                    }
                    // /shorts/ID, /embed/ID, /v/ID, /live/ID
                    else -> {
                        PATH_ID_PREFIXES.firstNotNullOfOrNull { prefix ->
                            if (path.startsWith(prefix)) {
                                path.removePrefix(prefix)
                                    .split("/").firstOrNull()
                                    ?.split("?")?.firstOrNull()
                                    ?.takeIf { it.isNotBlank() }
                            } else null
                        }
                    }
                }
            }
            host in SHORT_HOSTS -> {
                path.removePrefix("/")
                    .takeIf { it.isNotBlank() && !it.contains("/") }
            }
            else -> null
        }
    }

    /** Returns normalized canonical URL for a video ID. */
    fun canonicalUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    /** True if [url] resolves to a valid YouTube video. */
    fun isValid(url: String): Boolean = extractVideoId(url) != null
}
