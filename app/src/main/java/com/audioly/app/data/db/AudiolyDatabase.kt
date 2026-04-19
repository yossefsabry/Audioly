package com.audioly.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
    exportSchema = false,
)
abstract class AudiolyDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun subtitleCacheDao(): SubtitleCacheDao

    companion object {
        @Volatile private var INSTANCE: AudiolyDatabase? = null

        /** v1 → v2: add audioStreamUrl column to tracks. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN audioStreamUrl TEXT DEFAULT NULL")
            }
        }

        /** v2 → v3: add lastPositionMs column to tracks for resume playback. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN lastPositionMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AudiolyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AudiolyDatabase::class.java,
                    "audioly.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}
