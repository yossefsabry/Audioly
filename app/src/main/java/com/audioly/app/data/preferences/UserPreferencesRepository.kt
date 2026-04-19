package com.audioly.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(private val context: Context) {

    private val store = context.dataStore

    val preferences: Flow<UserPreferences> = store.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[KEY_THEME_MODE] ?: UserPreferences.THEME_DARK,
            playbackSpeed = prefs[KEY_PLAYBACK_SPEED] ?: 1.0f,
            preferredSubtitleLanguage = prefs[KEY_SUBTITLE_LANGUAGE] ?: "en",
            subtitleFontSizeSp = prefs[KEY_SUBTITLE_FONT_SIZE] ?: 16f,
            subtitlePosition = prefs[KEY_SUBTITLE_POSITION] ?: UserPreferences.SUBTITLE_BOTTOM,
            maxCacheBytes = prefs[KEY_MAX_CACHE_BYTES] ?: UserPreferences.DEFAULT_CACHE_BYTES,
            skipIntervalSeconds = prefs[KEY_SKIP_INTERVAL] ?: 15,
        )
    }

    suspend fun setThemeMode(mode: String) =
        store.edit { it[KEY_THEME_MODE] = mode }

    suspend fun setPlaybackSpeed(speed: Float) =
        store.edit { it[KEY_PLAYBACK_SPEED] = speed }

    suspend fun setPreferredSubtitleLanguage(lang: String) =
        store.edit { it[KEY_SUBTITLE_LANGUAGE] = lang }

    suspend fun setSubtitleFontSize(sizeSp: Float) =
        store.edit { it[KEY_SUBTITLE_FONT_SIZE] = sizeSp }

    suspend fun setSubtitlePosition(position: String) =
        store.edit { it[KEY_SUBTITLE_POSITION] = position }

    suspend fun setMaxCacheBytes(bytes: Long) =
        store.edit { it[KEY_MAX_CACHE_BYTES] = bytes }

    suspend fun setSkipInterval(seconds: Int) =
        store.edit { it[KEY_SKIP_INTERVAL] = seconds }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val KEY_SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
        val KEY_SUBTITLE_FONT_SIZE = floatPreferencesKey("subtitle_font_size")
        val KEY_SUBTITLE_POSITION = stringPreferencesKey("subtitle_position")
        val KEY_MAX_CACHE_BYTES = longPreferencesKey("max_cache_bytes")
        val KEY_SKIP_INTERVAL = intPreferencesKey("skip_interval")
    }
}
