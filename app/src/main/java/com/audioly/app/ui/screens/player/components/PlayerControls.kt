package com.audioly.app.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.audioly.app.player.RepeatMode

/**
 * Player controls row matching the lyrics-player design:
 * [Repeat] [Previous] [Play/Pause] [Next] [Shuffle]
 *
 * Repeat and shuffle are always visible. Previous/next skip by
 * queue index when a queue exists, or seek ±15s otherwise.
 */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    modifier: Modifier = Modifier,
    onSkipToNext: (() -> Unit)? = null,
    onSkipToPrevious: (() -> Unit)? = null,
    repeatMode: RepeatMode = RepeatMode.OFF,
    onToggleRepeat: () -> Unit = {},
    shuffleEnabled: Boolean = false,
    onToggleShuffle: () -> Unit = {},
    // Legacy params kept for compat but unused in new design
    @Suppress("UNUSED_PARAMETER") skipIntervalSeconds: Int = 15,
    @Suppress("UNUSED_PARAMETER") hasQueue: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Repeat
        IconButton(onClick = onToggleRepeat, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = when (repeatMode) {
                    RepeatMode.ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                },
                contentDescription = "Repeat: ${repeatMode.name.lowercase()}",
                modifier = Modifier.size(24.dp),
                tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Previous (queue skip or seek back)
        IconButton(
            onClick = onSkipToPrevious ?: onSkipBack,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Play / Pause
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        // Next (queue skip or seek forward)
        IconButton(
            onClick = onSkipToNext ?: onSkipForward,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Shuffle
        IconButton(onClick = onToggleShuffle, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                modifier = Modifier.size(24.dp),
                tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
