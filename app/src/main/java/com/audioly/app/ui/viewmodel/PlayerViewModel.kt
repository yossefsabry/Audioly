package com.audioly.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioly.app.data.cache.DownloadState
import com.audioly.app.data.cache.SubtitleCacheManager
import com.audioly.app.data.cache.TrackDownloadManager
import com.audioly.shared.data.preferences.UserPreferences
import com.audioly.app.data.preferences.UserPreferencesRepository
import com.audioly.app.network.AppHttpClient
import com.audioly.app.player.Json3Parser
import com.audioly.shared.player.PlayerRepository
import com.audioly.shared.player.QueueItem
import com.audioly.shared.player.RepeatMode
import com.audioly.shared.player.SubtitleCue
import com.audioly.shared.player.SubtitleManager
import com.audioly.app.player.SubtitleTranslator
import com.audioly.shared.player.VttParser
import com.audioly.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * ViewModel for PlayerScreen. Manages subtitle loading/parsing, speed/subtitle
 * menu state, and delegates playback commands to PlayerRepository.
 */
class PlayerViewModel(
    val playerRepository: PlayerRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val subtitleCacheManager: SubtitleCacheManager,
    private val youTubeExtractor: com.audioly.app.extraction.YouTubeExtractor? = null,
    private val trackRepository: com.audioly.app.data.repository.TrackRepository? = null,
    private val trackDownloadManager: TrackDownloadManager? = null,
) : ViewModel() {

    val playerState = playerRepository.state
    val subtitleTracks = playerRepository.subtitleTracks
    val subtitleContent = playerRepository.subtitleContent

    val prefs: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    // Queue state (delegated from PlayerRepository)
    val queue = playerRepository.queue
    val queueIndex = playerRepository.queueIndex
    val repeatMode = playerRepository.repeatMode
    val shuffleEnabled = playerRepository.shuffleEnabled

    // Subtitle management per video
    private var subtitleManager = SubtitleManager()

    private val _subtitleCues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    val subtitleCues: StateFlow<List<SubtitleCue>> = _subtitleCues.asStateFlow()

    private val _activeCueIndex = MutableStateFlow(-1)
    val activeCueIndex: StateFlow<Int> = _activeCueIndex.asStateFlow()

    /**
     * Set to true when the user explicitly selects "Off" for subtitles.
     * Cleared on video change so auto-select fires for the new video.
     */
    private var userDisabledSubtitles = false

    private var currentVideoId: String? = null

    /** Tracks in-flight subtitle downloads to prevent duplicate concurrent requests. */
    private val downloadingLangs = mutableSetOf<String>()

    val showSubtitles: StateFlow<Boolean> = combine(
        _subtitleCues,
        playerState,
    ) { cues, state ->
        cues.isNotEmpty() && state.selectedSubtitleLanguage.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True when subtitles are available (tracks extracted) even if not yet downloaded. */
    val hasSubtitleTracks: StateFlow<Boolean> = subtitleTracks
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ─── Download state ──────────────────────────────────────────────────────

    /** Reactive download state for the currently playing video. */
    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    val downloadState: StateFlow<DownloadState> = playerState
        .map { it.videoId }
        .distinctUntilChanged()
        .flatMapLatest { videoId ->
            if (videoId != null && trackDownloadManager != null) {
                trackDownloadManager.stateFlow(videoId)
            } else {
                flowOf(DownloadState.Idle)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadState.Idle)

    init {
        // Watch for video changes — reset subtitle manager and user preference
        viewModelScope.launch {
            playerState
                .map { it.videoId }
                .distinctUntilChanged()
                .collect { videoId ->
                    if (videoId != null && videoId != currentVideoId) {
                        currentVideoId = videoId
                        userDisabledSubtitles = false
                        subtitleManager = SubtitleManager()
                        _subtitleCues.value = emptyList()
                        _activeCueIndex.value = -1
                    }
                }
        }

        // Update active cue index on position changes (lightweight — no I/O)
        viewModelScope.launch {
            playerState.collect { state ->
                val idx = subtitleManager.activeIndex(state.positionMs)
                if (idx != _activeCueIndex.value) {
                    _activeCueIndex.value = idx
                    playerRepository.setSubtitleIndex(idx)
                }
            }
        }

        // Auto-select subtitle language when tracks first arrive for a new video.
        // Uses distinctUntilChanged to only fire when tracks or language actually change,
        // preventing the constant re-triggering that was overriding user's "Off" choice.
        viewModelScope.launch {
            combine(
                subtitleTracks,
                playerState.map { it.videoId to it.selectedSubtitleLanguage }.distinctUntilChanged(),
                prefs,
            ) { tracks, (_, lang), prefs ->
                Triple(tracks, lang, prefs)
            }.distinctUntilChanged()
                .collect { (tracks, lang, prefs) ->
                    if (tracks.isNotEmpty() && lang.isEmpty() && !userDisabledSubtitles) {
                        val preferred = prefs.preferredSubtitleLanguage.ifEmpty { "en" }
                        // Match exact code first, then prefix (e.g. "en" matches "en-US")
                        val match = tracks.firstOrNull { it.languageCode == preferred }
                            ?: tracks.firstOrNull { it.languageCode.startsWith(preferred) }
                        playerRepository.setSubtitleLanguage(
                            match?.languageCode ?: tracks.first().languageCode
                        )
                    }
                }
        }

        // Parse VTT when content or selected language changes.
        // Use distinctUntilChanged on the language to avoid re-parsing on every position tick.
        viewModelScope.launch {
            combine(
                playerState.map { it.selectedSubtitleLanguage }.distinctUntilChanged(),
                subtitleContent,
            ) { lang, contentMap ->
                lang to contentMap
            }.collectLatest { (lang, contentMap) ->
                val vtt = contentMap[lang]
                if (vtt != null) {
                    val cues = withContext(Dispatchers.Default) { VttParser.parse(vtt) }
                    _subtitleCues.value = cues
                    subtitleManager.load(cues)
                } else {
                    _subtitleCues.value = emptyList()
                    subtitleManager.load(emptyList())
                }
            }
        }

        // Download VTT when selected language changes OR when tracks get updated URLs.
        // Combines both (videoId, lang) and subtitleTracks so that when fresh tracks
        // replace cached-with-empty-URL tracks, the download is re-attempted.
        // NOTE: Even tracks with empty URLs are passed through — downloadAndCacheSubtitle
        // checks disk cache first, so cached content loads immediately without a URL.
        viewModelScope.launch {
            combine(
                playerState.map { state -> state.videoId to state.selectedSubtitleLanguage }.distinctUntilChanged(),
                subtitleTracks,
            ) { (videoId, lang), tracks ->
                Triple(videoId, lang, tracks)
            }.collectLatest { (videoId, lang, tracks) ->
                if (lang.isBlank() || videoId == null) return@collectLatest
                if (subtitleContent.value.containsKey(lang)) return@collectLatest

                val track = tracks.firstOrNull { it.languageCode == lang }
                    ?: return@collectLatest

                downloadAndCacheSubtitle(videoId, lang, track)
            }
        }

        // Eagerly pre-fetch the first subtitle track when tracks arrive (parallel with audio).
        viewModelScope.launch {
            combine(
                subtitleTracks,
                playerState.map { it.videoId }.distinctUntilChanged(),
            ) { tracks, videoId ->
                tracks to videoId
            }.distinctUntilChanged()
                .collect { (tracks, videoId) ->
                    if (tracks.isEmpty() || videoId == null) return@collect
                    val firstTrack = tracks.first()
                    if (subtitleContent.value.containsKey(firstTrack.languageCode)) return@collect
                    // Pre-fetch in background so it's ready when auto-select picks it
                    downloadAndCacheSubtitle(videoId, firstTrack.languageCode, firstTrack)
                }
        }

        // Handle queue advance requests (tracks that need extraction)
        viewModelScope.launch {
            playerRepository.queueAdvanceRequests.collectLatest { item ->
                handleQueueExtraction(item)
            }
        }

        // Periodically save playback position for resume (only when playing)
        viewModelScope.launch {
            while (true) {
                delay(POSITION_SAVE_INTERVAL_MS)
                if (playerState.value.isPlaying) {
                    saveCurrentPosition()
                }
            }
        }
    }

    // ─── Subtitle download helper ────────────────────────────────────────────

    private suspend fun downloadAndCacheSubtitle(
        videoId: String,
        lang: String,
        track: com.audioly.shared.extraction.SubtitleTrack,
    ) {
        // Deduplicate: skip if already downloading this language
        synchronized(downloadingLangs) {
            if (!downloadingLangs.add(lang)) return
        }
        try {
            // For auto-translated tracks, skip disk cache and always download fresh.
            // Reason: (1) older app versions cached untranslated content under "en" key,
            // (2) YouTube translations can improve over time, so fresh is preferred.
            if (!track.isAutoTranslated) {
                val cached = try {
                    withContext(Dispatchers.IO) { subtitleCacheManager.load(videoId, lang) }
                } catch (_: Exception) { null }

                if (cached != null) {
                    playerRepository.addSubtitleContent(lang, cached)
                    return
                }
            }

            val vttContent: String? = if (track.isAutoTranslated) {
                downloadAutoTranslated(videoId, lang, track)
            } else {
                downloadRegularSubtitle(track)
            }

            if (vttContent == null) {
                AppLogger.w(TAG, "Subtitle unavailable for $lang ($videoId)")
                return
            }

            // Cache for future use (always as VTT regardless of source format)
            try {
                withContext(Dispatchers.IO) {
                    subtitleCacheManager.save(
                        videoId = videoId,
                        languageCode = lang,
                        languageName = track.languageName,
                        format = "vtt",
                        isAutoGenerated = track.isAutoGenerated,
                        content = vttContent,
                    )
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to cache subtitle: ${e.message}")
            }

            playerRepository.addSubtitleContent(lang, vttContent)
        } finally {
            synchronized(downloadingLangs) { downloadingLangs.remove(lang) }
        }
    }

    /** Downloads a regular (non-translated) subtitle track as VTT. */
    private suspend fun downloadRegularSubtitle(
        track: com.audioly.shared.extraction.SubtitleTrack,
    ): String? {
        if (track.url.isBlank()) {
            AppLogger.w(TAG, "Subtitle track has no URL for ${track.languageCode}")
            return null
        }
        AppLogger.d(TAG, "Downloading subtitle: ${track.languageCode} — URL: ${track.url}")
        return downloadVttContent(track.url)
    }

    /**
     * Downloads an auto-translated subtitle with two-phase fallback:
     *   1. Try YouTube's `&fmt=json3&tlang=en` (server-side translation)
     *   2. If that fails (429 / empty), download the **source language** VTT
     *      and translate client-side using Google Translate.
     */
    private suspend fun downloadAutoTranslated(
        videoId: String,
        lang: String,
        track: com.audioly.shared.extraction.SubtitleTrack,
    ): String? {
        // ── Phase 1: YouTube server-side translation (json3 + tlang) ─────────
        if (track.url.isNotBlank()) {
            AppLogger.d(TAG, "Phase 1: YouTube json3 translation for $lang ($videoId)")
            val rawJson3 = downloadVttContent(track.url)
            if (rawJson3 != null) {
                val vtt = Json3Parser.toVtt(rawJson3)
                if (vtt != null) {
                    AppLogger.d(TAG, "Phase 1 succeeded: YouTube json3 → VTT for $lang")
                    return vtt
                }
                AppLogger.w(TAG, "Phase 1: json3 parse failed, snippet: ${rawJson3.take(200)}")
            } else {
                AppLogger.d(TAG, "Phase 1 failed (429/network) — falling back to client-side translation")
            }
        }

        // ── Phase 2: Client-side translation via Google Translate ────────────
        val sourceLang = track.sourceLanguageCode
        if (sourceLang == null) {
            AppLogger.w(TAG, "Phase 2: no sourceLanguageCode for $lang — cannot translate")
            return null
        }

        AppLogger.d(TAG, "Phase 2: Google Translate ($sourceLang → $lang) for $videoId")

        // Get source-language VTT — try in-memory first, then download
        val sourceVtt = subtitleContent.value[sourceLang]
            ?: run {
                val sourceTrack = subtitleTracks.value.firstOrNull { it.languageCode == sourceLang }
                if (sourceTrack != null && sourceTrack.url.isNotBlank()) {
                    AppLogger.d(TAG, "Phase 2: downloading source VTT ($sourceLang)")
                    downloadVttContent(sourceTrack.url)
                } else null
            }

        if (sourceVtt == null) {
            AppLogger.w(TAG, "Phase 2: could not obtain source VTT for $sourceLang")
            return null
        }

        val translatedVtt = SubtitleTranslator.translateVtt(sourceVtt, sourceLang, lang)
        if (translatedVtt != null) {
            AppLogger.d(TAG, "Phase 2 succeeded: Google Translate $sourceLang → $lang")
        } else {
            AppLogger.w(TAG, "Phase 2 failed: Google Translate returned null")
        }
        return translatedVtt
    }

    // ─── Playback commands (delegate to PlayerRepository) ─────────────────────

    fun togglePlayPause() = playerRepository.togglePlayPause()
    fun seekTo(positionMs: Long) = playerRepository.seekTo(positionMs)
    fun skipForward(intervalMs: Long) = playerRepository.skipForward(intervalMs)
    fun skipBack(intervalMs: Long) = playerRepository.skipBack(intervalMs)
    fun setSpeed(speed: Float) = playerRepository.setSpeed(speed)

    /**
     * Set subtitle language. Empty string means "Off".
     * Tracks user's explicit "Off" choice to prevent auto-select from overriding it.
     */
    fun setSubtitleLanguage(languageCode: String) {
        if (languageCode.isEmpty()) {
            userDisabledSubtitles = true
        }
        playerRepository.setSubtitleLanguage(languageCode)
    }

    // ─── Queue commands ──────────────────────────────────────────────────────

    fun skipToNext() = playerRepository.skipToNext()
    fun skipToPrevious() = playerRepository.skipToPrevious()
    fun toggleRepeatMode() = playerRepository.toggleRepeatMode()
    fun toggleShuffle() = playerRepository.toggleShuffle()
    fun removeFromQueue(index: Int) = playerRepository.removeFromQueue(index)

    // ─── Download commands ───────────────────────────────────────────────────

    /** Start downloading the current track's audio + preferred subtitle for offline use. */
    fun startDownload() {
        val videoId = playerState.value.videoId ?: return
        val audioUrl = playerRepository.currentAudioUrl ?: return
        val tracks = subtitleTracks.value
        trackDownloadManager?.startDownload(videoId, audioUrl, tracks)
    }

    /** Cancel in-progress download for the current track. */
    fun cancelDownload() {
        val videoId = playerState.value.videoId ?: return
        trackDownloadManager?.cancelDownload(videoId)
    }

    /**
     * Save current playback position to DB for resume.
     * Skips save if near start (< 5s) or near end (last 5% or < 5s remaining).
     */
    private suspend fun saveCurrentPosition() {
        val repo = trackRepository ?: return
        val state = playerState.value
        val videoId = state.videoId ?: return
        val posMs = state.positionMs
        val durMs = state.durationMs
        // Don't save trivial positions or near-end positions (treat as "completed")
        if (posMs < 5_000L) {
            repo.saveLastPosition(videoId, 0L)
            return
        }
        if (durMs > 0 && (durMs - posMs) < (durMs * 0.05).toLong().coerceAtLeast(5_000L)) {
            repo.saveLastPosition(videoId, 0L)
            return
        }
        try {
            repo.saveLastPosition(videoId, posMs)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to save position: ${e.message}")
        }
    }

    /** Save position when leaving the player. Call from onCleared or screen exit. */
    override fun onCleared() {
        // Fire-and-forget: save final position
        val repo = trackRepository
        val state = playerState.value
        val videoId = state.videoId
        if (repo != null && videoId != null && state.positionMs > 5_000L) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
                try {
                    val durMs = state.durationMs
                    val posMs = state.positionMs
                    val nearEnd = durMs > 0 && (durMs - posMs) < (durMs * 0.05).toLong().coerceAtLeast(5_000L)
                    repo.saveLastPosition(videoId, if (nearEnd) 0L else posMs)
                } catch (_: Exception) { /* best effort */ }
            }
        }
        super.onCleared()
    }

    private suspend fun handleQueueExtraction(item: QueueItem) {
        val extractor = youTubeExtractor ?: return
        try {
            val url = "https://www.youtube.com/watch?v=${item.videoId}"
            AppLogger.i(TAG, "Queue advance: extracting ${item.videoId}")
            val result = extractor.extract(url)
            if (result is com.audioly.shared.extraction.ExtractionResult.Success) {
                val info = result.streamInfo
                try {
                    trackRepository?.upsertFromExtraction(info)
                    trackRepository?.setAudioStreamUrl(info.videoId, info.audioStreamUrl)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to save track to DB", e)
                }
                playerRepository.clearSubtitles()
                playerRepository.setSubtitleTracks(info.subtitleTracks)
                playerRepository.load(
                    audioUrl = info.audioStreamUrl,
                    videoId = info.videoId,
                    title = info.title,
                    uploader = info.uploader,
                    thumbnailUrl = info.thumbnailUrl,
                    durationMs = info.durationSeconds * 1000L,
                )
            } else {
                AppLogger.w(TAG, "Queue advance extraction failed for ${item.videoId}")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Queue advance error", e)
        }
    }

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val POSITION_SAVE_INTERVAL_MS = 10_000L
        private const val MAX_DOWNLOAD_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 2_000L

        private const val SUBTITLE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /**
         * Downloads subtitle content from [url] with retry + exponential backoff
         * for HTTP 429 (Too Many Requests) responses. YouTube rate-limits the
         * timedtext API, especially auto-translated requests.
         */
        private suspend fun downloadVttContent(url: String): String? {
            var attempt = 0
            var backoffMs = INITIAL_BACKOFF_MS
            while (attempt < MAX_DOWNLOAD_RETRIES) {
                val result = singleDownload(url)
                when {
                    result.first != null -> return result.first       // success
                    result.second == 429 -> {                          // rate-limited
                        attempt++
                        if (attempt < MAX_DOWNLOAD_RETRIES) {
                            AppLogger.d(TAG, "Subtitle 429 — retry $attempt/${MAX_DOWNLOAD_RETRIES} after ${backoffMs}ms")
                            delay(backoffMs)
                            backoffMs *= 2
                        }
                    }
                    else -> return null                                // non-retryable error
                }
            }
            AppLogger.w(TAG, "Subtitle download exhausted $MAX_DOWNLOAD_RETRIES retries for: $url")
            return null
        }

        /** Single HTTP request. Returns (body, httpCode). body is null on failure. */
        private suspend fun singleDownload(url: String): Pair<String?, Int> =
            suspendCancellableCoroutine { cont ->
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", SUBTITLE_USER_AGENT)
                    .build()
                val call = AppHttpClient.subtitleClient.newCall(request)

                cont.invokeOnCancellation { call.cancel() }

                call.enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: IOException) {
                        if (cont.isCancelled) return
                        AppLogger.w(TAG, "Subtitle download failed: ${e.message} — URL: $url")
                        cont.resumeWith(Result.success(null to 0))
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        if (cont.isCancelled) {
                            response.close()
                            return
                        }
                        val code = response.code
                        val body = try {
                            response.use {
                                if (it.isSuccessful) {
                                    it.body?.string()?.takeIf { s -> s.isNotBlank() }
                                } else {
                                    if (code != 429) {
                                        AppLogger.w(TAG, "Subtitle HTTP $code for URL: $url")
                                    }
                                    null
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "Subtitle response read failed: ${e.message} — URL: $url")
                            null
                        }
                        cont.resumeWith(Result.success(body to code))
                    }
                })
            }
    }
}
