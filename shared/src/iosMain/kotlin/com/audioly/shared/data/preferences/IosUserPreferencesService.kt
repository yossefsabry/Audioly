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

    override suspend fun setTheme(mode: ThemeMode) {
        defaults.setObject(mode.name, forKey = KEY_THEME)
        updateFlow()
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        defaults.setFloat(speed, forKey = KEY_PLAYBACK_SPEED)
        updateFlow()
    }

    override suspend fun setSubtitleLanguage(language: String) {
        defaults.setObject(language, forKey = KEY_SUBTITLE_LANG)
        updateFlow()
    }

    override suspend fun setSubtitleFontSize(size: Float) {
        defaults.setFloat(size, forKey = KEY_SUBTITLE_FONT)
        updateFlow()
    }

    override suspend fun setSubtitlePosition(position: Float) {
        defaults.setFloat(position, forKey = KEY_SUBTITLE_POS)
        updateFlow()
    }

    override suspend fun setCacheSizeMb(sizeMb: Long) {
        defaults.setInteger(sizeMb, forKey = KEY_CACHE_SIZE)
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
        val themeName = defaults.stringForKey(KEY_THEME)
        val theme = themeName?.let {
            try { ThemeMode.valueOf(it) } catch (_: Exception) { ThemeMode.SYSTEM }
        } ?: ThemeMode.SYSTEM

        val speed = defaults.floatForKey(KEY_PLAYBACK_SPEED).takeIf { it > 0f } ?: 1.0f
        val subtitleLang = defaults.stringForKey(KEY_SUBTITLE_LANG) ?: ""
        val subtitleFont = defaults.floatForKey(KEY_SUBTITLE_FONT).takeIf { it > 0f } ?: 16f
        val subtitlePos = defaults.floatForKey(KEY_SUBTITLE_POS).takeIf { it > 0f } ?: 0.85f
        val cacheSize = defaults.integerForKey(KEY_CACHE_SIZE).takeIf { it > 0 } ?: 512L
        val skipInterval = defaults.integerForKey(KEY_SKIP_INTERVAL).toInt().takeIf { it > 0 } ?: 15

        return UserPreferences(
            theme = theme,
            playbackSpeed = speed,
            subtitleLanguage = subtitleLang,
            subtitleFontSize = subtitleFont,
            subtitlePosition = subtitlePos,
            cacheSizeMb = cacheSize,
            skipIntervalSeconds = skipInterval,
        )
    }

    private companion object {
        const val KEY_THEME = "audioly_theme"
        const val KEY_PLAYBACK_SPEED = "audioly_playback_speed"
        const val KEY_SUBTITLE_LANG = "audioly_subtitle_lang"
        const val KEY_SUBTITLE_FONT = "audioly_subtitle_font"
        const val KEY_SUBTITLE_POS = "audioly_subtitle_pos"
        const val KEY_CACHE_SIZE = "audioly_cache_size"
        const val KEY_SKIP_INTERVAL = "audioly_skip_interval"
    }
}
