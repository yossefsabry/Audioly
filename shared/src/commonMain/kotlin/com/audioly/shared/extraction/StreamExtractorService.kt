package com.audioly.shared.extraction

/**
 * Common interface for YouTube stream extraction.
 * Android: NewPipeExtractor + InnerTube fallback
 * iOS: InnerTube API client (Ktor-based)
 */
interface StreamExtractorService {
    /** Extract audio stream info from a YouTube video URL or ID. */
    suspend fun extractStream(videoIdOrUrl: String): ExtractionResult

    /** Search YouTube for videos matching [query]. */
    suspend fun search(query: String, limit: Int = 20): List<SearchResult>

    /** Get search suggestions/autocomplete for [query]. */
    suspend fun searchSuggestions(query: String): List<String>
}
