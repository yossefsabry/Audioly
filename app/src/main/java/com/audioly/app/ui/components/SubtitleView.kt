package com.audioly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.audioly.app.player.SubtitleCue

/**
 * Scrollable list of subtitle cues.
 *
 * - Highlights the currently active cue.
 * - Auto-scrolls to keep the active cue visible (unless user manually scrolled).
 * - Tapping a cue calls [onCueTap] with the cue's start position so the player
 *   can seek to it.
 */
@Composable
fun SubtitleView(
    cues: List<SubtitleCue>,
    activeCueIndex: Int,
    fontSizeSp: TextUnit,
    modifier: Modifier = Modifier,
    onCueTap: (positionMs: Long) -> Unit = {},
) {
    val listState = rememberLazyListState()
    var userScrolled by remember { mutableStateOf(false) }

    // Detect user-initiated scrolls via snapshotFlow
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling) {
                    userScrolled = true
                }
            }
    }

    // Auto-scroll when active cue changes and user hasn't manually scrolled
    LaunchedEffect(activeCueIndex) {
        if (activeCueIndex >= 0 && !userScrolled) {
            listState.animateScrollToItem(activeCueIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
    ) {
        itemsIndexed(cues) { index, cue ->
            val isActive = index == activeCueIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    )
                    .clickable {
                        userScrolled = false
                        onCueTap(cue.startMs)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = cue.text,
                    fontSize = fontSizeSp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
        }
    }
}
