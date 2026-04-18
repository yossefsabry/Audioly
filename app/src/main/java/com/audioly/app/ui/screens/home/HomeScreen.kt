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
import androidx.compose.runtime.produceState
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
import com.audioly.app.ui.playback.launchPlaybackFromUrl
import com.audioly.app.ui.playback.launchPlaybackFromVideoId
import com.audioly.app.ui.components.TrackItem
import com.audioly.app.ui.components.UrlInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: AudiolyApp,
    onNavigateToPlayer: (String) -> Unit = {},
    /** Pre-populated from share intent; consumed once. */
    initialUrl: String? = null,
) {
    var urlInput by remember(initialUrl) { mutableStateOf(initialUrl ?: "") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var isExtracting by remember { mutableStateOf(false) }

    val history by app.trackRepository.observeHistory(limit = 10).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Pre-compute cache statuses off main thread to avoid jank
    val cacheStatusMap by produceState<Map<String, Boolean>>(emptyMap(), history) {
        value = withContext(Dispatchers.IO) {
            history.associate { it.videoId to app.cacheRepository.getAudioStatus(it.videoId).hasCache }
        }
    }

    // Auto-trigger extraction if launched from share intent
    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            launchPlaybackFromUrl(
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
                        launchPlaybackFromUrl(
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
                            TrackItem(
                                track = track,
                                isCached = cacheStatusMap[track.videoId] ?: false,
                                onClick = {
                                    scope.launch {
                                        launchPlaybackFromVideoId(
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
