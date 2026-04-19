package com.audioly.app.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioly.app.player.SubtitleCue
import kotlinx.coroutines.delay

/**
 * Lyrics-style subtitle view.
 *
 * - Active cue is large, bold, and fully opaque (center of attention).
 * - Surrounding cues are smaller and faded out.
 * - Auto-scrolls to center the active cue (unless user manually scrolled recently).
 * - Tapping any cue seeks playback to that position.
 */
@Composable
fun SubtitleView(
    cues: List<SubtitleCue>,
    activeCueIndex: Int,
    fontSizeSp: TextUnit = 18.sp,
    modifier: Modifier = Modifier,
    onCueTap: (positionMs: Long) -> Unit = {},
) {
    val listState = rememberLazyListState()
    var userScrolled by remember { mutableStateOf(false) }
    var programmaticScrollInFlight by remember { mutableStateOf(false) }

    // Detect user-initiated scrolls
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling && !programmaticScrollInFlight) {
                    userScrolled = true
                }
            }
    }

    // Reset userScrolled after 3 seconds so auto-scroll resumes
    LaunchedEffect(userScrolled) {
        if (userScrolled) {
            delay(3_000L)
            userScrolled = false
        }
    }

    // Auto-scroll to center active cue
    LaunchedEffect(activeCueIndex, userScrolled, cues) {
        if (activeCueIndex >= 0 && !userScrolled) {
            programmaticScrollInFlight = true
            try {
                // Scroll so active cue is roughly centered in the visible area
                val visibleCount = listState.layoutInfo.visibleItemsInfo.size
                val targetIndex = (activeCueIndex - visibleCount / 2).coerceAtLeast(0)
                listState.animateScrollToItem(targetIndex)
            } finally {
                programmaticScrollInFlight = false
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(cues) { index, cue ->
            val isActive = index == activeCueIndex
            // Distance from active cue determines fade level
            val distance = if (activeCueIndex >= 0) kotlin.math.abs(index - activeCueIndex) else 0
            val alpha = when {
                isActive -> 1f
                distance == 1 -> 0.55f
                distance == 2 -> 0.35f
                else -> 0.20f
            }
            val fontSize = if (isActive) {
                fontSizeSp * 1.35f
            } else {
                fontSizeSp
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        userScrolled = false
                        onCueTap(cue.startMs)
                    }
                    .padding(horizontal = 24.dp, vertical = if (isActive) 12.dp else 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cue.text,
                    fontSize = fontSize,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
                    textAlign = TextAlign.Center,
                    lineHeight = fontSize * 1.3f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
