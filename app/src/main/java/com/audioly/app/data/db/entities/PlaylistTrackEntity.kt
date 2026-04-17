package com.audioly.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join table linking tracks to playlists.
 * [position] determines display order within a playlist.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["videoId"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index("videoId"),
    ],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val videoId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
)
