package com.audioly.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.audioly.app.AudiolyApp
import com.audioly.app.player.SubtitleManager
import com.audioly.app.player.VttParser
import com.audioly.app.ui.components.SubtitleView
import com.audioly.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    app: AudiolyApp,
    onNavigateUp: () -> Unit = {},
) {
    val playerRepository = app.playerRepository
    val state by playerRepository.state.collectAsState()

    // Show loading if player is empty (no video loaded yet)
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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

    // ─── Subtitle state ───────────────────────────────────────────────────────

    val subtitleTracks by playerRepository.subtitleTracks.collectAsState()
    val subtitleContentMap by playerRepository.subtitleContent.collectAsState()

    val subtitleManager = remember(state.videoId) { SubtitleManager() }

    // Auto-select first available subtitle language when tracks load
    LaunchedEffect(subtitleTracks) {
        if (subtitleTracks.isNotEmpty() && state.selectedSubtitleLanguage.isEmpty()) {
            playerRepository.setSubtitleLanguage(subtitleTracks.first().languageCode)
        }
    }

    // Load parsed cues when VTT content or selected language changes
    LaunchedEffect(state.selectedSubtitleLanguage, subtitleContentMap) {
        val lang = state.selectedSubtitleLanguage
        val vtt = subtitleContentMap[lang]
        if (vtt != null) {
            subtitleManager.load(VttParser.parse(vtt))
        } else {
            subtitleManager.load(emptyList())
        }
    }

    // Download VTT when user selects a new language
    LaunchedEffect(state.selectedSubtitleLanguage) {
        val lang = state.selectedSubtitleLanguage
        if (lang.isBlank()) return@LaunchedEffect
        if (subtitleContentMap.containsKey(lang)) return@LaunchedEffect

        val videoId = state.videoId ?: return@LaunchedEffect
        val track = subtitleTracks.firstOrNull { it.languageCode == lang }
            ?: return@LaunchedEffect

        // Try disk cache first (IO dispatcher for file access)
        val cached = try {
            withContext(Dispatchers.IO) {
                app.subtitleCacheManager.load(videoId, lang)
            }
        } catch (e: Exception) {
            null
        }

        if (cached != null) {
            playerRepository.addSubtitleContent(lang, cached)
            return@LaunchedEffect
        }

        // Download from URL
        val content = downloadVttContent(track.url) ?: return@LaunchedEffect

        // Cache for future use (non-fatal if fails)
        try {
            withContext(Dispatchers.IO) {
                app.subtitleCacheManager.save(
                    videoId = videoId,
                    languageCode = lang,
                    languageName = track.languageName,
                    format = track.format,
                    isAutoGenerated = track.isAutoGenerated,
                    content = content,
                )
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to cache subtitle: ${e.message}")
        }

        playerRepository.addSubtitleContent(lang, content)
    }

    val activeCueIndex = subtitleManager.activeIndex(state.positionMs)
    LaunchedEffect(activeCueIndex) {
        playerRepository.setSubtitleIndex(activeCueIndex)
    }

    // ─── Speed / subtitle pickers ─────────────────────────────────────────────

    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }

    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    val availableLanguages = subtitleTracks.map { it.languageCode }.distinct().sorted()

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
            // Artwork
            if (state.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = state.thumbnailUrl,
                    contentDescription = state.title,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp)),
                )
            } else {
                // Placeholder when no thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Title + uploader
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.uploader,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Error message
            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Progress — use local state for smooth dragging, only seek on release
            val durationMs = state.durationMs.coerceAtLeast(1L)
            var isDragging by remember { mutableStateOf(false) }
            var dragPosition by remember { mutableStateOf(0f) }
            val displayPosition = if (isDragging) dragPosition else state.positionMs.toFloat()

            // Buffer indicator + seek bar layered
            Box(modifier = Modifier.fillMaxWidth()) {
                // Gray buffered progress bar (behind the slider)
                LinearProgressIndicator(
                    progress = { state.bufferedFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                // Seek slider on top
                Slider(
                    value = displayPosition,
                    onValueChange = { value ->
                        isDragging = true
                        dragPosition = value
                    },
                    onValueChangeFinished = {
                        playerRepository.seekTo(dragPosition.toLong())
                        isDragging = false
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
                    ),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatMs(state.positionMs), style = MaterialTheme.typography.bodySmall)
                Text(formatMs(state.durationMs), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))

            // Playback controls
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { playerRepository.skipBack() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.FastRewind, contentDescription = "Skip back 15s", modifier = Modifier.size(32.dp))
                    }
                    
                    if (state.isBuffering) {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    } else {
                        IconButton(
                            onClick = { playerRepository.togglePlayPause() },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = { playerRepository.skipForward() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = "Skip forward 15s", modifier = Modifier.size(32.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Speed + subtitle language pickers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Box {
                    TextButton(onClick = { showSpeedMenu = true }) {
                        Text("${state.playbackSpeed}x")
                    }
                    DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                        speeds.forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}x") },
                                onClick = { playerRepository.setSpeed(speed); showSpeedMenu = false },
                            )
                        }
                    }
                }

                if (availableLanguages.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { showSubtitleMenu = true }) {
                            Text(
                                text = state.selectedSubtitleLanguage.ifEmpty { "Subtitles" },
                                maxLines = 1,
                            )
                        }
                        DropdownMenu(expanded = showSubtitleMenu, onDismissRequest = { showSubtitleMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Off") },
                                onClick = { playerRepository.setSubtitleLanguage(""); showSubtitleMenu = false },
                            )
                            availableLanguages.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang) },
                                    onClick = { playerRepository.setSubtitleLanguage(lang); showSubtitleMenu = false },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Subtitle list
            if (!subtitleManager.isEmpty && state.selectedSubtitleLanguage.isNotEmpty()) {
                SubtitleView(
                    cues = subtitleManager.cues,
                    activeCueIndex = activeCueIndex,
                    fontSizeSp = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onCueTap = { posMs -> playerRepository.seekTo(posMs) },
                )
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private const val TAG = "PlayerScreen"

/** Shared OkHttpClient for subtitle downloads — avoids creating per-request instances. */
private val subtitleHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

/** Downloads VTT subtitle content from a URL. Returns null on failure. */
private suspend fun downloadVttContent(url: String): String? = withContext(Dispatchers.IO) {
    try {
        val request = okhttp3.Request.Builder().url(url).build()
        subtitleHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (e: Exception) {
        AppLogger.w(TAG, "Failed to download subtitle: ${e.message}")
        null
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
