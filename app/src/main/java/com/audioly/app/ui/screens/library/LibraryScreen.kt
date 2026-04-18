package com.audioly.app.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.audioly.app.AudiolyApp
import com.audioly.app.data.model.Track
import com.audioly.app.ui.playback.launchPlaybackFromVideoId
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    app: AudiolyApp,
    onNavigateToPlayer: (String) -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("History", "Playlists", "Cached")
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isLaunching by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            val onTrackClick: (Track) -> Unit = { track ->
                scope.launch {
                    isLaunching += 1
                    try {
                        launchPlaybackFromVideoId(
                            videoId = track.videoId,
                            app = app,
                            onNavigateToPlayer = onNavigateToPlayer,
                            setExtracting = { },
                            isCurrentlyIdle = isLaunching == 1,
                            snackbarHostState = snackbarHostState,
                        )
                    } finally {
                        isLaunching = (isLaunching - 1).coerceAtLeast(0)
                    }
                }
            }

            when (selectedTab) {
                0 -> HistoryTab(
                    trackRepository = app.trackRepository,
                    cacheRepository = app.cacheRepository,
                    onTrackClick = onTrackClick,
                    modifier = Modifier.fillMaxSize(),
                )
                1 -> PlaylistsTab(
                    playlistRepository = app.playlistRepository,
                    onPlaylistClick = { /* TODO: navigate to playlist detail */ },
                    modifier = Modifier.fillMaxSize(),
                )
                2 -> CachedTab(
                    trackRepository = app.trackRepository,
                    cacheRepository = app.cacheRepository,
                    onTrackClick = onTrackClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
