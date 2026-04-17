package com.audioly.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.audioly.app.data.db.dao.PlaylistDao
import com.audioly.app.data.db.dao.SubtitleCacheDao
import com.audioly.app.data.db.dao.TrackDao
import com.audioly.app.data.db.entities.PlaylistEntity
import com.audioly.app.data.db.entities.PlaylistTrackEntity
import com.audioly.app.data.db.entities.SubtitleCacheEntity
import com.audioly.app.data.db.entities.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        SubtitleCacheEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AudiolyDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun subtitleCacheDao(): SubtitleCacheDao

    companion object {
        @Volatile private var INSTANCE: AudiolyDatabase? = null

        fun getInstance(context: Context): AudiolyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AudiolyDatabase::class.java,
                    "audioly.db",
                ).build().also { INSTANCE = it }
            }
    }
}
