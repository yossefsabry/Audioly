package com.audioly.app.share

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.audioly.app.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test: app launches without crashing from a shared YouTube URL.
 * Does NOT verify live extraction (would require network + real video).
 * Verifies that the share intent pipeline is wired up and the Activity
 * opens without an exception.
 */
@RunWith(AndroidJUnit4::class)
class ShareIntentTest {

    @Test
    fun launchFromShareIntentDoesNotCrash() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://youtu.be/dQw4w9WgXcQ")
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // If we reach here without an exception, the share routing is wired
            assert(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED))
        }
    }

    @Test
    fun launchFromNormalIntentDoesNotCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assert(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED))
        }
    }
}
