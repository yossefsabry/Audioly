package com.audioly.app.extraction

import com.audioly.app.util.AppLogger
import com.audioly.shared.extraction.ExtractionResult
import com.audioly.shared.extraction.StreamInfo
import com.audioly.shared.extraction.SubtitleTrack
import com.audioly.shared.util.UrlValidator
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import com.audioly.app.network.AppHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo as NpStreamInfo
import java.io.IOException

/**
 * Extracts audio streams and subtitle tracks from YouTube videos.
 *
 * Two-tier extraction strategy:
 *  1. **NewPipe Extractor** (primary) — handles cipher, throttle, PoToken internally.
 *     Actively maintained by the community to keep up with YouTube changes.
 *  2. **InnerTube /player API** (fallback) — direct HTTP calls using multiple client
 *     configurations (IOS, embedded player, TV).
 *
 * Subtitle tracks are extracted from both paths when available.
 */
class YouTubeExtractor {

    // ── InnerTube client definitions ─────────────────────────────────────────

    private data class InnerTubeClient(
        val clientName: String,
        val clientNameId: String,
        val clientVersion: String,
        val userAgent: String,
        val androidSdkVersion: Int? = null,
        val deviceModel: String? = null,
        val clientScreen: String? = null,
        val embedUrl: String? = null,
    )

