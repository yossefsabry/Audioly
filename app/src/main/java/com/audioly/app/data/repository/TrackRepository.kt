package com.audioly.app.data.repository

import com.audioly.app.data.db.dao.TrackDao
import com.audioly.app.data.db.entities.TrackEntity
import com.audioly.app.data.model.Track
import com.audioly.app.extraction.SearchResult
import com.audioly.app.extraction.StreamInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrackRepository(private val dao: TrackDao) {

    // ─── Observe ──────────────────────────────────────────────────────────────

    fun observeHistory(limit: Int = 50): Flow<List<Track>> =
        dao.observeHistory(limit).map { it.map(TrackEntity::toDomain) }

    fun observeCached(): Flow<List<Track>> =
        dao.observeCached().map { it.map(TrackEntity::toDomain) }

    fun observeAll(): Flow<List<Track>> =
        dao.observeAll().map { it.map(TrackEntity::toDomain) }

    // ─── Reads ────────────────────────────────────────────────────────────────

    suspend fun getById(videoId: String): Track? =
        dao.getById(videoId)?.toDomain()

    // ─── Writes ───────────────────────────────────────────────────────────────

    /**
     * Save stream info from extractor. Will not overwrite [audioFilePath] or
     * play stats if the row already exists (handled by [recordPlay] / [setAudioFilePath]).
     */
    suspend fun upsertFromExtraction(info: StreamInfo) {
        dao.upsertPreservingUserData(
            TrackEntity(
                videoId = info.videoId,
                title = info.title,
                uploader = info.uploader,
                thumbnailUrl = info.thumbnailUrl,
                durationSeconds = info.durationSeconds,
                audioFilePath = null,
                lastPlayedAt = 0L,
                playCount = 0,
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Ensure a track row exists from search result metadata (no extraction needed).
     * Preserves existing user data like cache path and play stats.
     */
    suspend fun upsertFromSearchResult(result: SearchResult) {
        dao.upsertPreservingUserData(
            TrackEntity(
                videoId = result.videoId,
                title = result.title,
                uploader = result.uploader,
                thumbnailUrl = result.thumbnailUrl,
                durationSeconds = result.durationSeconds,
                audioFilePath = null,
                lastPlayedAt = 0L,
                playCount = 0,
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun recordPlay(videoId: String) =
        dao.recordPlay(videoId)

    suspend fun setAudioFilePath(videoId: String, path: String?) =
        dao.setAudioFilePath(videoId, path)

    suspend fun setAudioStreamUrl(videoId: String, url: String?) =
        dao.setAudioStreamUrl(videoId, url)

    suspend fun saveLastPosition(videoId: String, positionMs: Long) =
        dao.setLastPosition(videoId, positionMs)

    suspend fun getLastPosition(videoId: String): Long =
        dao.getLastPosition(videoId) ?: 0L

    suspend fun delete(videoId: String) =
        dao.delete(videoId)
}

// ─── Mapper ───────────────────────────────────────────────────────────────────

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
    lastPositionMs = lastPositionMs,
)
