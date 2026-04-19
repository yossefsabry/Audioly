package com.audioly.app.extraction

import com.audioly.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Wraps NewPipe SearchExtractor to search YouTube for videos.
 * All calls run on IO dispatcher (NewPipe search is blocking I/O).
 */
class YouTubeSearchService {

    data class SearchPage(
        val results: List<SearchResult>,
        val nextPage: Page?,
        val correctedQuery: String?,
    )

    /**
     * Perform an initial search query.
     */
    suspend fun search(query: String): Result<SearchPage> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val queryHandler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
            val searchInfo = SearchInfo.getInfo(service, queryHandler)

            val results = searchInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .filter { !it.isShortFormContent }
                .mapNotNull { item -> itemToResult(item) }

            val corrected = if (searchInfo.isCorrectedSearch) searchInfo.searchSuggestion else null
            Result.success(SearchPage(results, searchInfo.nextPage, corrected))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AppLogger.e(TAG, "Search network error", e)
            Result.failure(e)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Search failed", e)
            Result.failure(e)
        }
    }

    /**
     * Load the next page of search results.
     */
    suspend fun searchMore(query: String, page: Page): Result<SearchPage> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val queryHandler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
            val moreItems = SearchInfo.getMoreItems(service, queryHandler, page)

            val results = moreItems.items
                .filterIsInstance<StreamInfoItem>()
                .filter { !it.isShortFormContent }
                .mapNotNull { item -> itemToResult(item) }

            Result.success(SearchPage(results, moreItems.nextPage, null))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AppLogger.e(TAG, "Search pagination network error", e)
            Result.failure(e)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Search pagination failed", e)
            Result.failure(e)
        }
    }

    private fun itemToResult(item: StreamInfoItem): SearchResult? {
        val url = item.url ?: return null
        val videoId = extractVideoId(url) ?: return null
        val thumbUrl = item.thumbnails.firstOrNull()?.url ?: ""

        return SearchResult(
            videoId = videoId,
            title = item.name ?: "Unknown",
            uploader = item.uploaderName ?: "",
            thumbnailUrl = thumbUrl,
            durationSeconds = item.duration.coerceAtLeast(0),
            viewCount = item.viewCount.coerceAtLeast(0),
        )
    }

    private fun extractVideoId(url: String): String? {
        // YouTube URLs: /watch?v=ID or /shorts/ID
        val regex = Regex("""(?:v=|youtu\.be/|/shorts/)([a-zA-Z0-9_-]{11})""")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    companion object {
        private const val TAG = "YouTubeSearchService"
    }
}
