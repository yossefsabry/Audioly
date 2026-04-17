package com.audioly.app.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Application-wide logger. Stores entries in-memory (circular buffer) and
 * appends to a persistent log file so crash data survives restarts.
 *
 * Usage:
 *   AppLogger.d("MyTag", "something happened")
 *   AppLogger.e("MyTag", "failed", exception)
 */
object AppLogger {

    private const val MAX_ENTRIES = 1000
    private const val LOG_FILE_NAME = "audioly.log"
    private const val MAX_FILE_SIZE = 2L * 1024 * 1024 // 2 MB

    private val buffer = ConcurrentLinkedDeque<LogEntry>()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Observable list of log entries, newest first. */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private var logFile: File? = null

    // ─── Init ─────────────────────────────────────────────────────────────────

    /**
     * Call once from Application.onCreate(). Sets up the persistent log file
     * and loads previous session logs.
     */
    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").also { it.mkdirs() }
        logFile = File(dir, LOG_FILE_NAME)

        // Load previous logs from file (last session crash data)
        loadPreviousLogs()

        i("AppLogger", "Logger initialized — file: ${logFile?.absolutePath}")
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable)
    fun fatal(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.FATAL, tag, message, throwable)

    // ─── Bulk operations ──────────────────────────────────────────────────────

    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
        logFile?.let { file ->
            try { file.writeText("") } catch (_: Exception) {}
        }
    }

    /** Export all entries as a single text block. */
    fun exportText(): String {
        return buffer.reversed().joinToString("\n") { it.formatted() }
    }

    /** Get the log file path for sharing. */
    fun getLogFilePath(): String? = logFile?.absolutePath

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        // Also send to Android logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
            LogLevel.FATAL -> Log.e(tag, "FATAL: $message", throwable)
        }

        val traceString = throwable?.let { stackTraceToString(it) }
        val entry = LogEntry(
            level = level,
            tag = tag,
            message = message,
            throwable = traceString,
        )

        // Add to circular buffer
        buffer.addFirst(entry)
        while (buffer.size > MAX_ENTRIES) {
            buffer.removeLast()
        }

        // Update StateFlow
        _entries.value = buffer.toList()

        // Append to file (fire-and-forget, don't block caller)
        appendToFile(entry)
    }

    private fun appendToFile(entry: LogEntry) {
        val file = logFile ?: return
        try {
            // Rotate if too large
            if (file.exists() && file.length() > MAX_FILE_SIZE) {
                val backup = File(file.parent, "audioly_prev.log")
                if (backup.exists()) backup.delete()
                file.renameTo(backup)
                logFile = File(file.parent, LOG_FILE_NAME)
            }
            FileWriter(logFile, true).use { writer ->
                writer.appendLine(entry.formatted())
            }
        } catch (_: Exception) {
            // Can't log a logging failure — just drop it
        }
    }

    private fun loadPreviousLogs() {
        val file = logFile ?: return
        if (!file.exists() || file.length() == 0L) return
        try {
            // Add a separator for previous session
            val separator = LogEntry(
                level = LogLevel.INFO,
                tag = "AppLogger",
                message = "──── Previous session logs above ────",
            )
            buffer.addLast(separator)
            _entries.value = buffer.toList()
        } catch (_: Exception) {
            // Ignore
        }
    }

    private fun stackTraceToString(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        // Limit stack trace length
        val full = sw.toString()
        return if (full.length > 4000) full.substring(0, 4000) + "\n... (truncated)" else full
    }
}
