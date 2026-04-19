package com.audioly.app.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioly.app.data.cache.AudioCacheStatus
import com.audioly.app.data.model.Track
import com.audioly.app.data.repository.CacheRepository
import com.audioly.app.data.repository.TrackRepository
import com.audioly.app.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CachedTab(
    trackRepository: TrackRepository,
    cacheRepository: CacheRepository,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allTracks by trackRepository.observeAll().collectAsState(initial = emptyList())
    val cacheVersion by cacheRepository.cacheVersion.collectAsState()
    val scope = rememberCoroutineScope()

    // Compute cached tracks + statuses off the main thread to avoid jank
    val cachedWithStatus by produceState<List<Pair<Track, AudioCacheStatus>>>(
        emptyList(), allTracks, cacheVersion,
    ) {
        value = withContext(Dispatchers.IO) {
            allTracks.mapNotNull { track ->
                val status = cacheRepository.getAudioStatus(track.videoId)
                if (status.hasCache) track to status else null
            }
        }
    }

    if (cachedWithStatus.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.DownloadDone,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "No cached tracks",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tracks are cached during playback or from the player download button.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(cachedWithStatus, key = { it.first.videoId }) { (track, status) ->
                TrackItem(
                    track = track,
                    isCached = status.hasCache,
                    onClick = { onTrackClick(track) },
                    trailing = {
                        IconButton(onClick = {
                            scope.launch { cacheRepository.deleteVideo(track.videoId) }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove from cache")
                        }
                    },
                )
            }
        }
    }
}
