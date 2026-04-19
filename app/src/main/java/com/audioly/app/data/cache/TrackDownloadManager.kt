package com.audioly.app.data.cache

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import com.audioly.app.data.preferences.UserPreferencesRepository
import com.audioly.shared.extraction.SubtitleTrack
import com.audioly.app.network.AppHttpClient
import com.audioly.app.util.AppLogger
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Call

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data object Completed : DownloadState
    data class Failed(val message: String) : DownloadState
}

@OptIn(UnstableApi::class)
class TrackDownloadManager(
    private val audioCacheManager: AudioCacheManager,
    private val subtitleCacheManager: SubtitleCacheManager,
    private val preferencesRepository: UserPreferencesRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    private val activeDownloads = mutableMapOf<String, ActiveDownload>()

    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    fun stateFlow(videoId: String): Flow<DownloadState> =
        combine(_downloads, audioCacheManager.cacheVersion) { downloadMap, _ ->
            when (val state = downloadMap[videoId]) {
                is DownloadState.Downloading,
                is DownloadState.Failed,
                -> state

                else -> {
                    if (audioCacheManager.getStatus(videoId).isFullyCached) {
                        DownloadState.Completed
                    } else {
                        DownloadState.Idle
                    }
                }
            }
        }.distinctUntilChanged()

    fun startDownload(
        videoId: String,
        audioUrl: String,
        subtitleTracks: List<SubtitleTrack> = emptyList(),
    ) {
        val activeDownload = ActiveDownload()
        synchronized(activeDownloads) {
            if (activeDownloads.containsKey(videoId)) return
            activeDownloads[videoId] = activeDownload
        }

        putState(videoId, DownloadState.Downloading(0f))

        activeDownload.job = scope.launch {
            try {
                downloadAudio(videoId, audioUrl, activeDownload)
                putState(videoId, DownloadState.Downloading(1f))
                downloadPreferredSubtitle(videoId, subtitleTracks, activeDownload)
                clearState(videoId)
                AppLogger.i(TAG, "Download completed for $videoId")
            } catch (e: CancellationException) {
                clearState(videoId)
                AppLogger.d(TAG, "Download cancelled for $videoId")
                throw e
            } catch (e: Exception) {
                putState(videoId, DownloadState.Failed(e.message ?: "Download failed"))
                AppLogger.e(TAG, "Download failed for $videoId", e)
            } finally {
                activeDownload.cacheWriter = null
                activeDownload.subtitleCall = null
                synchronized(activeDownloads) { activeDownloads.remove(videoId) }
                audioCacheManager.noteCacheProgress(videoId)
            }
        }
    }

    fun cancelDownload(videoId: String) {
        val activeDownload = synchronized(activeDownloads) { activeDownloads[videoId] } ?: run {
            clearState(videoId)
            return
        }
        activeDownload.cancelRequested = true
        activeDownload.cacheWriter?.cancel()
        activeDownload.subtitleCall?.cancel()
        activeDownload.job?.cancel()
        clearState(videoId)
    }

    private fun downloadAudio(
        videoId: String,
        audioUrl: String,
        activeDownload: ActiveDownload,
    ) {
        if (audioCacheManager.getStatus(videoId).isFullyCached) return

        val dataSource = audioCacheManager.dataSourceFactory.createDataSource()
        val dataSpec = DataSpec.Builder()
            .setUri(Uri.parse(audioUrl))
            .setKey(videoId)
            .build()
        val cacheWriter = CacheWriter(
            dataSource,
            dataSpec,
            /* temporaryBuffer= */ null,
        ) { requestLength, bytesCached, _ ->
            val progress = if (requestLength > 0L) {
                (bytesCached.toFloat() / requestLength.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            putState(videoId, DownloadState.Downloading(progress))
        }

        activeDownload.cacheWriter = cacheWriter
        try {
            cacheWriter.cache()
        } catch (e: IOException) {
            if (activeDownload.cancelRequested) {
                throw CancellationException("Audio download cancelled").apply { initCause(e) }
            }
            throw e
        } finally {
            activeDownload.cacheWriter = null
        }
    }

    private suspend fun downloadPreferredSubtitle(
        videoId: String,
        tracks: List<SubtitleTrack>,
        activeDownload: ActiveDownload,
    ) {
        if (tracks.isEmpty()) return

        val preferred = try {
            preferencesRepository.preferences.first().preferredSubtitleLanguage
        } catch (_: Exception) {
            ""
        }

        val track = if (preferred.isNotEmpty()) {
            tracks.firstOrNull { it.languageCode == preferred } ?: tracks.first()
        } else {
            tracks.first()
        }

        val cached = try {
            subtitleCacheManager.load(videoId, track.languageCode)
        } catch (_: Exception) {
            null
        }
        if (cached != null || track.url.isBlank()) return

        val content = downloadSubtitleContent(track.url, activeDownload) ?: return

        try {
            subtitleCacheManager.save(
                videoId = videoId,
                languageCode = track.languageCode,
                languageName = track.languageName,
                format = track.format,
                isAutoGenerated = track.isAutoGenerated,
                content = content,
            )
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to cache subtitle during download: ${e.message}")
        }
    }

    private fun downloadSubtitleContent(url: String, activeDownload: ActiveDownload): String? {
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", SUBTITLE_USER_AGENT)
            .build()
        val call = AppHttpClient.subtitleClient.newCall(request)
        activeDownload.subtitleCall = call
        return try {
            call.execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        } catch (e: IOException) {
            if (activeDownload.cancelRequested || call.isCanceled()) {
                throw CancellationException("Subtitle download cancelled").apply { initCause(e) }
            }
            AppLogger.w(TAG, "Subtitle download failed: ${e.message}")
            null
        } finally {
            activeDownload.subtitleCall = null
        }
    }

    private fun putState(videoId: String, state: DownloadState) {
        _downloads.update { it + (videoId to state) }
    }

    private fun clearState(videoId: String) {
        _downloads.update { it - videoId }
    }

    private class ActiveDownload {
        @Volatile
        var cancelRequested: Boolean = false

        @Volatile
        var job: Job? = null

        @Volatile
        var cacheWriter: CacheWriter? = null

        @Volatile
        var subtitleCall: Call? = null
    }

    companion object {
        private const val TAG = "TrackDownloadManager"
        private const val SUBTITLE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
