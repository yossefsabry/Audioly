package com.audioly.app.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.audioly.app.player.RepeatMode

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    skipIntervalSeconds: Int,
    onTogglePlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    modifier: Modifier = Modifier,
    // Queue-aware controls
    hasQueue: Boolean = false,
    onSkipToNext: (() -> Unit)? = null,
    onSkipToPrevious: (() -> Unit)? = null,
    repeatMode: RepeatMode = RepeatMode.OFF,
    onToggleRepeat: (() -> Unit)? = null,
    shuffleEnabled: Boolean = false,
    onToggleShuffle: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shuffle button (only when queue active)
            if (hasQueue && onToggleShuffle != null) {
                IconButton(onClick = onToggleShuffle, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        modifier = Modifier.size(22.dp),
                        tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Skip previous (queue) or skip back
            if (hasQueue && onSkipToPrevious != null) {
                IconButton(onClick = onSkipToPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            IconButton(
                onClick = onSkipBack,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    Icons.Default.FastRewind,
                    contentDescription = "Skip back ${skipIntervalSeconds}s",
                    modifier = Modifier.size(32.dp),
                )
            }

            if (isBuffering) {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
            } else {
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            IconButton(
                onClick = onSkipForward,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    Icons.Default.FastForward,
                    contentDescription = "Skip forward ${skipIntervalSeconds}s",
                    modifier = Modifier.size(32.dp),
                )
            }

            // Skip next (queue)
            if (hasQueue && onSkipToNext != null) {
                IconButton(onClick = onSkipToNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // Repeat button (only when queue active)
            if (hasQueue && onToggleRepeat != null) {
                IconButton(onClick = onToggleRepeat, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = when (repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat: ${repeatMode.name.lowercase()}",
                        modifier = Modifier.size(22.dp),
                        tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
