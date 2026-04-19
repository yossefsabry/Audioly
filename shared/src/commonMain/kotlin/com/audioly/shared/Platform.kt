package com.audioly.shared

/**
 * Platform-specific information for the shared module.
 */
expect fun getPlatformName(): String

/**
 * Shared entry point for dependency injection / service creation.
 */
object SharedFactory {
    val platformName: String get() = getPlatformName()
}
