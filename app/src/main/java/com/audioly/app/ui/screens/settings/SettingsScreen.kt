package com.audioly.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioly.app.AudiolyApp
import com.audioly.shared.data.preferences.UserPreferences
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: AudiolyApp, onNavigateToLogs: () -> Unit = {}) {
    val prefs by app.preferencesRepository.preferences.collectAsState(initial = UserPreferences())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ─── Theme ────────────────────────────────────────────────────────
            SectionHeader("Appearance")
            DropdownSetting(
                label = "Theme",
                selected = prefs.themeMode,
                options = listOf(UserPreferences.THEME_SYSTEM, UserPreferences.THEME_LIGHT, UserPreferences.THEME_DARK),
                onSelect = { scope.launch { app.preferencesRepository.setThemeMode(it) } },
            )

            HorizontalDivider()

            // ─── Playback ─────────────────────────────────────────────────────
            SectionHeader("Playback")
            SliderSetting(
                label = "Default speed",
                value = prefs.playbackSpeed,
                valueRange = 0.5f..2.0f,
                steps = 5,
                format = { "%.2fx".format(it) },
                onChange = { scope.launch { app.preferencesRepository.setPlaybackSpeed(it) } },
            )
            SliderSetting(
                label = "Skip interval (seconds)",
                value = prefs.skipIntervalSeconds.toFloat(),
                valueRange = 5f..30f,
                steps = 4,
                format = { "${it.roundToInt()}s" },
                onChange = { scope.launch { app.preferencesRepository.setSkipInterval(it.roundToInt()) } },
            )

            HorizontalDivider()

            // ─── Subtitles ────────────────────────────────────────────────────
            SectionHeader("Subtitles")
            SliderSetting(
                label = "Font size",
                value = prefs.subtitleFontSizeSp,
                valueRange = 10f..28f,
                steps = 8,
                format = { "${it.roundToInt()}sp" },
                onChange = { scope.launch { app.preferencesRepository.setSubtitleFontSize(it) } },
            )
            DropdownSetting(
                label = "Subtitle position",
                selected = prefs.subtitlePosition,
                options = listOf(
                    UserPreferences.SUBTITLE_BOTTOM,
                    UserPreferences.SUBTITLE_MIDDLE,
                    UserPreferences.SUBTITLE_TOP,
                ),
                onSelect = { scope.launch { app.preferencesRepository.setSubtitlePosition(it) } },
            )

            HorizontalDivider()

            // ─── Cache ────────────────────────────────────────────────────────
            SectionHeader("Cache")
            SliderSetting(
                label = "Max cache size (restart required)",
                value = (prefs.maxCacheBytes / (1024 * 1024)).toFloat(),
                valueRange = 64f..2048f,
                steps = 30,
                format = { "${it.roundToInt()} MB" },
                onChange = { scope.launch { app.preferencesRepository.setMaxCacheBytes(it.toLong() * 1024 * 1024) } },
            )

            HorizontalDivider()

            // ─── Developer ────────────────────────────────────────────────────
            SectionHeader("Developer")
            TextButton(
                onClick = onNavigateToLogs,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text("View Logs")
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f))
            Text(format(value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
