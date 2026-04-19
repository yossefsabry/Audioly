package com.audioly.shared.util

/**
 * Multiplatform logger interface. Platform implementations use
 * Android Logcat / iOS NSLog respectively.
 */
expect object AppLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
