package com.audioly.app

import android.app.Application
import com.audioly.app.data.cache.AudioCacheManager
import com.audioly.app.data.cache.TrackDownloadManager
import com.audioly.app.data.db.AudiolyDatabase
import com.audioly.app.data.preferences.UserPreferencesRepository
import com.audioly.app.data.repository.CacheRepository
import com.audioly.app.data.repository.PlaylistRepository
import com.audioly.app.data.repository.TrackRepository
import com.audioly.app.extraction.OkHttpDownloader
import com.audioly.app.extraction.YouTubeExtractor
import com.audioly.app.extraction.YouTubeSearchService
import com.audioly.app.data.cache.SubtitleCacheManager
import com.audioly.app.player.PlayerRepository
import com.audioly.app.util.AppLogger
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.NewPipe

class AudiolyApp : Application() {

    // ─── Singletons ───────────────────────────────────────────────────────────

    lateinit var database: AudiolyDatabase
        private set

    lateinit var audioCacheManager: AudioCacheManager
        private set

    lateinit var subtitleCacheManager: SubtitleCacheManager
        private set

    lateinit var trackDownloadManager: TrackDownloadManager
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

    lateinit var youTubeSearchService: YouTubeSearchService
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

        // Init preferences early so cache size pref is available before AudioCacheManager
        preferencesRepository = UserPreferencesRepository(this)

        // Read cache size synchronously — SimpleCache needs the value at construction and
        // can't be resized afterwards. DataStore dispatches file I/O on its own IO thread;
        // this runBlocking only parks the main thread briefly until the first emission.
        // Acceptable here because no UI is drawn yet (Application.onCreate).
        val maxCacheBytes = try {
            runBlocking { preferencesRepository.preferences.first().maxCacheBytes }
        } catch (_: Exception) {
            AudioCacheManager.DEFAULT_MAX_BYTES
        }

        audioCacheManager = try {
            AudioCacheManager(this, maxCacheBytes)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create audio cache, retrying after cleanup", e)
            // SimpleCache can fail if lock file is stale or directory is corrupted
            try {
                File(cacheDir, "audio_cache").deleteRecursively()
                AudioCacheManager(this, maxCacheBytes)
            } catch (e2: Exception) {
                AppLogger.fatal(TAG, "Audio cache creation failed even after cleanup", e2)
                throw e2 // Unrecoverable — app cannot function without cache
            }
        }

        subtitleCacheManager = SubtitleCacheManager(
            subtitleRootDir = File(cacheDir, "subtitles"),
            dao = database.subtitleCacheDao(),
        )

        trackDownloadManager = TrackDownloadManager(
            audioCacheManager = audioCacheManager,
            subtitleCacheManager = subtitleCacheManager,
            preferencesRepository = preferencesRepository,
        )

        trackRepository = TrackRepository(database.trackDao())
        playlistRepository = PlaylistRepository(database.playlistDao())

        cacheRepository = CacheRepository(
            audioCacheManager = audioCacheManager,
            subtitleCacheManager = subtitleCacheManager,
            trackRepository = trackRepository,
        )

        youTubeExtractor = YouTubeExtractor()
        youTubeSearchService = YouTubeSearchService()

        AppLogger.i(TAG, "Application initialized")
    }

    override fun onTerminate() {
        // Note: onTerminate() never runs on real devices, kept only for emulator completeness
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
