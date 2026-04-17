package com.audioly.app.data.preferences

/** Immutable snapshot of all user preferences. */
data class UserPreferences(
    /** "system", "light", or "dark" */
    val themeMode: String = THEME_SYSTEM,
    /** Playback speed multiplier, e.g. 1.0f, 1.5f, 2.0f */
    val playbackSpeed: Float = 1.0f,
    /** BCP-47 language code preferred for subtitles, empty = none. */
    val preferredSubtitleLanguage: String = "",
    /** Subtitle font size in sp. */
    val subtitleFontSizeSp: Float = 16f,
    /** Subtitle vertical position: "bottom", "middle", "top" */
    val subtitlePosition: String = SUBTITLE_BOTTOM,
    /** Maximum ExoPlayer SimpleCache size in bytes. */
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
