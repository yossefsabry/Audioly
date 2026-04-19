package com.audioly.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.audioly.app.AudiolyApp
import com.audioly.app.MainActivity
import com.audioly.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground [MediaSessionService] that keeps audio alive when the app is backgrounded.
 *
 * Components that need playback control bind to this service via [LocalBinder] or
 * interact through [PlaybackController].
 */
@OptIn(UnstableApi::class)
class AudioService : MediaSessionService() {

    inner class LocalBinder : Binder() {
        val service get() = this@AudioService
    }

    private val binder = LocalBinder()

    lateinit var player: AudioPlayer
        private set

    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tickJob: Job? = null
    private var lastRecordedVideoId: String? = null

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "AudioService created")
        val app = application as AudiolyApp
        player = AudioPlayer(
            context = this,
            audioCacheManager = app.audioCacheManager,
            onTrackEnded = { app.playerRepository.onTrackCompleted() },
        )
        mediaSession = MediaSession.Builder(this, player.exoPlayer)
            .setSessionActivity(buildSessionActivity())
            .build()
        // Register session with MediaSessionService so the notification manager picks it up.
        // Without this, onGetSession() is never called (no MediaController connects) and
        // the system never creates the media notification.
        addSession(mediaSession!!)
        createNotificationChannel()
        startPositionTicker()
    }

    override fun onBind(intent: Intent?): IBinder {
        // If the intent has MediaSessionService's action, return the framework binder
        // so system media controls (notification, Android Auto) work correctly.
        val superBinder = super.onBind(intent)
        if (intent?.action == SERVICE_INTERFACE) {
            return superBinder ?: binder
        }
        return binder
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        AppLogger.i(TAG, "AudioService destroyed")
        // Cancel tick job first and wait — prevents tick() on released player
        tickJob?.cancel()
        tickJob = null
        // Cancel the entire scope (prevents leaked coroutines)
        serviceScope.cancel()
        // Release player independently of mediaSession state
        if (::player.isInitialized) {
            try {
                player.release()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error releasing player", e)
            }
        }
        // Remove session from service before releasing
        mediaSession?.let { removeSession(it) }
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val playerState = player.state.value
        if (playerState.isPlaying || playerState.isBuffering) {
            AppLogger.i(TAG, "Task removed while playback active; keeping service alive")
        } else {
            AppLogger.i(TAG, "Task removed while idle; stopping service")
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    // ─── Position ticker ──────────────────────────────────────────────────────

    private fun startPositionTicker() {
        tickJob = serviceScope.launch {
            while (true) {
                val s = player.state.value
                // Only tick when actively playing or buffering — skip idle/error to save CPU
                if (s.isPlaying || s.isBuffering) {
                    player.tick()
                    recordPlaybackStartIfNeeded()
                }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun recordPlaybackStartIfNeeded() {
        val state = player.state.value
        val videoId = state.videoId ?: return
        if (!state.isPlaying || state.positionMs > PLAY_START_WINDOW_MS) return
        if (lastRecordedVideoId == videoId) return

        lastRecordedVideoId = videoId
        serviceScope.launch(Dispatchers.IO) {
            try {
                (application as AudiolyApp).trackRepository.recordPlay(videoId)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to record play for $videoId: ${e.message}")
            }
        }
    }

    // ─── Notification boilerplate ─────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audioly Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Audio playback controls" }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun buildSessionActivity(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        private const val TAG = "AudioService"
        const val CHANNEL_ID = "audioly_playback"
        private const val TICK_INTERVAL_MS = 250L
        private const val PLAY_START_WINDOW_MS = 2_000L
    }
}
