package com.audioly.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.audioly.app.data.preferences.UserPreferences
import com.audioly.app.ui.components.MiniPlayer
import com.audioly.app.ui.navigation.Screen
import com.audioly.app.ui.navigation.bottomNavItems
import com.audioly.app.ui.screens.home.HomeScreen
import com.audioly.app.ui.screens.library.LibraryScreen
import com.audioly.app.ui.screens.library.PlaylistDetailScreen
import com.audioly.app.ui.screens.player.PlayerScreen
import com.audioly.app.ui.screens.search.SearchScreen
import com.audioly.app.ui.screens.settings.SettingsScreen
import com.audioly.app.ui.screens.logs.LogViewerScreen
import com.audioly.app.ui.theme.AudiolyTheme
import com.audioly.app.ui.viewmodel.AudiolyViewModelFactory
import com.audioly.app.ui.viewmodel.HomeViewModel
import com.audioly.app.ui.viewmodel.LibraryViewModel
import com.audioly.app.ui.viewmodel.PlayerViewModel
import com.audioly.app.ui.viewmodel.SearchViewModel
import com.audioly.app.util.AppLogger
import com.audioly.shared.util.UrlValidator
import com.audioly.app.player.PlaybackController

class MainActivity : ComponentActivity() {

    private var sharedUrlState: MutableState<String?>? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        AppLogger.i("MainActivity", "Notification permission granted: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AudiolyApp

        // Request notification permission (required on Android 13+ for foreground service)
        requestNotificationPermissionIfNeeded()

        // Bind to AudioService and wire PlayerRepository
        val playbackController = PlaybackController(this, app.playerRepository)
        lifecycle.addObserver(playbackController)

        val viewModelFactory = AudiolyViewModelFactory(app)

        setContent {
            // Read theme preference
            val prefs by app.preferencesRepository.preferences.collectAsState(
                initial = UserPreferences()
            )
            val darkTheme = when (prefs.themeMode) {
                UserPreferences.THEME_LIGHT -> false
                UserPreferences.THEME_DARK -> true
                else -> null // null = follow system
            }

            AudiolyTheme(darkTheme = darkTheme) {
                val sharedUrl = remember { mutableStateOf(intent?.resolveSharedUrl()) }
                LaunchedEffect(sharedUrl) {
                    sharedUrlState = sharedUrl
                }
                DisposableEffect(Unit) {
                    onDispose {
                        if (sharedUrlState === sharedUrl) {
                            sharedUrlState = null
                        }
                    }
                }
                AudiolyMainContent(
                    initialUrl = sharedUrl.value,
                    app = app,
                    viewModelFactory = viewModelFactory,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUrlState?.value = intent?.resolveSharedUrl()
    }

    /**
     * Extract the raw shared text (URL) from a SEND intent.
     * Returns the full URL string, NOT the videoId — so HomeScreen can handle extraction.
     */
    private fun Intent.resolveSharedUrl(): String? {
        if (action != Intent.ACTION_SEND) return null
        val text = getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val result = if (UrlValidator.isValid(text)) text else null
        if (result != null) {
            AppLogger.i("MainActivity", "Share intent received: $text")
        }
        return result
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudiolyMainContent(
    initialUrl: String?,
    app: AudiolyApp,
    viewModelFactory: AudiolyViewModelFactory,
) {
    val navController = rememberNavController()
    val playerState by app.playerRepository.state.collectAsState()
    var pendingSharedUrl by remember(initialUrl) { mutableStateOf(initialUrl) }

    LaunchedEffect(initialUrl) {
        if (initialUrl != null) {
            pendingSharedUrl = initialUrl
            navController.navigate(Screen.Home.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDest = navBackStackEntry?.destination
            val showMiniPlayer = !playerState.isEmpty && currentDest?.route?.startsWith("player/") != true
            val showBottomBar = currentDest?.route?.startsWith("player/") != true
                && currentDest?.route != Screen.Logs.route
            Column {
                if (showMiniPlayer) {
                    MiniPlayer(
                        state = playerState,
                        onTogglePlayPause = { app.playerRepository.togglePlayPause() },
                        onTap = {
                            playerState.videoId?.let { videoId ->
                                navController.navigate(Screen.Player.createRoute(videoId)) {
                                    launchSingleTop = true
                                }
                            }
                        },
                    )
                }
                if (showBottomBar) {
                    NavigationBar {
                        bottomNavItems.forEach { item ->
                            val selected = currentDest?.hierarchy?.any { it.route == item.screen.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(navController = navController, startDestination = Screen.Home.route) {
                composable(Screen.Home.route) {
                    val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToPlayer = { videoId ->
                            navController.navigate(Screen.Player.createRoute(videoId)) {
                                launchSingleTop = true
                            }
                        },
                        initialUrl = pendingSharedUrl,
                    )
                    LaunchedEffect(pendingSharedUrl) {
                        if (pendingSharedUrl != null) {
                            pendingSharedUrl = null
                        }
                    }
                }
                composable(Screen.Library.route) {
                    val libraryViewModel: LibraryViewModel = viewModel(factory = viewModelFactory)
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onNavigateToPlayer = { videoId ->
                            navController.navigate(Screen.Player.createRoute(videoId)) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToPlaylist = { playlistId ->
                            navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                        },
                    )
                }
                composable(Screen.Search.route) {
                    val searchViewModel: SearchViewModel = viewModel(factory = viewModelFactory)
                    SearchScreen(
                        viewModel = searchViewModel,
                        onNavigateToPlayer = { videoId ->
                            navController.navigate(Screen.Player.createRoute(videoId)) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        app = app,
                        onNavigateToLogs = { navController.navigate(Screen.Logs.route) },
                    )
                }
                composable(Screen.Logs.route) {
                    LogViewerScreen(onNavigateUp = { navController.popBackStack() })
                }
                composable(Screen.PlaylistDetail.route) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId")?.toLongOrNull() ?: return@composable
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        playlistRepository = app.playlistRepository,
                        cacheRepository = app.cacheRepository,
                        onNavigateUp = { navController.popBackStack() },
                        onPlayAll = { queueItems, startIndex ->
                            app.playerRepository.setQueue(queueItems, startIndex)
                            val item = queueItems.getOrNull(startIndex) ?: return@PlaylistDetailScreen
                            val audioUrl = item.audioUrl
                            if (audioUrl != null) {
                                app.playerRepository.clearSubtitles()
                                app.playerRepository.load(
                                    audioUrl = audioUrl,
                                    videoId = item.videoId,
                                    title = item.title,
                                    uploader = item.uploader,
                                    thumbnailUrl = item.thumbnailUrl,
                                    durationMs = item.durationSeconds * 1000L,
                                )
                            }
                            navController.navigate(Screen.Player.createRoute(item.videoId)) {
                                launchSingleTop = true
                            }
                        },
                        onPlayTrack = { track ->
                            navController.navigate(Screen.Player.createRoute(track.videoId)) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable("player/{videoId}") { _ ->
                    val playerViewModel: PlayerViewModel = viewModel(factory = viewModelFactory)
                    PlayerScreen(
                        viewModel = playerViewModel,
                        onNavigateUp = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
