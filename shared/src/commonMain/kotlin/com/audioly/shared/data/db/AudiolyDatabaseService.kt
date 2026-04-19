package com.audioly.shared.data.db

import com.audioly.shared.data.model.Track
import com.audioly.shared.data.model.Playlist
import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic database interface.
 * Android: Room implementation, iOS: SQLDelight or Room KMP.
 */
interface AudiolyDatabaseService {
    // ─── Track operations ────────────────────────────────────────────────────
    fun getAllTracks(): Flow<List<Track>>
    suspend fun getTrackById(videoId: String): Track?
    suspend fun insertTrack(track: Track)
    suspend fun updateTrack(track: Track)
    suspend fun deleteTrack(videoId: String)
    suspend fun getRecentTracks(limit: Int = 50): List<Track>
    suspend fun getMostPlayedTracks(limit: Int = 50): List<Track>

    // ─── Playlist operations ─────────────────────────────────────────────────
    fun getAllPlaylists(): Flow<List<Playlist>>
    suspend fun getPlaylistById(id: Long): Playlist?
    suspend fun createPlaylist(name: String): Long
    suspend fun deletePlaylist(id: Long)
    suspend fun getTracksForPlaylist(playlistId: Long): List<Track>
    suspend fun addTrackToPlaylist(playlistId: Long, videoId: String)
    suspend fun removeTrackFromPlaylist(playlistId: Long, videoId: String)
}
