package com.audioly.app.data.repository

import com.audioly.app.data.db.dao.TrackDao
import com.audioly.app.data.db.entities.TrackEntity
import com.audioly.app.data.model.Track
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
        val existing = dao.getById(info.videoId)
        dao.upsert(
            TrackEntity(
                videoId = info.videoId,
                title = info.title,
                uploader = info.uploader,
                thumbnailUrl = info.thumbnailUrl,
                durationSeconds = info.durationSeconds,
                audioFilePath = existing?.audioFilePath,
                lastPlayedAt = existing?.lastPlayedAt ?: 0L,
                playCount = existing?.playCount ?: 0,
                addedAt = existing?.addedAt ?: System.currentTimeMillis(),
            ),
        )
    }

    suspend fun recordPlay(videoId: String) =
        dao.recordPlay(videoId)

    suspend fun setAudioFilePath(videoId: String, path: String?) =
        dao.setAudioFilePath(videoId, path)

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
    lastPlayedAt = lastPlayedAt,
    playCount = playCount,
    addedAt = addedAt,
)
