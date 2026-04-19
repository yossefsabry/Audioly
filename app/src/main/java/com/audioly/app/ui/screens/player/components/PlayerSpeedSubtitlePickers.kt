package com.audioly.app.ui.screens.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@Composable
fun PlayerSpeedSubtitlePickers(
    currentSpeed: Float,
    selectedLanguage: String,
    availableLanguages: List<Pair<String, String>>,
    onSpeedSelected: (Float) -> Unit,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }

    // Resolve selected language code to display name
    val selectedDisplayName = availableLanguages
        .firstOrNull { it.first == selectedLanguage }?.second
        ?: selectedLanguage

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Box {
            TextButton(onClick = { showSpeedMenu = true }) {
                Text(formatSpeed(currentSpeed))
            }
            DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                SPEEDS.forEach { speed ->
                    DropdownMenuItem(
                        text = { Text(formatSpeed(speed)) },
                        onClick = { onSpeedSelected(speed); showSpeedMenu = false },
                    )
                }
            }
        }

        if (availableLanguages.isNotEmpty()) {
            Box {
                TextButton(onClick = { showSubtitleMenu = true }) {
                    Text(
                        text = selectedDisplayName.ifEmpty { "Subtitles" },
                        maxLines = 1,
                    )
                }
                DropdownMenu(expanded = showSubtitleMenu, onDismissRequest = { showSubtitleMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Off") },
                        onClick = { onLanguageSelected(""); showSubtitleMenu = false },
                    )
                    availableLanguages.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = { onLanguageSelected(code); showSubtitleMenu = false },
                        )
                    }
                }
            }
        }
    }
}

private fun formatSpeed(speed: Float): String {
    val s = "%.2f".format(speed).trimEnd('0').trimEnd('.')
    return "${s}x"
}
