package com.audioly.app.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioly.app.data.model.Track
import com.audioly.app.data.repository.TrackRepository
import com.audioly.app.ui.components.TrackItem
import kotlinx.coroutines.flow.Flow

@Composable
fun HistoryTab(
    trackRepository: TrackRepository,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val history by trackRepository.observeHistory().collectAsState(initial = emptyList())

    if (history.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No history yet.\nPaste or share a YouTube URL to get started.")
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(history, key = { it.videoId }) { track ->
                TrackItem(
                    track = track,
                    onClick = { onTrackClick(track) },
                )
            }
        }
    }
}
