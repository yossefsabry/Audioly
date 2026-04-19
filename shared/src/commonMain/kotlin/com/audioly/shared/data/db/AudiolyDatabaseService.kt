package com.audioly.shared.data.db

import com.audioly.shared.data.model.Track
import com.audioly.shared.data.model.Playlist
import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic database interface.
 * Android: Room implementation, iOS: SQLDelight.
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

    /** Increment playCount and update lastPlayedAt. */
    suspend fun recordPlay(videoId: String, playedAt: Long)

    /** Set the local cached audio file path (null = not cached). */
    suspend fun setAudioFilePath(videoId: String, path: String?)

    /** Set the last-fetched audio stream URL. */
    suspend fun setAudioStreamUrl(videoId: String, url: String?)

    /** Persist the last playback position in milliseconds. */
    suspend fun setLastPosition(videoId: String, positionMs: Long)

    /** Retrieve the last saved playback position, or null if unknown. */
    suspend fun getLastPosition(videoId: String): Long?

    /** Reactive stream of play history (tracks with lastPlayedAt > 0, newest first). */
    fun observeHistory(): Flow<List<Track>>

    /** Reactive stream of locally cached tracks (audioFilePath != null). */
    fun observeCached(): Flow<List<Track>>

    // ─── Playlist operations ─────────────────────────────────────────────────
    fun getAllPlaylists(): Flow<List<Playlist>>
    suspend fun getPlaylistById(id: Long): Playlist?
    suspend fun createPlaylist(name: String): Long
    suspend fun deletePlaylist(id: Long)
    suspend fun renamePlaylist(id: Long, name: String)
    suspend fun getTracksForPlaylist(playlistId: Long): List<Track>
    suspend fun addTrackToPlaylist(playlistId: Long, videoId: String)
    suspend fun removeTrackFromPlaylist(playlistId: Long, videoId: String)

    /** Reactive stream of tracks in a playlist, ordered by position. */
    fun observePlaylistTracks(playlistId: Long): Flow<List<Track>>

    /** Change a track's position within a playlist. */
    suspend fun reorderPlaylist(playlistId: Long, videoId: String, newPosition: Int)
}
