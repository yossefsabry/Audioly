package com.audioly.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
 * Lyrics-style subtitle view with smooth crossfade transitions.
 *
 * - Active cue is large, bold, and fully opaque (center of attention).
 * - Surrounding cues are smaller and faded out.
 * - Transitions between active/inactive states animate smoothly (~300ms).
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
            SubtitleCueItem(
                cue = cue,
                isActive = index == activeCueIndex,
                activeCueIndex = activeCueIndex,
                index = index,
                baseFontSizeSp = fontSizeSp,
                onTap = {
                    userScrolled = false
                    onCueTap(cue.startMs)
                },
            )
        }
    }
}

/**
 * Individual subtitle cue item with animated transitions.
 *
 * All visual properties (alpha, font size, vertical padding) animate smoothly
 * when the cue transitions between active and inactive states, creating a
 * gentle crossfade effect that's easy on the eyes.
 */
@Composable
private fun SubtitleCueItem(
    cue: SubtitleCue,
    isActive: Boolean,
    activeCueIndex: Int,
    index: Int,
    baseFontSizeSp: TextUnit,
    onTap: () -> Unit,
) {
    val animDuration = 300 // ms

    // Target values based on active state
    val distance = if (activeCueIndex >= 0) kotlin.math.abs(index - activeCueIndex) else 0
    val targetAlpha = when {
        isActive -> 1f
        distance == 1 -> 0.55f
        distance == 2 -> 0.35f
        else -> 0.20f
    }
    val targetFontSizeMultiplier = if (isActive) 1.35f else 1f
    val targetVerticalPadding = if (isActive) 12f else 6f

    // Animate all visual properties
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = animDuration),
        label = "cueAlpha",
    )
    val animatedFontSizeMultiplier by animateFloatAsState(
        targetValue = targetFontSizeMultiplier,
        animationSpec = tween(durationMillis = animDuration),
        label = "cueFontSize",
    )
    val animatedVerticalPadding by animateDpAsState(
        targetValue = targetVerticalPadding.dp,
        animationSpec = tween(durationMillis = animDuration),
        label = "cuePadding",
    )

    val fontSize = baseFontSizeSp * animatedFontSizeMultiplier
    // Animate font weight: bold threshold at > 0.5 multiplier progress
    val fontWeight = if (animatedFontSizeMultiplier > 1.15f) FontWeight.Bold else FontWeight.Normal

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 24.dp, vertical = animatedVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cue.text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = animatedAlpha),
            textAlign = TextAlign.Center,
            lineHeight = fontSize * 1.3f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
