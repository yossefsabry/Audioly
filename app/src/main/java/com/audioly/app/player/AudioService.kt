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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var tickJob: Job? = null

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "AudioService created")
        val app = application as AudiolyApp
        player = AudioPlayer(this, app.audioCacheManager)
        mediaSession = MediaSession.Builder(this, player.exoPlayer)
            .setSessionActivity(buildSessionActivity())
            .build()
        createNotificationChannel()
        startPositionTicker()
    }

    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        AppLogger.i(TAG, "AudioService destroyed")
        tickJob?.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    // ─── Position ticker ──────────────────────────────────────────────────────

    private fun startPositionTicker() {
        tickJob = serviceScope.launch {
            while (true) {
                player.tick()
                delay(TICK_INTERVAL_MS)
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
    }
}
