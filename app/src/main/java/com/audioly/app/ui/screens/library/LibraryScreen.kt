package com.audioly.app.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.audioly.app.AudiolyApp
import com.audioly.app.data.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    app: AudiolyApp,
    onNavigateToPlayer: (String) -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("History", "Playlists", "Cached")

    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
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

            val onTrackClick: (Track) -> Unit = { track -> onNavigateToPlayer(track.videoId) }

            when (selectedTab) {
                0 -> HistoryTab(
                    trackRepository = app.trackRepository,
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
