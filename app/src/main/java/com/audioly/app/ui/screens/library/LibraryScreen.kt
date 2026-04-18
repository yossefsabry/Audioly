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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.audioly.app.ui.viewmodel.LibraryEvent
import com.audioly.app.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToPlayer: (String) -> Unit = {},
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val tabs = listOf("History", "Playlists", "Cached")
    val snackbarHostState = remember { SnackbarHostState() }

    // Consume one-shot events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.NavigateToPlayer -> onNavigateToPlayer(event.videoId)
                is LibraryEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

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
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> HistoryTab(
                    trackRepository = viewModel.trackRepository,
                    cacheRepository = viewModel.cacheRepository,
                    onTrackClick = { track -> viewModel.playTrack(track) },
                    modifier = Modifier.fillMaxSize(),
                )
                1 -> PlaylistsTab(
                    playlistRepository = viewModel.playlistRepository,
                    onPlaylistClick = { /* TODO: navigate to playlist detail */ },
                    modifier = Modifier.fillMaxSize(),
                )
                2 -> CachedTab(
                    trackRepository = viewModel.trackRepository,
                    cacheRepository = viewModel.cacheRepository,
                    onTrackClick = { track -> viewModel.playTrack(track) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
