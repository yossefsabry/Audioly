package com.audioly.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.audioly.app.data.db.entities.PlaylistEntity
import com.audioly.app.data.db.entities.PlaylistTrackEntity
import com.audioly.app.data.db.entities.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    // ─── Playlist CRUD ────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :nowMs WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String, nowMs: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylist(id: Long): PlaylistEntity?

    // ─── Playlist tracks ──────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrack(entry: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeTrack(playlistId: Long, videoId: String)

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.videoId = pt.videoId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """,
    )
    fun observePlaylistTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int?

    @Query(
        "UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND videoId = :videoId",
    )
    suspend fun updatePosition(playlistId: Long, videoId: String, position: Int)

    /** Replace entire ordering of a playlist atomically. */
    @Transaction
    suspend fun reorderPlaylist(playlistId: Long, orderedVideoIds: List<String>) {
        orderedVideoIds.forEachIndexed { index, videoId ->
            updatePosition(playlistId, videoId, index)
        }
        touchPlaylist(playlistId)
    }

    @Query("UPDATE playlists SET updatedAt = :nowMs WHERE id = :id")
    suspend fun touchPlaylist(id: Long, nowMs: Long = System.currentTimeMillis())
}
