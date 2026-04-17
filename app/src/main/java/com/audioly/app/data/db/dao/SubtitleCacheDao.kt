package com.audioly.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.audioly.app.data.db.entities.SubtitleCacheEntity

@Dao
interface SubtitleCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SubtitleCacheEntity)

    @Query(
        "SELECT * FROM subtitle_cache WHERE videoId = :videoId AND languageCode = :languageCode LIMIT 1",
    )
    suspend fun get(videoId: String, languageCode: String): SubtitleCacheEntity?

    @Query("SELECT * FROM subtitle_cache WHERE videoId = :videoId")
    suspend fun getAllForVideo(videoId: String): List<SubtitleCacheEntity>

    @Query("DELETE FROM subtitle_cache WHERE videoId = :videoId AND languageCode = :languageCode")
    suspend fun delete(videoId: String, languageCode: String)

    @Query("DELETE FROM subtitle_cache WHERE videoId = :videoId")
    suspend fun deleteAllForVideo(videoId: String)
}
