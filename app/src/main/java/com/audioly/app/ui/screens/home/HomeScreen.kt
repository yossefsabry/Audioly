package com.audioly.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
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
                snackbarHostState = snackbarHostState,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audioly") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Play YouTube as audio",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                return@Column
            }

            HorizontalDivider()
            Text("Recent", style = MaterialTheme.typography.titleMedium)

            if (history.isEmpty()) {
                Text(
                    "No recent videos yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    items(history, key = { it.videoId }) { track ->
                        TrackItem(
                            track = track,
                            onClick = { onNavigateToPlayer(track.videoId) },
                        )
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
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
) {
    val videoId = UrlValidator.extractVideoId(url)
    if (videoId == null) {
        setError(if (url.isBlank()) "Enter a YouTube URL" else "Not a valid YouTube URL")
        return
    }
    setError(null)
    setExtracting(true)
    try {
        AppLogger.i(TAG, "Extracting: $url")
        when (val result = app.youTubeExtractor.extract(url)) {
            is ExtractionResult.Success -> {
                try {
                    app.trackRepository.upsertFromExtraction(result.streamInfo)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to save track to DB", e)
                    // Non-fatal — continue to play even if DB write fails
                }
                // Load audio into player
                app.playerRepository.load(
                    audioUrl = result.streamInfo.audioStreamUrl,
                    videoId = result.streamInfo.videoId,
                    title = result.streamInfo.title,
                    uploader = result.streamInfo.uploader,
                    thumbnailUrl = result.streamInfo.thumbnailUrl,
                    durationMs = result.streamInfo.durationSeconds * 1000L,
                )
                onNavigateToPlayer(result.streamInfo.videoId)
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
        // Catch-all to prevent crash
        AppLogger.e(TAG, "Unexpected error during extraction", e)
        snackbarHostState.showSnackbar("Something went wrong: ${e.message ?: "unknown error"}")
    } finally {
        setExtracting(false)
    }
}
