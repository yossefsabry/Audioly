package com.audioly.shared.data.db

import com.audioly.shared.data.model.Track
import com.audioly.shared.data.model.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory iOS database stub.
 * TODO: Replace with SQLDelight or Room KMP for persistence.
 */
class IosDatabaseService : AudiolyDatabaseService {

    private val tracks = mutableMapOf<String, Track>()
    private val playlists = mutableMapOf<Long, Playlist>()
    private val playlistTracks = mutableMapOf<Long, MutableList<String>>() // playlistId → videoIds

    private val _tracksFlow = MutableStateFlow<List<Track>>(emptyList())
    private val _playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())

    private var nextPlaylistId = 1L

    override fun getAllTracks(): Flow<List<Track>> = _tracksFlow

    override suspend fun getTrackById(videoId: String): Track? = tracks[videoId]

    override suspend fun insertTrack(track: Track) {
        tracks[track.videoId] = track
        _tracksFlow.value = tracks.values.toList()
    }

    override suspend fun updateTrack(track: Track) {
        tracks[track.videoId] = track
        _tracksFlow.value = tracks.values.toList()
    }

    override suspend fun deleteTrack(videoId: String) {
        tracks.remove(videoId)
        _tracksFlow.value = tracks.values.toList()
    }

    override suspend fun getRecentTracks(limit: Int): List<Track> {
        return tracks.values.sortedByDescending { it.lastPlayedAt }.take(limit)
    }

    override suspend fun getMostPlayedTracks(limit: Int): List<Track> {
        return tracks.values.sortedByDescending { it.playCount }.take(limit)
    }

    override fun getAllPlaylists(): Flow<List<Playlist>> = _playlistsFlow

    override suspend fun getPlaylistById(id: Long): Playlist? = playlists[id]

    override suspend fun createPlaylist(name: String): Long {
        val id = nextPlaylistId++
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        playlists[id] = Playlist(id = id, name = name, createdAt = now, updatedAt = now)
        playlistTracks[id] = mutableListOf()
        _playlistsFlow.value = playlists.values.toList()
        return id
    }

    override suspend fun deletePlaylist(id: Long) {
        playlists.remove(id)
        playlistTracks.remove(id)
        _playlistsFlow.value = playlists.values.toList()
    }

    override suspend fun getTracksForPlaylist(playlistId: Long): List<Track> {
        val videoIds = playlistTracks[playlistId] ?: return emptyList()
        return videoIds.mapNotNull { tracks[it] }
    }

    override suspend fun addTrackToPlaylist(playlistId: Long, videoId: String) {
        val list = playlistTracks.getOrPut(playlistId) { mutableListOf() }
        if (videoId !in list) list.add(videoId)
    }

    override suspend fun removeTrackFromPlaylist(playlistId: Long, videoId: String) {
        playlistTracks[playlistId]?.remove(videoId)
    }
}
