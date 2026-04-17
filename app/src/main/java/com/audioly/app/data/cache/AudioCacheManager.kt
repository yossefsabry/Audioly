package com.audioly.app.data.cache

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Singleton wrapper around Media3 [SimpleCache] for audio files.
 *
 * Must be created once (in [AudiolyApp]) and closed when the process exits.
 * Do NOT create multiple instances — SimpleCache enforces single-owner.
 */
class AudioCacheManager(context: Context, maxBytes: Long = DEFAULT_MAX_BYTES) {

    val cache: SimpleCache

    private val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).also { it.mkdirs() }
    private val databaseProvider: StandaloneDatabaseProvider

    init {
        databaseProvider = StandaloneDatabaseProvider(context)
        val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
        cache = SimpleCache(cacheDir, evictor, databaseProvider)
    }

    /** Total bytes currently stored. */
    val usedBytes: Long get() = cache.cacheSpace

    /** Remove all cached audio. */
    fun clearAll() {
        for (key in cache.keys.toList()) {
            cache.removeResource(key)
        }
    }

    /** Remove audio for a specific video ID. */
    fun deleteForVideo(videoId: String) {
        cache.removeResource(videoId)
    }

    fun release() {
        cache.release()
        databaseProvider.close()
    }

    companion object {
        private const val CACHE_DIR_NAME = "audio_cache"
        const val DEFAULT_MAX_BYTES = 512L * 1024 * 1024 // 512 MB
    }
}