    private companion object {
        const val TAG = "YouTubeExtractor"

        const val INNERTUBE_URL =
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"

        val httpClient = AppHttpClient.base

        val CLIENTS = listOf(
            // 1. IOS client — returns non-throttled direct audio URLs
            InnerTubeClient(
                clientName = "IOS",
                clientNameId = "5",
                clientVersion = "19.29.1",
                deviceModel = "iPhone16,2",
                userAgent = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X; en_US)",
            ),
            // 2. Android embedded player — embed context bypasses some PoToken checks
            InnerTubeClient(
                clientName = "ANDROID_EMBEDDED_PLAYER",
                clientNameId = "55",
                clientVersion = "19.29.37",
                androidSdkVersion = 30,
                embedUrl = "https://www.youtube.com",
                userAgent = "com.google.android.youtube/19.29.37 (Linux; U; Android 11) gzip",
            ),
            // 3. TV HTML5 embed — historically not subject to PoToken
            InnerTubeClient(
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientNameId = "85",
                clientVersion = "2.0",
                clientScreen = "EMBED",
                embedUrl = "https://www.youtube.com",
                userAgent = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 5.0) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/2.1 Chrome/56.0.2924.0 TV Safari/537.36",
            ),
        )

        // WEB client — most reliable for returning caption/subtitle tracks (ASR + manual).
        // Not used for audio streams (often cipher-protected) but used as dedicated caption source.
        val WEB_CLIENT = InnerTubeClient(
            clientName = "WEB",
            clientNameId = "1",
            clientVersion = "2.20241001.00.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        )
    }

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun extract(url: String): ExtractionResult = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "Extraction started for: $url")

        val videoId = UrlValidator.extractVideoId(url)
            ?: return@withContext ExtractionResult.Failure.InvalidUrl(url).also {
                AppLogger.w(TAG, "Invalid URL: $url")
            }

        // 1. Try NewPipe Extractor (primary — handles cipher, throttle, PoToken)
        try {
            val result = extractWithNewPipe(videoId)
            if (result != null) {
                AppLogger.i(TAG, "NewPipe extraction succeeded for $videoId")
                return@withContext augmentWithCaptions(result, videoId)
            }
        } catch (e: AgeRestrictedContentException) {
            AppLogger.w(TAG, "NewPipe: age-restricted for $videoId")
            return@withContext ExtractionResult.Failure.AgeRestricted(videoId)
        } catch (e: ContentNotAvailableException) {
            AppLogger.w(TAG, "NewPipe: content not available for $videoId: ${e.message}")
            return@withContext ExtractionResult.Failure.VideoUnavailable(videoId)
        } catch (e: CancellationException) {
            throw e // Never swallow coroutine cancellation
        } catch (e: IOException) {
            AppLogger.w(TAG, "NewPipe network error for $videoId: ${e.message}")
            // Fall through to InnerTube
        } catch (e: Throwable) {
            AppLogger.w(
                TAG,
                "NewPipe error for $videoId: ${e.javaClass.simpleName}: ${e.message}",
            )
            // Fall through to InnerTube
        }

        // 2. Fall back to InnerTube direct API
        AppLogger.i(TAG, "Falling back to InnerTube for $videoId")
        for (client in CLIENTS) {
            AppLogger.i(TAG, "Trying InnerTube client ${client.clientName} for $videoId")
            try {
                val result = tryClient(videoId, client)
                if (result != null) {
                    AppLogger.i(TAG, "InnerTube ${client.clientName} succeeded for $videoId")
                    return@withContext augmentWithCaptions(result, videoId)
                }
            } catch (e: CancellationException) {
                throw e // Never swallow coroutine cancellation
            } catch (e: IOException) {
                AppLogger.w(TAG, "InnerTube ${client.clientName} network error for $videoId: ${e.message}")
            } catch (e: Throwable) {
                AppLogger.w(
                    TAG,
                    "InnerTube ${client.clientName} error for $videoId: ${e.javaClass.simpleName}: ${e.message}",
                )
            }
        }

        AppLogger.e(TAG, "All extraction methods exhausted for $videoId")
        return@withContext ExtractionResult.Failure.ExtractionFailed(
            Exception("All extraction methods failed for $videoId"),
        )
    }

    // ── Dedicated subtitle fetch ────────────────────────────────────────────────

    /**
     * Public API: fetches only subtitle/caption tracks for a given video ID.
     *
     * Tries NewPipe first (most reliable for ASR captions), then falls back
     * to InnerTube clients. Used when playing from cache without cached subtitles.
     */
    suspend fun fetchSubtitles(videoId: String): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "fetchSubtitles: fetching tracks for $videoId")

        // 1. Try NewPipe — most reliable for ASR + manual subtitles
        try {
            val url = UrlValidator.canonicalUrl(videoId)
            val npInfo = NpStreamInfo.getInfo(url)
            val tracks = SubtitleExtractor.extract(npInfo)
            if (tracks.isNotEmpty()) {
                AppLogger.i(TAG, "fetchSubtitles: NewPipe returned ${tracks.size} tracks for $videoId")
                return@withContext tracks
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLogger.w(TAG, "fetchSubtitles: NewPipe failed for $videoId: ${e.message}")
        }

        // 2. Fall back to InnerTube clients
        for (client in CLIENTS + WEB_CLIENT) {
            val tracks = fetchCaptionsFromClient(videoId, client)
            if (tracks.isNotEmpty()) return@withContext tracks
        }

        AppLogger.i(TAG, "fetchSubtitles: no tracks found for $videoId")
        emptyList()
    }

    /**
     * Fetches subtitle/caption tracks using a specific InnerTube client.
     */
    private fun fetchCaptionsFromClient(videoId: String, client: InnerTubeClient): List<SubtitleTrack> {
        try {
            val requestBody = buildRequestBody(videoId, client)
            val request = Request.Builder()
                .url(INNERTUBE_URL)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .header("User-Agent", client.userAgent)
                .header("X-Youtube-Client-Name", client.clientNameId)
                .header("X-Youtube-Client-Version", client.clientVersion)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(TAG, "${client.clientName} caption fetch HTTP ${response.code} for $videoId")
                    return emptyList()
                }
                val bodyStr = response.body?.string() ?: return emptyList()
                val json = JSONObject(bodyStr)
                val tracks = SubtitleExtractor.extractFromInnerTubeJson(json)
                if (tracks.isNotEmpty()) {
                    AppLogger.i(TAG, "${client.clientName} caption fetch for $videoId — got ${tracks.size} tracks")
                }
                return tracks
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLogger.w(TAG, "${client.clientName} caption fetch failed for $videoId: ${e.message}")
            return emptyList()
        }
    }

    /**
     * If extraction succeeded but returned 0 subtitle tracks, try fetching
     * captions separately using multiple InnerTube clients.
     */
    private fun augmentWithCaptions(result: ExtractionResult, videoId: String): ExtractionResult {
        if (result !is ExtractionResult.Success) return result
        if (result.streamInfo.subtitleTracks.isNotEmpty()) return result

        AppLogger.i(TAG, "No subtitles from primary extraction — trying dedicated caption fetch")
        for (client in CLIENTS + WEB_CLIENT) {
            val captions = fetchCaptionsFromClient(videoId, client)
            if (captions.isNotEmpty()) {
                return ExtractionResult.Success(
                    result.streamInfo.copy(subtitleTracks = captions)
                )
            }
        }
        AppLogger.i(TAG, "All caption fetch methods returned no tracks for $videoId")
        return result
    }

    // ── NewPipe extraction (primary) ──────────────────────────────────────────

    /**
     * Extracts audio + subtitles using NewPipe Extractor library.
     * NewPipe handles cipher decryption, throttle bypass, and PoToken internally.
     *
     * Returns [ExtractionResult.Success] or null if no audio streams found.
     * Throws NewPipe exceptions for definitive failures (age-restricted, unavailable).
     */
    private fun extractWithNewPipe(videoId: String): ExtractionResult? {
        val url = UrlValidator.canonicalUrl(videoId)
        AppLogger.i(TAG, "NewPipe: extracting $videoId")

        val npInfo = NpStreamInfo.getInfo(url)

        // Get best audio stream — prefer ORIGINAL track over dubbed/descriptive
        val audioStreams = npInfo.audioStreams
        if (audioStreams.isNullOrEmpty()) {
            AppLogger.w(TAG, "NewPipe: no audio streams for $videoId")
            return null
        }

        // Prefer original-language track; fall back to any track if none is marked ORIGINAL
        val originalStreams = audioStreams.filter { stream ->
            stream.audioTrackType == AudioTrackType.ORIGINAL ||
                stream.audioTrackType == null  // null means single-track video (inherently original)
        }
        val candidateStreams = if (originalStreams.isNotEmpty()) originalStreams else audioStreams
        val bestAudio = candidateStreams
            .filter { !it.content.isNullOrBlank() }
            .maxByOrNull { it.averageBitrate }
            ?: return null
        val audioUrl = bestAudio.content
        if (audioUrl.isNullOrBlank()) {
            AppLogger.w(TAG, "NewPipe: empty audio URL for $videoId")
            return null
        }

        AppLogger.d(TAG, "NewPipe: selected audio track type=${bestAudio.audioTrackType}, " +
            "locale=${bestAudio.audioLocale}, bitrate=${bestAudio.averageBitrate}")

        // Get subtitles
        val subtitles = try {
            SubtitleExtractor.extract(npInfo)
        } catch (e: Exception) {
            AppLogger.w(TAG, "NewPipe: subtitle extraction failed: ${e.message}")
            emptyList()
        }

        // Get best thumbnail
        val thumbnail = try {
            npInfo.thumbnails
                .maxByOrNull { it.height * it.width }
                ?.url
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        } catch (e: Exception) {
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        }

        AppLogger.i(TAG, "NewPipe: extracted $videoId — ${subtitles.size} subtitle tracks")

        return ExtractionResult.Success(
            StreamInfo(
                videoId = videoId,
                title = npInfo.name ?: "Unknown",
                uploader = npInfo.uploaderName ?: "Unknown",
                thumbnailUrl = thumbnail,
                durationSeconds = npInfo.duration,
                audioStreamUrl = audioUrl,
                subtitleTracks = subtitles,
            )
        )
    }

    // ── InnerTube extraction (fallback) ───────────────────────────────────────

    /**
     * Attempt extraction using one InnerTube client configuration.
     *
     * Returns:
     *  - [ExtractionResult.Success] on success
     *  - [ExtractionResult.Failure] for definitive errors (age-restricted, unavailable)
     *  - `null` when this client simply didn't work — caller should try the next one
     */
    private fun tryClient(videoId: String, client: InnerTubeClient): ExtractionResult? {
        val requestBody = buildRequestBody(videoId, client)
        val request = Request.Builder()
            .url(INNERTUBE_URL)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .header("User-Agent", client.userAgent)
            .header("X-Youtube-Client-Name", client.clientNameId)
            .header("X-Youtube-Client-Version", client.clientVersion)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string()
                ?: run {
                    AppLogger.w(TAG, "Empty body from InnerTube ${client.clientName}")
                    return null
                }

            if (!response.isSuccessful) {
                AppLogger.w(TAG, "InnerTube ${client.clientName} HTTP ${response.code} for $videoId")
                return null
            }

            val json = JSONObject(bodyStr)

            // ── Check playability ─────────────────────────────────────────────
            val playability = json.optJSONObject("playabilityStatus")
            val status = playability?.optString("status") ?: "UNKNOWN"
            AppLogger.d(TAG, "InnerTube ${client.clientName} playabilityStatus=$status for $videoId")

            when (status) {
                "OK" -> { /* proceed */ }
                "LOGIN_REQUIRED" -> {
                    val reason = playability?.optString("reason") ?: ""
                    return if (reason.contains("age", ignoreCase = true)) {
                        ExtractionResult.Failure.AgeRestricted(videoId)
                    } else {
                        // LOGIN_REQUIRED without age = bot detection; try next client
                        AppLogger.w(
                            TAG,
                            "InnerTube ${client.clientName}: LOGIN_REQUIRED (non-age) for $videoId, trying next",
                        )
                        null
                    }
                }
                "UNPLAYABLE", "ERROR", "CONTENT_CHECK_REQUIRED" -> {
                    AppLogger.w(TAG, "InnerTube ${client.clientName}: $status for $videoId, trying next client")
                    // Don't short-circuit — other clients may succeed where this one didn't
                    return null
                }
                else -> {
                    AppLogger.w(TAG, "InnerTube ${client.clientName}: unexpected status=$status, trying next client")
                    return null
                }
            }

            val streaming = json.optJSONObject("streamingData")
                ?: run {
                    AppLogger.w(TAG, "No streamingData from ${client.clientName} for $videoId")
                    return null
                }

            // ── Extract metadata ──────────────────────────────────────────────
            val details = json.optJSONObject("videoDetails")
            val title = details?.optString("title")?.takeIf { it.isNotBlank() } ?: "Unknown"
            val uploader = details?.optString("author")?.takeIf { it.isNotBlank() } ?: "Unknown"
            val durationSeconds = details?.optString("lengthSeconds")?.toLongOrNull() ?: 0L
            val thumbnail = pickBestThumbnail(details, videoId)

            // ── Extract subtitle tracks from captions ─────────────────────────
            val subtitleTracks = SubtitleExtractor.extractFromInnerTubeJson(json)

            // ── Prefer HLS manifest (ExoPlayer handles natively, no throttle) ─
            val hlsUrl = streaming.optString("hlsManifestUrl", "").takeIf { it.isNotBlank() }
            if (hlsUrl != null) {
                AppLogger.i(TAG, "Using HLS manifest for $videoId via ${client.clientName}")
                return ExtractionResult.Success(
                    StreamInfo(
                        videoId = videoId,
                        title = title,
                        uploader = uploader,
                        thumbnailUrl = thumbnail,
                        durationSeconds = durationSeconds,
                        audioStreamUrl = hlsUrl,
                        subtitleTracks = subtitleTracks,
                    )
                )
            }

            // ── Fall back to best direct audio stream ─────────────────────────
            val audioUrl = pickBestAudioUrl(streaming, client.clientName, videoId)
                ?: return null

            AppLogger.i(TAG, "Using direct audio for $videoId via ${client.clientName}")
            return ExtractionResult.Success(
                StreamInfo(
                    videoId = videoId,
                    title = title,
                    uploader = uploader,
                    thumbnailUrl = thumbnail,
                    durationSeconds = durationSeconds,
                    audioStreamUrl = audioUrl,
                    subtitleTracks = subtitleTracks,
                )
            )
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildRequestBody(videoId: String, client: InnerTubeClient): String {
        val clientObj = JSONObject().apply {
            put("clientName", client.clientName)
            put("clientVersion", client.clientVersion)
            put("hl", "en")
            put("gl", "US")
            client.deviceModel?.let { put("deviceModel", it) }
            client.androidSdkVersion?.let { put("androidSdkVersion", it) }
            client.clientScreen?.let { put("clientScreen", it) }
        }

        val contextObj = JSONObject().apply {
            put("client", clientObj)
            client.embedUrl?.let {
                put("thirdParty", JSONObject().put("embedUrl", it))
            }
        }

        return JSONObject().apply {
            put("videoId", videoId)
            put("context", contextObj)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString()
    }

    /**
     * Picks the highest-bitrate audio-only format from adaptiveFormats.
     * Prefers the original/default audio track over dubbed alternatives.
     * Skips any format that uses signatureCipher/cipher (requires JS decoding).
     */
    private fun pickBestAudioUrl(
        streaming: JSONObject,
        clientName: String,
        videoId: String,
    ): String? {
        val adaptiveFormats = streaming.optJSONArray("adaptiveFormats")
            ?: run {
                AppLogger.w(TAG, "No adaptiveFormats from $clientName for $videoId")
                return null
            }

        var bestUrl = ""
        var bestBitrate = -1
        var bestDefaultUrl = ""
        var bestDefaultBitrate = -1

        for (i in 0 until adaptiveFormats.length()) {
            val fmt = adaptiveFormats.optJSONObject(i) ?: continue
            val mimeType = fmt.optString("mimeType", "")

            // Audio-only streams have mimeType starting with "audio/"
            if (!mimeType.startsWith("audio/")) continue

            // Skip cipher-protected formats (we have no JS engine to decode them)
            val streamUrl = fmt.optString("url", "")
            if (streamUrl.isBlank()) {
                val hasCipher = fmt.has("signatureCipher") || fmt.has("cipher")
                if (hasCipher) {
                    AppLogger.d(TAG, "Skipping cipher-protected audio itag=${fmt.optInt("itag")} from $clientName")
                }
                continue
            }

            val bitrate = fmt.optInt("bitrate", 0)

            // Check if this is the default/original audio track
            val audioTrack = fmt.optJSONObject("audioTrack")
            val isDefault = audioTrack?.optBoolean("audioIsDefault", false) ?: true  // true if no audioTrack = single track

            if (isDefault && bitrate > bestDefaultBitrate) {
                bestDefaultBitrate = bitrate
                bestDefaultUrl = streamUrl
            }
            if (bitrate > bestBitrate) {
                bestBitrate = bitrate
                bestUrl = streamUrl
            }
        }

        // Prefer default/original track; fall back to highest bitrate overall
        val selectedUrl = bestDefaultUrl.ifBlank { bestUrl }
        val selectedBitrate = if (bestDefaultUrl.isNotBlank()) bestDefaultBitrate else bestBitrate

        if (selectedUrl.isBlank()) {
            AppLogger.w(TAG, "No usable audio streams from $clientName for $videoId")
            return null
        }

        AppLogger.d(TAG, "Best audio: bitrate=$selectedBitrate, isDefault=${bestDefaultUrl.isNotBlank()} from $clientName")
        return selectedUrl
    }

    private fun pickBestThumbnail(details: JSONObject?, videoId: String): String {
        if (details == null) return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        val thumbs = details.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        var bestUrl = ""
        var bestArea = -1
        for (i in 0 until thumbs.length()) {
            val t = thumbs.optJSONObject(i) ?: continue
            val area = t.optInt("width", 0) * t.optInt("height", 0)
            if (area > bestArea) {
                bestArea = area
                bestUrl = t.optString("url", "")
            }
        }

        return bestUrl.takeIf { it.isNotBlank() }
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }
}
