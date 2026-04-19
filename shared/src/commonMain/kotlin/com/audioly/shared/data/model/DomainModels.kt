package com.audioly.shared.data.model

/** Domain model for a playable track. */
data class Track(
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val audioFilePath: String?,
    val audioStreamUrl: String?,
    val lastPlayedAt: Long,
    val playCount: Int,
    val addedAt: Long,
    val lastPositionMs: Long = 0L,
)

/** Domain model for a playlist. */
data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)
