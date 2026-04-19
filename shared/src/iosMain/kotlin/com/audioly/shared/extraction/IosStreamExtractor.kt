package com.audioly.shared.extraction

import com.audioly.shared.network.SharedHttpClient
import com.audioly.shared.util.AppLogger
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*

/**
 * iOS YouTube extraction using InnerTube API directly (via Ktor).
 * No NewPipeExtractor available on iOS — uses YouTube's internal API.
 */
class IosStreamExtractor : StreamExtractorService {

    private val client = SharedHttpClient.instance
    private val json = SharedHttpClient.json

    override suspend fun extractStream(videoIdOrUrl: String): ExtractionResult {
        val videoId = com.audioly.shared.util.UrlValidator.extractVideoId(videoIdOrUrl)
            ?: videoIdOrUrl.takeIf { it.length == 11 }
            ?: return ExtractionResult.Failure.InvalidUrl(videoIdOrUrl)

        return try {
            val streamInfo = fetchViaInnerTube(videoId)
            if (streamInfo != null) {
                ExtractionResult.Success(streamInfo)
            } else {
                ExtractionResult.Failure.ExtractionFailed(
                    RuntimeException("Failed to extract stream for $videoId")
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Extraction failed for $videoId", e)
            ExtractionResult.Failure.NetworkError(e)
        }
    }

    override suspend fun search(query: String, limit: Int): List<SearchResult> {
        return try {
            val body = buildJsonObject {
                put("query", query)
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20231219.04.00")
                        put("hl", "en")
                        put("gl", "US")
                    }
                }
            }

            val response = client.post("https://www.youtube.com/youtubei/v1/search") {
                header("Content-Type", "application/json")
                setBody(body.toString())
            }

            parseSearchResults(response.bodyAsText(), limit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Search failed for: $query", e)
            emptyList()
        }
    }

    override suspend fun searchSuggestions(query: String): List<String> {
        return try {
            val response = client.get(
                "https://suggestqueries-clients6.youtube.com/complete/search"
            ) {
                parameter("client", "youtube")
                parameter("ds", "yt")
                parameter("q", query)
            }
            // Parse JSONP response
            val text = response.bodyAsText()
            val jsonStr = text.substringAfter("(").substringBeforeLast(")")
            val arr = json.parseToJsonElement(jsonStr).jsonArray
            arr.getOrNull(1)?.jsonArray?.map { it.jsonArray[0].jsonPrimitive.content } ?: emptyList()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Suggestions failed", e)
            emptyList()
        }
    }

    // ─── InnerTube API ───────────────────────────────────────────────────────

    private suspend fun fetchViaInnerTube(videoId: String): StreamInfo? {
        // Try IOS client first (often has direct URLs)
        val clients = listOf(
            InnerTubeClient("IOS", "19.29.1", "com.google.ios.youtube"),
            InnerTubeClient("ANDROID", "19.29.37", "com.google.android.youtube"),
            InnerTubeClient("WEB", "2.20231219.04.00", null),
        )

        for (innerClient in clients) {
            try {
                val result = tryInnerTubeClient(videoId, innerClient)
                if (result != null) return result
            } catch (e: Exception) {
                AppLogger.w(TAG, "InnerTube ${innerClient.name} failed for $videoId", e)
            }
        }
        return null
    }

    private suspend fun tryInnerTubeClient(
        videoId: String,
        innerClient: InnerTubeClient,
    ): StreamInfo? {
        val body = buildJsonObject {
            put("videoId", videoId)
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", innerClient.name)
                    put("clientVersion", innerClient.version)
                    innerClient.androidSdkVersion?.let { put("androidSdkVersion", it) }
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        val response = client.post("https://www.youtube.com/youtubei/v1/player") {
            header("Content-Type", "application/json")
            header("X-YouTube-Client-Name", innerClient.clientId)
            header("X-YouTube-Client-Version", innerClient.version)
            setBody(body.toString())
        }

        val jsonResponse = json.parseToJsonElement(response.bodyAsText()).jsonObject

        val playabilityStatus = jsonResponse["playabilityStatus"]?.jsonObject
        val status = playabilityStatus?.get("status")?.jsonPrimitive?.contentOrNull
        if (status != "OK") return null

        val streamingData = jsonResponse["streamingData"]?.jsonObject ?: return null
        val videoDetails = jsonResponse["videoDetails"]?.jsonObject ?: return null

        // Find best audio stream
        val adaptiveFormats = streamingData["adaptiveFormats"]?.jsonArray ?: return null
        val audioStream = adaptiveFormats
            .mapNotNull { it.jsonObject }
            .filter { format ->
                val mimeType = format["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
                mimeType.startsWith("audio/")
            }
            .maxByOrNull { format ->
                format["bitrate"]?.jsonPrimitive?.longOrNull ?: 0L
            } ?: return null

        val audioUrl = audioStream["url"]?.jsonPrimitive?.contentOrNull ?: return null

        val title = videoDetails["title"]?.jsonPrimitive?.contentOrNull ?: ""
        val uploader = videoDetails["author"]?.jsonPrimitive?.contentOrNull ?: ""
        val thumbnail = videoDetails["thumbnail"]?.jsonObject
            ?.get("thumbnails")?.jsonArray
            ?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""
        val durationSeconds = videoDetails["lengthSeconds"]?.jsonPrimitive?.longOrNull ?: 0L

        // Extract subtitle tracks
        val captions = jsonResponse["captions"]?.jsonObject
            ?.get("playerCaptionsTracklistRenderer")?.jsonObject
            ?.get("captionTracks")?.jsonArray

        val subtitleTracks = captions?.mapNotNull { track ->
            val trackObj = track.jsonObject
            SubtitleTrack(
                languageCode = trackObj["languageCode"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                languageName = trackObj["name"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull ?: "",
                isAutoGenerated = trackObj["kind"]?.jsonPrimitive?.contentOrNull == "asr",
                url = trackObj["baseUrl"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                format = "vtt",
            )
        } ?: emptyList()

        return StreamInfo(
            videoId = videoId,
            title = title,
            uploader = uploader,
            thumbnailUrl = thumbnail,
            durationSeconds = durationSeconds,
            audioStreamUrl = audioUrl,
            subtitleTracks = subtitleTracks,
        )
    }

    private fun parseSearchResults(responseText: String, limit: Int): List<SearchResult> {
        return try {
            val jsonResponse = json.parseToJsonElement(responseText).jsonObject
            val contents = jsonResponse["contents"]?.jsonObject
                ?.get("twoColumnSearchResultsRenderer")?.jsonObject
                ?.get("primaryContents")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
                ?: return emptyList()

            val results = mutableListOf<SearchResult>()
            for (section in contents) {
                val items = section.jsonObject["itemSectionRenderer"]?.jsonObject
                    ?.get("contents")?.jsonArray ?: continue
                for (item in items) {
                    val videoRenderer = item.jsonObject["videoRenderer"]?.jsonObject ?: continue
                    val vid = videoRenderer["videoId"]?.jsonPrimitive?.contentOrNull ?: continue
                    val title = videoRenderer["title"]?.jsonObject
                        ?.get("runs")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
                    val uploader = videoRenderer["ownerText"]?.jsonObject
                        ?.get("runs")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
                    val thumbnail = videoRenderer["thumbnail"]?.jsonObject
                        ?.get("thumbnails")?.jsonArray
                        ?.lastOrNull()?.jsonObject
                        ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

                    results += SearchResult(
                        videoId = vid,
                        title = title,
                        uploader = uploader,
                        thumbnailUrl = thumbnail,
                        durationSeconds = 0L,
                        viewCount = 0L,
                    )
                    if (results.size >= limit) break
                }
                if (results.size >= limit) break
            }
            results
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse search results", e)
            emptyList()
        }
    }

    private data class InnerTubeClient(
        val name: String,
        val version: String,
        val androidSdkVersion: String?,
    ) {
        val clientId: String get() = when (name) {
            "WEB" -> "1"
            "ANDROID" -> "3"
            "IOS" -> "5"
            else -> "1"
        }
    }

    private companion object {
        const val TAG = "IosStreamExtractor"
    }
}
