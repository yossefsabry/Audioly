package com.audioly.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.audioly.app.data.db.entities.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    /** Insert or replace track (upsert). Returns row id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity): Long

    @Query("SELECT * FROM tracks WHERE videoId = :videoId")
    suspend fun getById(videoId: String): TrackEntity?

    /** All tracks that have been played at least once, newest first. */
    @Query(
        "SELECT * FROM tracks WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit",
    )
    fun observeHistory(limit: Int = 50): Flow<List<TrackEntity>>

    /** Tracks with cached audio on disk. */
    @Query("SELECT * FROM tracks WHERE audioFilePath IS NOT NULL ORDER BY addedAt DESC")
    fun observeCached(): Flow<List<TrackEntity>>

    /** Bump play count and update timestamp. */
    @Query(
        "UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :nowMs WHERE videoId = :videoId",
    )
    suspend fun recordPlay(videoId: String, nowMs: Long = System.currentTimeMillis())

    @Query("UPDATE tracks SET audioFilePath = :path WHERE videoId = :videoId")
    suspend fun setAudioFilePath(videoId: String, path: String?)

    @Query("UPDATE tracks SET audioStreamUrl = :url WHERE videoId = :videoId")
    suspend fun setAudioStreamUrl(videoId: String, url: String?)

    @Query("DELETE FROM tracks WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("SELECT * FROM tracks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    /**
     * Atomically read existing row + upsert merged data.
     * Prevents race where concurrent calls for the same videoId
     * overwrite each other's audioFilePath / play stats.
     */
    @Transaction
    suspend fun upsertPreservingUserData(track: TrackEntity) {
        val existing = getById(track.videoId)
        upsert(
            track.copy(
                audioFilePath = existing?.audioFilePath ?: track.audioFilePath,
                audioStreamUrl = track.audioStreamUrl ?: existing?.audioStreamUrl,
                lastPlayedAt = existing?.lastPlayedAt ?: track.lastPlayedAt,
                playCount = existing?.playCount ?: track.playCount,
                addedAt = existing?.addedAt ?: track.addedAt,
            )
        )
    }
}
