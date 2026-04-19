package com.audioly.app.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.audioly.app.ui.components.SubtitleView
import com.audioly.app.ui.screens.player.components.PlayerArtwork
import com.audioly.app.ui.screens.player.components.PlayerControls
import com.audioly.app.ui.screens.player.components.PlayerSeekBar
import com.audioly.app.ui.screens.player.components.PlayerSpeedSubtitlePickers
import com.audioly.app.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateUp: () -> Unit = {},
) {
    val state by viewModel.playerState.collectAsState()
    val prefs by viewModel.prefs.collectAsState()
    val subtitleCues by viewModel.subtitleCues.collectAsState()
    val activeCueIndex by viewModel.activeCueIndex.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    val showSubtitles by viewModel.showSubtitles.collectAsState()
    val hasSubtitleTracks by viewModel.hasSubtitleTracks.collectAsState()
    val queueItems by viewModel.queue.collectAsState()
    val queueIdx by viewModel.queueIndex.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val hasQueue = queueItems.size > 1

    var showQueueSheet by remember { mutableStateOf(false) }

    val availableLanguages = subtitleTracks.map { it.languageCode }.distinct().sorted()

    // Loading state — no video loaded yet
    if (state.isEmpty) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Loading...") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Preparing audio...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasQueue) {
                        IconButton(onClick = { showQueueSheet = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Queue (${queueItems.size})",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ─── Main content area ───────────────────────────────────────
            // When subtitles are showing: lyrics replace the artwork (Image 4 design)
            // When no subtitles: show artwork + title/uploader
            if (showSubtitles && subtitleCues.isNotEmpty()) {
                // Lyrics area takes all available space
                SubtitleView(
                    cues = subtitleCues,
                    activeCueIndex = activeCueIndex,
                    fontSizeSp = prefs.subtitleFontSizeSp.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onCueTap = { posMs -> viewModel.seekTo(posMs) },
                )

                Spacer(Modifier.height(8.dp))

                // Title + uploader below lyrics (compact)
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.uploader,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                // No subtitles — artwork-focused layout
                Spacer(Modifier.height(16.dp))

                PlayerArtwork(
                    thumbnailUrl = state.thumbnailUrl,
                    title = state.title,
                )

                Spacer(Modifier.height(16.dp))

                // Title + uploader
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.uploader,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (state.hasCachedAudio) {
                    Text(
                        text = if (state.isFullyCached) "Available offline"
                        else "Caching audio ${formatCacheProgress(state.cachedBytes, state.cacheContentLength)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Error message
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // Waiting for subtitles indicator (only when a language is selected, not when user turned Off)
                if (hasSubtitleTracks && !showSubtitles && state.selectedSubtitleLanguage.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Loading lyrics...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
            }

            // ─── Controls section (always at bottom) ─────────────────────

            // Playback controls: repeat, prev, play/pause, next, shuffle
            PlayerControls(
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onSkipBack = { viewModel.skipBack(prefs.skipIntervalSeconds * 1000L) },
                onSkipForward = { viewModel.skipForward(prefs.skipIntervalSeconds * 1000L) },
                onSkipToNext = if (hasQueue) ({ viewModel.skipToNext() }) else null,
                onSkipToPrevious = if (hasQueue) ({ viewModel.skipToPrevious() }) else null,
                repeatMode = repeatMode,
                onToggleRepeat = { viewModel.toggleRepeatMode() },
                shuffleEnabled = shuffleEnabled,
                onToggleShuffle = { viewModel.toggleShuffle() },
            )

            // Seek bar
            PlayerSeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                bufferedFraction = state.bufferedFraction,
                onSeek = { viewModel.seekTo(it) },
            )

            // Speed + subtitle pickers (compact row at bottom)
            PlayerSpeedSubtitlePickers(
                currentSpeed = state.playbackSpeed,
                selectedLanguage = state.selectedSubtitleLanguage,
                availableLanguages = availableLanguages,
                onSpeedSelected = { viewModel.setSpeed(it) },
                onLanguageSelected = { viewModel.setSubtitleLanguage(it) },
            )

            Spacer(Modifier.height(8.dp))
        }
    }

    // Queue bottom sheet
    if (showQueueSheet && hasQueue) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Up Next",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                LazyColumn {
                    itemsIndexed(queueItems, key = { idx, item -> "${item.videoId}_$idx" }) { idx, item ->
                        val isCurrent = idx == queueIdx
                        Surface(
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (item.thumbnailUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = item.title,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = item.uploader,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (!isCurrent) {
                                    IconButton(onClick = { viewModel.removeFromQueue(idx) }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun formatCacheProgress(cachedBytes: Long, contentLength: Long): String {
    if (cachedBytes <= 0L) return "0%"
    if (contentLength <= 0L) return "${cachedBytes / 1024} KB"
    val percent = ((cachedBytes.toDouble() / contentLength.toDouble()) * 100.0)
        .toInt().coerceIn(0, 100)
    return "$percent%"
}
