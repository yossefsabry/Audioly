package com.audioly.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioly.app.data.cache.SubtitleCacheManager
import com.audioly.app.data.preferences.UserPreferencesRepository
import com.audioly.app.data.model.Playlist
import com.audioly.app.data.repository.CacheRepository
import com.audioly.app.data.repository.PlaylistRepository
import com.audioly.app.data.repository.TrackRepository
import com.audioly.app.extraction.ExtractionResult
import com.audioly.app.extraction.SearchResult
import com.audioly.app.extraction.YouTubeExtractor
import com.audioly.app.extraction.YouTubeSearchService
import com.audioly.app.player.PlayerRepository
import com.audioly.app.player.QueueItem
import com.audioly.app.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

/**
 * ViewModel for SearchScreen. Owns query state, search results,
 * pagination, and play-from-search-result flow.
 */
class SearchViewModel(
    private val searchService: YouTubeSearchService,
    private val youTubeExtractor: YouTubeExtractor,
    private val playerRepository: PlayerRepository,
    private val trackRepository: TrackRepository,
    private val cacheRepository: CacheRepository,
    private val playlistRepository: PlaylistRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val subtitleCacheManager: SubtitleCacheManager,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _lastFailedResult = MutableStateFlow<SearchResult?>(null)
    val lastFailedResult: StateFlow<SearchResult?> = _lastFailedResult.asStateFlow()

    private val _correctedQuery = MutableStateFlow<String?>(null)
    val correctedQuery: StateFlow<String?> = _correctedQuery.asStateFlow()

    private val _events = MutableSharedFlow<SearchEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private var nextPage: Page? = null
    private var currentQuery: String = ""
    private var searchJob: Job? = null

    fun updateQuery(text: String) {
        _query.value = text
    }

    fun search(query: String = _query.value) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        searchJob?.cancel()
        currentQuery = trimmed
        _query.value = trimmed
        nextPage = null
        _correctedQuery.value = null

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _results.value = emptyList()
            try {
                searchService.search(trimmed).fold(
                    onSuccess = { page ->
                        _results.value = page.results
                        nextPage = page.nextPage
                        _correctedQuery.value = page.correctedQuery
                    },
                    onFailure = { e ->
                        AppLogger.e(TAG, "Search failed", e)
                        _events.emit(SearchEvent.ShowSnackbar("Search failed: ${e.message ?: "unknown error"}"))
                    },
                )
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun loadMore() {
        val page = nextPage ?: return
        if (_isLoadingMore.value || _isSearching.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                searchService.searchMore(currentQuery, page).fold(
                    onSuccess = { morePage ->
                        _results.value = _results.value + morePage.results
                        nextPage = morePage.nextPage
                    },
                    onFailure = { e ->
                        AppLogger.e(TAG, "Load more failed", e)
                        _events.emit(SearchEvent.ShowSnackbar("Failed to load more results"))
                    },
                )
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    val hasMore: Boolean get() = nextPage != null

    /** Retry the last failed extraction. */
    fun retryLastResult() {
        val result = _lastFailedResult.value ?: return
        playResult(result)
    }

    fun playResult(result: SearchResult) {
        if (_isExtracting.value) return

        viewModelScope.launch {
            _isExtracting.value = true
            _lastFailedResult.value = null
            try {
                // Try cache first
                if (tryPlayFromCache(result.videoId)) return@launch

                val url = "https://www.youtube.com/watch?v=${result.videoId}"
                AppLogger.i(TAG, "Extracting search result: ${result.videoId}")
                when (val extraction = youTubeExtractor.extract(url)) {
                    is ExtractionResult.Success -> {
                        val info = extraction.streamInfo
                        try {
                            trackRepository.upsertFromExtraction(info)
                            trackRepository.setAudioStreamUrl(info.videoId, info.audioStreamUrl)
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
                        applyDefaultSpeed()
                        _events.emit(SearchEvent.NavigateToPlayer(info.videoId))
                    }
                    is ExtractionResult.Failure.NetworkError -> {
                        _lastFailedResult.value = result
                        _events.emit(SearchEvent.ShowSnackbar("Network error. Check your connection."))
                    }
                    is ExtractionResult.Failure -> {
                        _lastFailedResult.value = result
                        _events.emit(SearchEvent.ShowSnackbar("Could not load this track right now"))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Unexpected error during search playback", e)
                _lastFailedResult.value = result
                _events.emit(SearchEvent.ShowSnackbar("Something went wrong: ${e.message ?: "unknown error"}"))
            } finally {
                _isExtracting.value = false
            }
        }
    }

    private suspend fun tryPlayFromCache(videoId: String): Boolean {
        try {
            val cacheStatus = cacheRepository.getAudioStatus(videoId)
            if (!cacheStatus.isFullyCached) return false
            val track = trackRepository.getById(videoId) ?: return false
            val audioUrl = track.audioStreamUrl
            if (audioUrl.isNullOrBlank()) return false

            playerRepository.clearSubtitles()
            playerRepository.load(
                audioUrl = audioUrl,
                videoId = videoId,
                title = track.title,
                uploader = track.uploader,
                thumbnailUrl = track.thumbnailUrl,
                durationMs = track.durationSeconds * 1000L,
            )
            applyDefaultSpeed()
            _events.emit(SearchEvent.NavigateToPlayer(videoId))
            return true
        } catch (e: Exception) {
            AppLogger.w(TAG, "Cache playback failed: ${e.message}")
            return false
        }
    }

    private suspend fun applyDefaultSpeed() {
        try {
            val speed = preferencesRepository.preferences.first().playbackSpeed
            if (speed != 1.0f) playerRepository.setSpeed(speed)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to apply default speed: ${e.message}")
        }
    }

    fun addToQueue(result: SearchResult) {
        playerRepository.addToQueue(
            QueueItem(
                videoId = result.videoId,
                title = result.title,
                uploader = result.uploader,
                thumbnailUrl = result.thumbnailUrl,
                durationSeconds = result.durationSeconds,
            )
        )
        viewModelScope.launch {
            _events.emit(SearchEvent.ShowSnackbar("Added to queue: ${result.title}"))
        }
    }

    fun playNextInQueue(result: SearchResult) {
        playerRepository.playNext(
            QueueItem(
                videoId = result.videoId,
                title = result.title,
                uploader = result.uploader,
                thumbnailUrl = result.thumbnailUrl,
                durationSeconds = result.durationSeconds,
            )
        )
        viewModelScope.launch {
            _events.emit(SearchEvent.ShowSnackbar("Playing next: ${result.title}"))
        }
    }

    /** Observable list of playlists for the "Add to playlist" picker. */
    val playlists: StateFlow<List<Playlist>> = MutableStateFlow<List<Playlist>>(emptyList()).also { state ->
        viewModelScope.launch {
            playlistRepository.observePlaylists().collect { state.value = it }
        }
    }

    /**
     * Ensure track exists in DB from search metadata, then add to playlist.
     */
    fun addToPlaylist(result: SearchResult, playlistId: Long) {
        viewModelScope.launch {
            try {
                trackRepository.upsertFromSearchResult(result)
                playlistRepository.addTrackToPlaylist(playlistId, result.videoId)
                _events.emit(SearchEvent.ShowSnackbar("Added to playlist"))
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to add to playlist", e)
                _events.emit(SearchEvent.ShowSnackbar("Failed to add to playlist"))
            }
        }
    }

    companion object {
        private const val TAG = "SearchViewModel"
    }
}

sealed interface SearchEvent {
    data class NavigateToPlayer(val videoId: String) : SearchEvent
    data class ShowSnackbar(val message: String) : SearchEvent
}
