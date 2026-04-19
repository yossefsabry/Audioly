package com.audioly.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.audioly.app.AudiolyApp

/**
 * Manual ViewModelProvider.Factory for all app ViewModels.
 * Replaces Hilt/Dagger — each ViewModel receives specific repository dependencies.
 */
class AudiolyViewModelFactory(
    private val app: AudiolyApp,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(
            trackRepository = app.trackRepository,
            cacheRepository = app.cacheRepository,
            playerRepository = app.playerRepository,
            youTubeExtractor = app.youTubeExtractor,
            preferencesRepository = app.preferencesRepository,
            subtitleCacheManager = app.subtitleCacheManager,
        ) as T

        modelClass.isAssignableFrom(PlayerViewModel::class.java) -> PlayerViewModel(
            playerRepository = app.playerRepository,
            preferencesRepository = app.preferencesRepository,
            subtitleCacheManager = app.subtitleCacheManager,
            youTubeExtractor = app.youTubeExtractor,
            trackRepository = app.trackRepository,
            trackDownloadManager = app.trackDownloadManager,
        ) as T

        modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel(
            trackRepository = app.trackRepository,
            cacheRepository = app.cacheRepository,
            playlistRepository = app.playlistRepository,
            playerRepository = app.playerRepository,
            youTubeExtractor = app.youTubeExtractor,
            preferencesRepository = app.preferencesRepository,
            subtitleCacheManager = app.subtitleCacheManager,
        ) as T

        modelClass.isAssignableFrom(SearchViewModel::class.java) -> SearchViewModel(
            searchService = app.youTubeSearchService,
            youTubeExtractor = app.youTubeExtractor,
            playerRepository = app.playerRepository,
            trackRepository = app.trackRepository,
            cacheRepository = app.cacheRepository,
            playlistRepository = app.playlistRepository,
            preferencesRepository = app.preferencesRepository,
            subtitleCacheManager = app.subtitleCacheManager,
        ) as T

        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
