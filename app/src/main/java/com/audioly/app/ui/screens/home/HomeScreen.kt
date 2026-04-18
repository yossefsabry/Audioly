package com.audioly.app.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.audioly.app.R
import com.audioly.app.AudiolyApp
import com.audioly.app.extraction.ExtractionResult
import com.audioly.app.ui.components.TrackItem
import com.audioly.app.ui.components.UrlInput
import com.audioly.app.util.AppLogger
import com.audioly.app.util.UrlValidator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: AudiolyApp,
    onNavigateToPlayer: (String) -> Unit = {},
    /** Pre-populated from share intent; consumed once. */
    initialUrl: String? = null,
) {
    var urlInput by remember { mutableStateOf(initialUrl ?: "") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var isExtracting by remember { mutableStateOf(false) }

    val history by app.trackRepository.observeHistory(limit = 10).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-trigger extraction if launched from share intent
    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            handleGo(
                url = initialUrl,
                app = app,
                onNavigateToPlayer = onNavigateToPlayer,
                setError = { urlError = it },
                setExtracting = { isExtracting = it },
                isCurrentlyIdle = !isExtracting,
                snackbarHostState = snackbarHostState,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_audioly_logo),
                                contentDescription = "Audioly Logo",
                                modifier = Modifier.size(24.dp),
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Audioly")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp), // Increased spacing for modern airy feel
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Play YouTube",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "As Audio",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            UrlInput(
                value = urlInput,
                onValueChange = { urlInput = it; urlError = null },
                onGo = { url ->
                    scope.launch {
                        handleGo(
                            url = url,
                            app = app,
                            onNavigateToPlayer = onNavigateToPlayer,
                            setError = { urlError = it },
                            setExtracting = { isExtracting = it },
                            isCurrentlyIdle = !isExtracting,
                            snackbarHostState = snackbarHostState,
                        )
                    }
                },
                error = urlError,
                enabled = !isExtracting,
            )

            if (isExtracting) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            "Extracting audio...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                Text(
                    "Recent",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (history.isEmpty()) {
                    Text(
                        "No recent videos yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn {
                        items(history, key = { it.videoId }) { track ->
                            val cacheStatus = app.cacheRepository.getAudioStatus(track.videoId)
                            TrackItem(
                                track = track,
                                isCached = cacheStatus.hasCache,
                                onClick = {
                                    scope.launch {
                                        handleHistoryTap(
                                            videoId = track.videoId,
                                            app = app,
                                            onNavigateToPlayer = onNavigateToPlayer,
                                            setExtracting = { isExtracting = it },
                                            isCurrentlyIdle = !isExtracting,
                                            snackbarHostState = snackbarHostState,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Extraction flow ──────────────────────────────────────────────────────────

private const val TAG = "HomeScreen"

private suspend fun handleGo(
    url: String,
    app: AudiolyApp,
    onNavigateToPlayer: (String) -> Unit,
    setError: (String?) -> Unit,
    setExtracting: (Boolean) -> Unit,
    isCurrentlyIdle: Boolean,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
) {
    val videoId = UrlValidator.extractVideoId(url)
    if (videoId == null) {
        setError(if (url.isBlank()) "Enter a YouTube URL" else "Not a valid YouTube URL")
        return
    }
    setError(null)
    // Guard against concurrent extractions
    if (!isCurrentlyIdle) {
        AppLogger.w(TAG, "Extraction already in progress, ignoring duplicate request")
        return
    }

    // ── Try cache-backed instant playback ─────────────────────────────────
    if (tryPlayFromCache(videoId, app, onNavigateToPlayer)) return

    // ── Fallback: full extraction ─────────────────────────────────────────
    setExtracting(true)
    try {
        AppLogger.i(TAG, "Extracting: $url")
        when (val result = app.youTubeExtractor.extract(url)) {
            is ExtractionResult.Success -> {
                val info = result.streamInfo
                try {
                    app.trackRepository.upsertFromExtraction(info)
                    // Persist audio URL so cache-backed replay works next time
                    app.trackRepository.setAudioStreamUrl(info.videoId, info.audioStreamUrl)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to save track to DB", e)
                }
                // Clear old subtitle data and set new tracks
                app.playerRepository.clearSubtitles()
                app.playerRepository.setSubtitleTracks(info.subtitleTracks)
                // Load audio into player
                app.playerRepository.load(
                    audioUrl = info.audioStreamUrl,
                    videoId = info.videoId,
                    title = info.title,
                    uploader = info.uploader,
                    thumbnailUrl = info.thumbnailUrl,
                    durationMs = info.durationSeconds * 1000L,
                )
                onNavigateToPlayer(info.videoId)
            }
            is ExtractionResult.Failure.InvalidUrl ->
                setError("Not a valid YouTube URL")
            is ExtractionResult.Failure.VideoUnavailable ->
                snackbarHostState.showSnackbar("Video unavailable")
            is ExtractionResult.Failure.AgeRestricted ->
                snackbarHostState.showSnackbar("Age-restricted video — cannot play")
            is ExtractionResult.Failure.NetworkError ->
                snackbarHostState.showSnackbar("Network error. Check your connection.")
            is ExtractionResult.Failure.ExtractionFailed ->
                snackbarHostState.showSnackbar("Extraction failed: ${result.cause.message ?: "unknown error"}")
        }
    } catch (e: Exception) {
        AppLogger.e(TAG, "Unexpected error during extraction", e)
        snackbarHostState.showSnackbar("Something went wrong: ${e.message ?: "unknown error"}")
    } finally {
        setExtracting(false)
    }
}

/**
 * Attempt to play a video entirely from cache.
 * Returns true if successful (caller should skip extraction).
 */
private suspend fun tryPlayFromCache(
    videoId: String,
    app: AudiolyApp,
    onNavigateToPlayer: (String) -> Unit,
): Boolean {
    try {
        val cacheStatus = app.cacheRepository.getAudioStatus(videoId)
        if (!cacheStatus.isFullyCached) {
            AppLogger.d(
                TAG,
                "tryPlayFromCache($videoId): fullyCached=false, hasCache=${cacheStatus.hasCache}, cachedBytes=${cacheStatus.cachedBytes} - falling back to extraction",
            )
            return false
        }
        val track = app.trackRepository.getById(videoId)
        if (track == null) {
            AppLogger.d(TAG, "tryPlayFromCache($videoId): fully cached but no DB record")
            return false
        }
        val audioUrl = track.audioStreamUrl
        if (audioUrl.isNullOrBlank()) {
            AppLogger.d(TAG, "tryPlayFromCache($videoId): fully cached but no stored audioUrl")
            return false
        }

        AppLogger.i(TAG, "Playing from cache: $videoId (${cacheStatus.cachedBytes} bytes)")
        app.playerRepository.clearSubtitles()
        app.playerRepository.load(
            audioUrl = audioUrl,
            videoId = videoId,
            title = track.title,
            uploader = track.uploader,
            thumbnailUrl = track.thumbnailUrl,
            durationMs = track.durationSeconds * 1000L,
        )
        onNavigateToPlayer(videoId)
        return true
    } catch (e: Exception) {
        AppLogger.w(TAG, "Cache playback failed, falling back to extraction: ${e.message}")
        return false
    }
}

/**
 * Handle tap on a history item. Tries cache first, falls back to extraction.
 */
private suspend fun handleHistoryTap(
    videoId: String,
    app: AudiolyApp,
    onNavigateToPlayer: (String) -> Unit,
    setExtracting: (Boolean) -> Unit,
    isCurrentlyIdle: Boolean,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
) {
    if (!isCurrentlyIdle) return

    // Try instant cache playback
    if (tryPlayFromCache(videoId, app, onNavigateToPlayer)) return

    // Fallback: re-extract using a constructed YouTube URL
    handleGo(
        url = "https://www.youtube.com/watch?v=$videoId",
        app = app,
        onNavigateToPlayer = onNavigateToPlayer,
        setError = { /* History tap has no URL field to show errors */ },
        setExtracting = setExtracting,
        isCurrentlyIdle = isCurrentlyIdle,
        snackbarHostState = snackbarHostState,
    )
}
