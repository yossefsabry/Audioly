package com.audioly.app.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.audioly.app.util.AppLogger

/**
 * Lifecycle-aware helper that binds to [AudioService] and wires it to [PlayerRepository].
 *
 * Usage: create in `Activity.onCreate`, register as lifecycle observer.
 * Compose ViewModels interact exclusively with [PlayerRepository].
 */
class PlaybackController(
    context: Context,
    private val repository: PlayerRepository,
) : DefaultLifecycleObserver {

    // Use applicationContext to avoid leaking the Activity on config changes
    private val appContext: Context = context.applicationContext

    /** True once bindService() is called; cleared in onStop regardless of callback state. */
    private var bindRequested = false
    /** True only after onServiceConnected fires. */
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            AppLogger.d(TAG, "AudioService bound")
            val binder = service as? AudioService.LocalBinder
            if (binder == null) {
                AppLogger.w(TAG, "Unexpected binder type: ${service.javaClass.name}")
                return
            }
            repository.attach(binder.service.player)
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            AppLogger.d(TAG, "AudioService disconnected")
            repository.detach()
            bound = false
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        AppLogger.d(TAG, "Starting and binding to AudioService")
        val intent = Intent(appContext, AudioService::class.java)
        // Explicitly start the service so it becomes a "started service" and
        // survives unbind when the activity goes to background.
        // MediaSessionService auto-promotes to foreground (with notification)
        // once playback begins — no manual startForeground() needed.
        appContext.startService(intent)
        appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        bindRequested = true
    }

    override fun onStop(owner: LifecycleOwner) {
        if (bindRequested) {
            AppLogger.d(TAG, "Unbinding from AudioService")
            try {
                appContext.unbindService(connection)
            } catch (e: IllegalArgumentException) {
                AppLogger.w(TAG, "unbindService failed (already unbound): ${e.message}")
            }
            bindRequested = false
            bound = false
            repository.detach()
        }
    }

    private companion object {
        const val TAG = "PlaybackController"
    }
}
