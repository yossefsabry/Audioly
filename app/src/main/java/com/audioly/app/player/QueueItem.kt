package com.audioly.app.player

/**
 * A single item in the playback queue.
 * Contains all info needed to start playback without re-extraction
 * (for cached tracks) or to trigger extraction (via videoId).
 */
data class QueueItem(
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    /** Non-null if the audio URL is already known (extracted or cached). */
    val audioUrl: String? = null,
)
