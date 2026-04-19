package com.audioly.shared.data.preferences

import kotlinx.coroutines.flow.Flow

/**
 * User preferences data class. Platform-agnostic.
 */
data class UserPreferences(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val playbackSpeed: Float = 1.0f,
    val subtitleLanguage: String = "",
    val subtitleFontSize: Float = 16f,
    val subtitlePosition: Float = 0.85f,
    val cacheSizeMb: Long = 512L,
    val skipIntervalSeconds: Int = 15,
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Platform-agnostic preferences interface.
 * Android: DataStore, iOS: NSUserDefaults wrapper.
 */
interface UserPreferencesService {
    val preferences: Flow<UserPreferences>

    suspend fun setTheme(mode: ThemeMode)
    suspend fun setPlaybackSpeed(speed: Float)
    suspend fun setSubtitleLanguage(language: String)
    suspend fun setSubtitleFontSize(size: Float)
    suspend fun setSubtitlePosition(position: Float)
    suspend fun setCacheSizeMb(sizeMb: Long)
    suspend fun setSkipInterval(seconds: Int)
}
