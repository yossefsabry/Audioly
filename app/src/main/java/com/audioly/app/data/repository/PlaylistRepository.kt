package com.audioly.app.data.repository

import com.audioly.app.data.db.dao.PlaylistDao
import com.audioly.app.data.db.entities.PlaylistEntity
import com.audioly.app.data.db.entities.PlaylistTrackEntity
import com.audioly.app.data.model.Playlist
import com.audioly.app.data.model.Track
import com.audioly.app.data.db.entities.TrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaylistRepository(private val dao: PlaylistDao) {

    // ─── Observe ──────────────────────────────────────────────────────────────

    fun observePlaylists(): Flow<List<Playlist>> =
        dao.observePlaylists().map { it.map(PlaylistEntity::toDomain) }

    fun observePlaylistTracks(playlistId: Long): Flow<List<Track>> =
        dao.observePlaylistTracks(playlistId).map { it.map(TrackEntity::toDomain) }

    // ─── Playlist CRUD ────────────────────────────────────────────────────────

    suspend fun createPlaylist(name: String): Long =
        dao.createPlaylist(PlaylistEntity(name = name))

    suspend fun renamePlaylist(id: Long, name: String) =
        dao.renamePlaylist(id, name)

    suspend fun deletePlaylist(id: Long) =
        dao.deletePlaylist(id)

    suspend fun getPlaylist(id: Long): Playlist? =
        dao.getPlaylist(id)?.toDomain()

    // ─── Playlist tracks ──────────────────────────────────────────────────────

    suspend fun addTrackToPlaylist(playlistId: Long, videoId: String) {
        val nextPos = (dao.maxPosition(playlistId) ?: -1) + 1
        dao.addTrack(PlaylistTrackEntity(playlistId = playlistId, videoId = videoId, position = nextPos))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, videoId: String) =
        dao.removeTrack(playlistId, videoId)

    suspend fun reorderPlaylist(playlistId: Long, orderedVideoIds: List<String>) =
        dao.reorderPlaylist(playlistId, orderedVideoIds)
}

// ─── Mappers ──────────────────────────────────────────────────────────────────

private fun PlaylistEntity.toDomain() = Playlist(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun TrackEntity.toDomain() = Track(
    videoId = videoId,
    title = title,
    uploader = uploader,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    audioFilePath = audioFilePath,
    audioStreamUrl = audioStreamUrl,
    lastPlayedAt = lastPlayedAt,
    playCount = playCount,
    addedAt = addedAt,
)
