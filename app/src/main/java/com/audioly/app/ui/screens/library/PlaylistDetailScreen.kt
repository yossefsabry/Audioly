package com.audioly.app.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioly.app.data.model.Track

import com.audioly.app.data.repository.PlaylistRepository
import com.audioly.app.player.QueueItem
import com.audioly.app.ui.components.TrackItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playlistRepository: PlaylistRepository,
    onNavigateUp: () -> Unit,
    onPlayAll: (List<QueueItem>, startIndex: Int) -> Unit,
    onPlayTrack: (Track) -> Unit,
) {
    val tracks by playlistRepository.observePlaylistTracks(playlistId)
        .collectAsState(initial = null)
    val playlist by remember(playlistId) {
        kotlinx.coroutines.flow.flow {
            emit(playlistRepository.getPlaylist(playlistId))
        }
    }.collectAsState(initial = null)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val trackList = tracks
        when {
            trackList == null -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            trackList.isEmpty() -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No tracks in this playlist yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Search for videos and add them here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.padding(padding)) {
                    // Play all / Shuffle buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                onPlayAll(trackList.map { it.toQueueItem() }, 0)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("Play All", modifier = Modifier.padding(start = 4.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                val shuffled = trackList.shuffled()
                                onPlayAll(shuffled.map { it.toQueueItem() }, 0)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null)
                            Text("Shuffle", modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    Text(
                        "${trackList.size} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    LazyColumn {
                        itemsIndexed(trackList, key = { _, t -> t.videoId }) { index, track ->
                            TrackItem(
                                track = track,
                                isCached = track.audioFilePath != null,
                                onClick = {
                                    // Play from this index as a queue
                                    onPlayAll(trackList.map { it.toQueueItem() }, index)
                                },
                                trailing = {
                                    IconButton(onClick = {
                                        scope.launch {
                                            playlistRepository.removeTrackFromPlaylist(playlistId, track.videoId)
                                            snackbarHostState.showSnackbar("Removed from playlist")
                                        }
                                    }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
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

private fun Track.toQueueItem() = QueueItem(
    videoId = videoId,
    title = title,
    uploader = uploader,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    audioUrl = audioStreamUrl,
)
