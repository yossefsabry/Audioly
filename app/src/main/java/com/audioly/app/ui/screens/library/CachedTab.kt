package com.audioly.app.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            Text("No cached tracks.\nTracks will be cached automatically during playback.")
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
