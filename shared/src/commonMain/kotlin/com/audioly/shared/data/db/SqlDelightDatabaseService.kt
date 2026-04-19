package com.audioly.shared.data.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.audioly.shared.data.model.Playlist
import com.audioly.shared.data.model.Track
import com.audioly.shared.db.AudiolyDb
import com.audioly.shared.db.SelectCached
import com.audioly.shared.db.Tracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * SQLDelight implementation of [AudiolyDatabaseService].
 * Used on iOS (and optionally other platforms) for persistent storage.
 */
class SqlDelightDatabaseService(driverFactory: DatabaseDriverFactory) : AudiolyDatabaseService {

    private val db = AudiolyDb(driverFactory.createDriver())
    private val tracksQ = db.tracksQueries
    private val playlistsQ = db.playlistsQueries
    private val ptQ = db.playlistTracksQueries

    // ─── Track operations ────────────────────────────────────────────────────

    override fun getAllTracks(): Flow<List<Track>> =
        tracksQ.selectAll().asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getTrackById(videoId: String): Track? =
        withContext(Dispatchers.Default) {
            tracksQ.selectById(videoId).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun insertTrack(track: Track) =
        withContext(Dispatchers.Default) {
            tracksQ.insert(
                videoId = track.videoId,
                title = track.title,
                uploader = track.uploader,
                thumbnailUrl = track.thumbnailUrl,
                durationSeconds = track.durationSeconds,
                audioFilePath = track.audioFilePath,
                audioStreamUrl = track.audioStreamUrl,
                lastPlayedAt = track.lastPlayedAt,
                playCount = track.playCount.toLong(),
                addedAt = track.addedAt,
                lastPositionMs = track.lastPositionMs,
            )
        }

    override suspend fun updateTrack(track: Track) =
        withContext(Dispatchers.Default) {
            tracksQ.update(
                title = track.title,
                uploader = track.uploader,
                thumbnailUrl = track.thumbnailUrl,
                durationSeconds = track.durationSeconds,
                audioFilePath = track.audioFilePath,
                audioStreamUrl = track.audioStreamUrl,
                lastPlayedAt = track.lastPlayedAt,
                playCount = track.playCount.toLong(),
                addedAt = track.addedAt,
                lastPositionMs = track.lastPositionMs,
                videoId = track.videoId,
            )
        }

    override suspend fun deleteTrack(videoId: String) =
        withContext(Dispatchers.Default) {
            tracksQ.delete(videoId)
        }

    override suspend fun getRecentTracks(limit: Int): List<Track> =
        withContext(Dispatchers.Default) {
            tracksQ.selectRecent(limit.toLong()).executeAsList().map { it.toDomain() }
        }

    override suspend fun getMostPlayedTracks(limit: Int): List<Track> =
        withContext(Dispatchers.Default) {
            tracksQ.selectMostPlayed(limit.toLong()).executeAsList().map { it.toDomain() }
        }

    override suspend fun recordPlay(videoId: String, playedAt: Long) =
        withContext(Dispatchers.Default) {
            tracksQ.recordPlay(lastPlayedAt = playedAt, videoId = videoId)
        }

    override suspend fun setAudioFilePath(videoId: String, path: String?) =
        withContext(Dispatchers.Default) {
            tracksQ.setAudioFilePath(audioFilePath = path, videoId = videoId)
        }

    override suspend fun setAudioStreamUrl(videoId: String, url: String?) =
        withContext(Dispatchers.Default) {
            tracksQ.setAudioStreamUrl(audioStreamUrl = url, videoId = videoId)
        }

    override suspend fun setLastPosition(videoId: String, positionMs: Long) =
        withContext(Dispatchers.Default) {
            tracksQ.setLastPosition(lastPositionMs = positionMs, videoId = videoId)
        }

    override suspend fun getLastPosition(videoId: String): Long? =
        withContext(Dispatchers.Default) {
            tracksQ.getLastPosition(videoId).executeAsOneOrNull()
        }

    override fun observeHistory(): Flow<List<Track>> =
        tracksQ.selectHistory().asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toDomain() }
        }

    override fun observeCached(): Flow<List<Track>> =
        tracksQ.selectCached().asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toDomain() }
        }

    // ─── Playlist operations ─────────────────────────────────────────────────

    override fun getAllPlaylists(): Flow<List<Playlist>> =
        playlistsQ.selectAll().asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getPlaylistById(id: Long): Playlist? =
        withContext(Dispatchers.Default) {
            playlistsQ.selectById(id).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun createPlaylist(name: String): Long =
        withContext(Dispatchers.Default) {
            val now = Clock.System.now().toEpochMilliseconds()
            playlistsQ.insert(name = name, createdAt = now, updatedAt = now)
            playlistsQ.lastInsertRowId().executeAsOne()
        }

    override suspend fun deletePlaylist(id: Long) =
        withContext(Dispatchers.Default) {
            playlistsQ.delete(id)
        }

    override suspend fun renamePlaylist(id: Long, name: String) =
        withContext(Dispatchers.Default) {
            val now = Clock.System.now().toEpochMilliseconds()
            playlistsQ.rename(name = name, updatedAt = now, id = id)
        }

    override suspend fun getTracksForPlaylist(playlistId: Long): List<Track> =
        withContext(Dispatchers.Default) {
            ptQ.selectTracksForPlaylist(playlistId).executeAsList().map { it.toDomain() }
        }

    override suspend fun addTrackToPlaylist(playlistId: Long, videoId: String) =
        withContext(Dispatchers.Default) {
            val now = Clock.System.now().toEpochMilliseconds()
            val maxPos = ptQ.maxPosition(playlistId).executeAsOne().COALESCE ?: -1L
            ptQ.insert(
                playlistId = playlistId,
                videoId = videoId,
                position = maxPos + 1L,
                addedAt = now,
            )
        }

    override suspend fun removeTrackFromPlaylist(playlistId: Long, videoId: String) =
        withContext(Dispatchers.Default) {
            ptQ.delete(playlistId = playlistId, videoId = videoId)
        }

    override fun observePlaylistTracks(playlistId: Long): Flow<List<Track>> =
        ptQ.selectTracksForPlaylist(playlistId).asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun reorderPlaylist(playlistId: Long, videoId: String, newPosition: Int) =
        withContext(Dispatchers.Default) {
            ptQ.updatePosition(
                position = newPosition.toLong(),
                playlistId = playlistId,
                videoId = videoId,
            )
        }

    // ─── Mappers ─────────────────────────────────────────────────────────────

    private fun Tracks.toDomain() = Track(
        videoId = videoId,
        title = title,
        uploader = uploader,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        audioFilePath = audioFilePath,
        audioStreamUrl = audioStreamUrl,
        lastPlayedAt = lastPlayedAt,
        playCount = playCount.toInt(),
        addedAt = addedAt,
        lastPositionMs = lastPositionMs,
    )

    private fun SelectCached.toDomain() = Track(
        videoId = videoId,
        title = title,
        uploader = uploader,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        audioFilePath = audioFilePath, // non-null in SelectCached
        audioStreamUrl = audioStreamUrl,
        lastPlayedAt = lastPlayedAt,
        playCount = playCount.toInt(),
        addedAt = addedAt,
        lastPositionMs = lastPositionMs,
    )

    private fun com.audioly.shared.db.Playlists.toDomain() = Playlist(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
