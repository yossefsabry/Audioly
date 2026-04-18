package com.audioly.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted track. Doubles as history entry via [lastPlayedAt] and [playCount].
 * [audioFilePath] is non-null when audio is cached on disk.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    /** Local path to cached audio file, null when not cached. */
    val audioFilePath: String?,
    /** Last-used audio stream URL (may be expired). Used for cache-backed replay. */
    val audioStreamUrl: String? = null,
    /** Epoch-millis of most recent playback start, 0 when never played. */
    val lastPlayedAt: Long = 0L,
    val playCount: Int = 0,
    /** Epoch-millis when row was first inserted. */
    val addedAt: Long = System.currentTimeMillis(),
)
