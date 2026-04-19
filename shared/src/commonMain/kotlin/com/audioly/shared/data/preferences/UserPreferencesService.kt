package com.audioly.shared.data.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Immutable snapshot of all user preferences. Shared across platforms.
 */
data class UserPreferences(
    /** "system", "light", or "dark" */
    val themeMode: String = THEME_DARK,
    /** Playback speed multiplier, e.g. 1.0f, 1.5f, 2.0f */
    val playbackSpeed: Float = 1.0f,
    /** BCP-47 language code preferred for subtitles, empty = none. */
    val preferredSubtitleLanguage: String = "",
    /** Subtitle font size in sp. */
    val subtitleFontSizeSp: Float = 16f,
    /** Subtitle vertical position: "bottom", "middle", "top" */
    val subtitlePosition: String = SUBTITLE_BOTTOM,
    /** Maximum audio cache size in bytes. */
    val maxCacheBytes: Long = DEFAULT_CACHE_BYTES,
    /** Skip interval in seconds for forward/backward buttons. */
    val skipIntervalSeconds: Int = 15,
) {
    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val SUBTITLE_BOTTOM = "bottom"
        const val SUBTITLE_MIDDLE = "middle"
        const val SUBTITLE_TOP = "top"

        const val DEFAULT_CACHE_BYTES = 512L * 1024 * 1024
    }
}

/** Utility enum — useful for UI pickers, maps to themeMode strings. */
enum class ThemeMode(val value: String) {
    LIGHT(UserPreferences.THEME_LIGHT),
    DARK(UserPreferences.THEME_DARK),
    SYSTEM(UserPreferences.THEME_SYSTEM),
}

/**
 * Platform-agnostic preferences interface.
 * Android: DataStore, iOS: NSUserDefaults wrapper.
 */
interface UserPreferencesService {
    val preferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: String)
    suspend fun setPlaybackSpeed(speed: Float)
    suspend fun setPreferredSubtitleLanguage(lang: String)
    suspend fun setSubtitleFontSize(sizeSp: Float)
    suspend fun setSubtitlePosition(position: String)
    suspend fun setMaxCacheBytes(bytes: Long)
    suspend fun setSkipInterval(seconds: Int)
}
