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
    private val context: Context,
    private val repository: PlayerRepository,
) : DefaultLifecycleObserver {

    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            AppLogger.d(TAG, "AudioService bound")
            val binder = service as AudioService.LocalBinder
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
        AppLogger.d(TAG, "Binding to AudioService")
        val intent = Intent(context, AudioService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop(owner: LifecycleOwner) {
        if (bound) {
            AppLogger.d(TAG, "Unbinding from AudioService")
            context.unbindService(connection)
            bound = false
            repository.detach()
        }
    }

    private companion object {
        const val TAG = "PlaybackController"
    }
}
