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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.audioly.app.data.model.Track
import com.audioly.app.data.repository.CacheRepository
import com.audioly.app.data.repository.TrackRepository
import com.audioly.app.ui.components.TrackItem
import kotlinx.coroutines.launch

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
    @Suppress("UNUSED_VARIABLE")
    val cacheRefreshKey = cacheVersion
    val cached = allTracks.filter { cacheRepository.hasCachedAudio(it.videoId) }

    if (cached.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No cached tracks.\nTracks will be cached automatically during playback.")
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(cached, key = { it.videoId }) { track ->
                val status = cacheRepository.getAudioStatus(track.videoId)
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
