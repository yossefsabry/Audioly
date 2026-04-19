package com.audioly.shared.extraction

/**
 * A single search result item from YouTube search.
 */
data class SearchResult(
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long,
)
