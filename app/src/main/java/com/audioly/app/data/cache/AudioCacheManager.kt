package com.audioly.app.data.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.audioly.app.util.AppLogger
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioCacheStatus(
    val videoId: String? = null,
    val cachedBytes: Long = 0L,
    val contentLength: Long = C.LENGTH_UNSET.toLong(),
    val hasCache: Boolean = false,
    val isFullyCached: Boolean = false,
)

/**
 * Singleton wrapper around Media3 [SimpleCache] for audio files.
 *
 * Must be created once (in [AudiolyApp]) and closed when the process exits.
 * Do NOT create multiple instances — SimpleCache enforces single-owner.
 */
@OptIn(UnstableApi::class)
class AudioCacheManager(context: Context, maxBytes: Long = DEFAULT_MAX_BYTES) {

    val cache: SimpleCache
    val dataSourceFactory: CacheDataSource.Factory

    private val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).also { it.mkdirs() }
    private val databaseProvider: StandaloneDatabaseProvider
    private val lastKnownStatus = java.util.concurrent.ConcurrentHashMap<String, AudioCacheStatus>()
    private val _cacheVersion = MutableStateFlow(0L)

    val cacheVersion: StateFlow<Long> = _cacheVersion.asStateFlow()

    init {
        databaseProvider = StandaloneDatabaseProvider(context)
        val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
        cache = SimpleCache(cacheDir, evictor, databaseProvider)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        dataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Total bytes currently stored. */
    val usedBytes: Long get() = cache.cacheSpace

    fun getStatus(videoId: String?): AudioCacheStatus {
        if (videoId.isNullOrBlank()) return AudioCacheStatus()

        val spans = cache.getCachedSpans(videoId)
        if (spans.isEmpty()) {
            return AudioCacheStatus(videoId = videoId)
        }

        val contentLength = cache.getContentMetadata(videoId)
            .get(ContentMetadata.KEY_CONTENT_LENGTH, C.LENGTH_UNSET.toLong())
        val cachedBytes = cache.getCachedBytes(videoId, 0, Long.MAX_VALUE).coerceAtLeast(0L)
        val isFullyCached = contentLength > 0L && cache.isCached(videoId, 0, contentLength)

        return AudioCacheStatus(
            videoId = videoId,
            cachedBytes = cachedBytes,
            contentLength = contentLength,
            hasCache = cachedBytes > 0L,
            isFullyCached = isFullyCached,
        )
    }

    @Synchronized
    fun noteCacheProgress(videoId: String?): AudioCacheStatus {
        val status = getStatus(videoId)
        val key = status.videoId ?: return status
        val previous = lastKnownStatus[key]
        if (previous != status) {
            lastKnownStatus[key] = status
            _cacheVersion.value += 1L
        }
        return status
    }

    /** True if any cached audio data exists for this video. */
    fun isCached(videoId: String): Boolean =
        getStatus(videoId).hasCache

    /**
     * True if the entire audio file for [videoId] is cached.
     * Returns false if content length is unknown (never fully downloaded).
     */
    fun isFullyCached(videoId: String): Boolean {
        val status = getStatus(videoId)
        AppLogger.d(
            TAG,
            "isFullyCached($videoId): contentLength=${status.contentLength}, cachedBytes=${status.cachedBytes}, hasCache=${status.hasCache}",
        )
        return status.isFullyCached
    }

    /** Total cached bytes for a given video. */
    fun getCachedBytes(videoId: String): Long =
        getStatus(videoId).cachedBytes

    /** Remove all cached audio. */
    @Synchronized
    fun clearAll() {
        for (key in cache.keys.toList()) {
            cache.removeResource(key)
            lastKnownStatus.remove(key)
        }
        _cacheVersion.value += 1L
    }

    /** Remove audio for a specific video ID. */
    @Synchronized
    fun deleteForVideo(videoId: String) {
        cache.removeResource(videoId)
        lastKnownStatus.remove(videoId)
        _cacheVersion.value += 1L
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
