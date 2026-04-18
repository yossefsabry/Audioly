package com.audioly.app.data.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.audioly.app.util.AppLogger
import java.io.File

/**
 * Singleton wrapper around Media3 [SimpleCache] for audio files.
 *
 * Must be created once (in [AudiolyApp]) and closed when the process exits.
 * Do NOT create multiple instances — SimpleCache enforces single-owner.
 */
@OptIn(UnstableApi::class)
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

    /** True if any cached audio data exists for this video. */
    fun isCached(videoId: String): Boolean =
        cache.getCachedSpans(videoId).isNotEmpty()

    /**
     * True if the entire audio file for [videoId] is cached.
     * Returns false if content length is unknown (never fully downloaded).
     */
    fun isFullyCached(videoId: String): Boolean {
        val spans = cache.getCachedSpans(videoId)
        if (spans.isEmpty()) {
            AppLogger.d(TAG, "isFullyCached($videoId): no spans")
            return false
        }
        val contentLength = cache.getContentMetadata(videoId)
            .get(ContentMetadata.KEY_CONTENT_LENGTH, C.LENGTH_UNSET.toLong())
        val cachedBytes = cache.getCachedBytes(videoId, 0, Long.MAX_VALUE)
        AppLogger.d(TAG, "isFullyCached($videoId): spans=${spans.size}, contentLength=$contentLength, cachedBytes=$cachedBytes")
        if (contentLength <= 0) return false
        return cache.isCached(videoId, 0, contentLength)
    }

    /** Total cached bytes for a given video. */
    fun getCachedBytes(videoId: String): Long =
        cache.getCachedBytes(videoId, 0, Long.MAX_VALUE)

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
        private const val TAG = "AudioCacheManager"
        private const val CACHE_DIR_NAME = "audio_cache"
        const val DEFAULT_MAX_BYTES = 512L * 1024 * 1024 // 512 MB
    }
}
