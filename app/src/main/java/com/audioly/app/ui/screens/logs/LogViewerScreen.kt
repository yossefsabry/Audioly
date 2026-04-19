package com.audioly.app.ui.screens.logs

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioly.app.util.AppLogger
import com.audioly.app.util.LogEntry
import com.audioly.app.util.LogLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onNavigateUp: () -> Unit = {},
) {
    val allEntries by AppLogger.entries.collectAsState()
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val filtered = if (selectedLevel != null) {
        allEntries.filter { it.level == selectedLevel }
    } else {
        allEntries
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear logs?") },
            text = { Text("This will remove all log entries.") },
            confirmButton = {
                TextButton(onClick = {
                    AppLogger.clear()
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs (${filtered.size})") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val text = AppLogger.exportText()
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Audioly Logs")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share logs"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share logs")
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear logs")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedLevel == null,
                    onClick = { selectedLevel = null },
                    label = { Text("All") },
                )
                LogLevel.entries.reversed().forEach { level ->
                    val count = allEntries.count { it.level == level }
                    FilterChip(
                        selected = selectedLevel == level,
                        onClick = { selectedLevel = if (selectedLevel == level) null else level },
                        label = { Text("${level.label} ($count)") },
                    )
                }
            }

            HorizontalDivider()

            if (filtered.isEmpty()) {
                Text(
                    "No log entries.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    itemsIndexed(filtered, key = { index, entry -> "${index}-${entry.timestamp}-${entry.tag}" }) { _, entry ->
                        LogEntryRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.DEBUG -> Color(0xFF888888)
        LogLevel.INFO -> Color(0xFF4CAF50)
        LogLevel.WARN -> Color(0xFFFFC107)
        LogLevel.ERROR -> Color(0xFFFF5722)
        LogLevel.FATAL -> Color(0xFFD50000)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(
                color = if (entry.level.priority >= LogLevel.ERROR.priority)
                    levelColor.copy(alpha = 0.08f) else Color.Transparent,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row {
            // Level badge
            Text(
                text = entry.level.label,
                color = levelColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.width(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            // Timestamp
            Text(
                text = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                    .format(java.util.Date(entry.timestamp)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
            Spacer(Modifier.width(6.dp))
            // Tag
            Text(
                text = entry.tag,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        // Message
        Text(
            text = entry.message,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        // Stack trace (if present)
        if (entry.throwable != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.throwable,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = levelColor.copy(alpha = 0.8f),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
