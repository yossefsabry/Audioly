package com.audioly.app

import android.app.Application
import com.audioly.app.data.cache.AudioCacheManager
import com.audioly.app.data.db.AudiolyDatabase
import com.audioly.app.data.preferences.UserPreferencesRepository
import com.audioly.app.data.repository.CacheRepository
import com.audioly.app.data.repository.PlaylistRepository
import com.audioly.app.data.repository.TrackRepository
import com.audioly.app.extraction.OkHttpDownloader
import com.audioly.app.extraction.YouTubeExtractor
import com.audioly.app.data.cache.SubtitleCacheManager
import com.audioly.app.player.PlayerRepository
import com.audioly.app.util.AppLogger
import java.io.File
import org.schabi.newpipe.extractor.NewPipe

class AudiolyApp : Application() {

    // ─── Singletons ───────────────────────────────────────────────────────────

    lateinit var database: AudiolyDatabase
        private set

    lateinit var audioCacheManager: AudioCacheManager
        private set

    lateinit var subtitleCacheManager: SubtitleCacheManager
        private set

    lateinit var trackRepository: TrackRepository
        private set

    lateinit var playlistRepository: PlaylistRepository
        private set

    lateinit var cacheRepository: CacheRepository
        private set

    lateinit var preferencesRepository: UserPreferencesRepository
        private set

    lateinit var youTubeExtractor: YouTubeExtractor
        private set

    val playerRepository = PlayerRepository()

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        // Init logger first so all subsequent logs are captured
        AppLogger.init(this)

        // Global crash handler — log and let Android show the crash dialog
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.fatal(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            NewPipe.init(OkHttpDownloader.instance)
            AppLogger.i(TAG, "NewPipe extractor initialized")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to init NewPipe", e)
        }

        database = AudiolyDatabase.getInstance(this)

        audioCacheManager = AudioCacheManager(this)

        subtitleCacheManager = SubtitleCacheManager(
            subtitleRootDir = File(cacheDir, "subtitles"),
            dao = database.subtitleCacheDao(),
        )

        trackRepository = TrackRepository(database.trackDao())
        playlistRepository = PlaylistRepository(database.playlistDao())

        cacheRepository = CacheRepository(
            audioCacheManager = audioCacheManager,
            subtitleCacheManager = subtitleCacheManager,
            trackRepository = trackRepository,
        )

        preferencesRepository = UserPreferencesRepository(this)

        youTubeExtractor = YouTubeExtractor()
        AppLogger.i(TAG, "Application initialized")
    }

    override fun onTerminate() {
        try {
            audioCacheManager.release()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error releasing cache", e)
        }
        super.onTerminate()
    }

    companion object {
        private const val TAG = "AudiolyApp"
    }
}
