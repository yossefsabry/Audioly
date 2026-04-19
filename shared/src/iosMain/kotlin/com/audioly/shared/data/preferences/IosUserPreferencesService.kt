package com.audioly.shared.data.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.NSUserDefaults

/**
 * iOS UserPreferences backed by NSUserDefaults.
 */
class IosUserPreferencesService : UserPreferencesService {

    private val defaults = NSUserDefaults.standardUserDefaults

    private val _preferences = MutableStateFlow(loadPreferences())
    override val preferences: Flow<UserPreferences> = _preferences

    override suspend fun setThemeMode(mode: String) {
        defaults.setObject(mode, forKey = KEY_THEME_MODE)
        updateFlow()
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        defaults.setFloat(speed, forKey = KEY_PLAYBACK_SPEED)
        updateFlow()
    }

    override suspend fun setPreferredSubtitleLanguage(lang: String) {
        defaults.setObject(lang, forKey = KEY_SUBTITLE_LANG)
        updateFlow()
    }

    override suspend fun setSubtitleFontSize(sizeSp: Float) {
        defaults.setFloat(sizeSp, forKey = KEY_SUBTITLE_FONT)
        updateFlow()
    }

    override suspend fun setSubtitlePosition(position: String) {
        defaults.setObject(position, forKey = KEY_SUBTITLE_POS)
        updateFlow()
    }

    override suspend fun setMaxCacheBytes(bytes: Long) {
        defaults.setInteger(bytes, forKey = KEY_MAX_CACHE_BYTES)
        updateFlow()
    }

    override suspend fun setSkipInterval(seconds: Int) {
        defaults.setInteger(seconds.toLong(), forKey = KEY_SKIP_INTERVAL)
        updateFlow()
    }

    private fun updateFlow() {
        _preferences.value = loadPreferences()
    }

    private fun loadPreferences(): UserPreferences {
        val themeMode = defaults.stringForKey(KEY_THEME_MODE)
            ?.takeIf { it in listOf(UserPreferences.THEME_LIGHT, UserPreferences.THEME_DARK, UserPreferences.THEME_SYSTEM) }
            ?: UserPreferences.THEME_DARK

        val speed = defaults.floatForKey(KEY_PLAYBACK_SPEED).takeIf { it > 0f } ?: 1.0f
        val subtitleLang = defaults.stringForKey(KEY_SUBTITLE_LANG) ?: ""
        val subtitleFont = defaults.floatForKey(KEY_SUBTITLE_FONT).takeIf { it > 0f } ?: 16f
        val subtitlePos = defaults.stringForKey(KEY_SUBTITLE_POS)
            ?.takeIf { it in listOf(UserPreferences.SUBTITLE_BOTTOM, UserPreferences.SUBTITLE_MIDDLE, UserPreferences.SUBTITLE_TOP) }
            ?: UserPreferences.SUBTITLE_BOTTOM
        val maxCache = defaults.integerForKey(KEY_MAX_CACHE_BYTES).takeIf { it > 0 }
            ?: UserPreferences.DEFAULT_CACHE_BYTES
        val skipInterval = defaults.integerForKey(KEY_SKIP_INTERVAL).toInt().takeIf { it > 0 } ?: 15

        return UserPreferences(
            themeMode = themeMode,
            playbackSpeed = speed,
            preferredSubtitleLanguage = subtitleLang,
            subtitleFontSizeSp = subtitleFont,
            subtitlePosition = subtitlePos,
            maxCacheBytes = maxCache,
            skipIntervalSeconds = skipInterval,
        )
    }

    private companion object {
        const val KEY_THEME_MODE = "audioly_theme_mode"
        const val KEY_PLAYBACK_SPEED = "audioly_playback_speed"
        const val KEY_SUBTITLE_LANG = "audioly_subtitle_lang"
        const val KEY_SUBTITLE_FONT = "audioly_subtitle_font"
        const val KEY_SUBTITLE_POS = "audioly_subtitle_pos"
        const val KEY_MAX_CACHE_BYTES = "audioly_max_cache_bytes"
        const val KEY_SKIP_INTERVAL = "audioly_skip_interval"
    }
}
