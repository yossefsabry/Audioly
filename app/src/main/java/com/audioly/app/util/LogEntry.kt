package com.audioly.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single log entry.
 */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: String? = null,
) {
    fun formatted(): String {
        val time = DATE_FMT.format(Date(timestamp))
        val base = "$time  ${level.label}  [$tag]  $message"
        return if (throwable != null) "$base\n$throwable" else base
    }

    companion object {
        private val DATE_FMT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    }
}

enum class LogLevel(val label: String, val priority: Int) {
    DEBUG("D", 0),
    INFO("I", 1),
    WARN("W", 2),
    ERROR("E", 3),
    FATAL("F", 4),
}
