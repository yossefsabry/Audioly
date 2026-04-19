package com.audioly.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Subtitle overlay for the player screen. Shared between Android and iOS.
 *
 * @param text The subtitle line(s) to display. Empty = hidden.
 * @param fontSizeSp Font size in sp (from user preferences).
 * @param alignment Where to pin the overlay: [Alignment.BottomCenter],
 *   [Alignment.Center], or [Alignment.TopCenter].
 */
@Composable
fun SubtitleOverlay(
    text: String,
    fontSizeSp: Float = 16f,
    alignment: Alignment = Alignment.BottomCenter,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return

    Box(
        contentAlignment = alignment,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSizeSp.sp),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.small,
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Maps a subtitle position string ("bottom"/"middle"/"top") to a Compose [Alignment]. */
fun subtitleAlignment(position: String): Alignment = when (position) {
    "top" -> Alignment.TopCenter
    "middle" -> Alignment.Center
    else -> Alignment.BottomCenter
}
