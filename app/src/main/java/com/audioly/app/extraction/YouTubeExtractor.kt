package com.audioly.app.extraction

import com.audioly.app.util.AppLogger
import com.audioly.app.util.UrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo as NewPipeStreamInfo
import java.io.IOException

class YouTubeExtractor {

    private companion object {
        const val TAG = "YouTubeExtractor"
    }

    init {
        try {
            NewPipe.init(OkHttpDownloader.instance)
        } catch (_: Exception) {
            // Already initialized
        }
    }

    suspend fun extract(url: String): ExtractionResult = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "Extraction started for: $url")
        val videoId = UrlValidator.extractVideoId(url)
            ?: return@withContext ExtractionResult.Failure.InvalidUrl(url).also {
                AppLogger.w(TAG, "Invalid URL: $url")
            }

        val canonicalUrl = UrlValidator.canonicalUrl(videoId)

        try {
            // StreamInfo.getInfo(service, url) is a static method — URL as String
            val npStreamInfo = NewPipeStreamInfo.getInfo(ServiceList.YouTube, canonicalUrl)

            // Pick best audio stream (highest bitrate)
            val audioStream = npStreamInfo.audioStreams
                .filter { it.content.isNotBlank() }
                .maxByOrNull { it.averageBitrate }
                ?: return@withContext ExtractionResult.Failure.ExtractionFailed(
                    IllegalStateException("No audio streams found for $videoId")
                )

            // Thumbnail: pick highest quality
            val thumbnail = npStreamInfo.thumbnails
                .maxByOrNull { it.width * it.height }
                ?.url ?: ""

            // Subtitles
            val subtitleTracks = SubtitleExtractor.extract(npStreamInfo)

            ExtractionResult.Success(
                StreamInfo(
                    videoId = videoId,
                    title = npStreamInfo.name,
                    uploader = npStreamInfo.uploaderName,
                    thumbnailUrl = thumbnail,
                    durationSeconds = npStreamInfo.duration,
                    audioStreamUrl = audioStream.content,
                    subtitleTracks = subtitleTracks
                )
            ).also {
                AppLogger.i(TAG, "Extraction success: $videoId — ${npStreamInfo.name} (${npStreamInfo.duration}s)")
            }

        } catch (e: AgeRestrictedContentException) {
            AppLogger.w(TAG, "Age-restricted: $videoId", e)
            ExtractionResult.Failure.AgeRestricted(videoId)
        } catch (e: ContentNotAvailableException) {
            AppLogger.w(TAG, "Unavailable: $videoId", e)
            ExtractionResult.Failure.VideoUnavailable(videoId)
        } catch (e: ReCaptchaException) {
            AppLogger.e(TAG, "ReCaptcha for $videoId", e)
            ExtractionResult.Failure.ExtractionFailed(e)
        } catch (e: ExtractionException) {
            AppLogger.e(TAG, "Extraction failed for $videoId", e)
            ExtractionResult.Failure.ExtractionFailed(e)
        } catch (e: IOException) {
            AppLogger.e(TAG, "Network error for $videoId", e)
            ExtractionResult.Failure.NetworkError(e)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Unexpected error for $videoId", e)
            ExtractionResult.Failure.ExtractionFailed(e)
        }
    }
}
