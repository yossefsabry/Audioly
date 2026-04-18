package com.audioly.app.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerRepositoryTest {

    @Test
    fun `load while detached replays on attach`() = runTest {
        val repo = PlayerRepository(UnconfinedTestDispatcher(testScheduler))

        repo.load(
            audioUrl = "https://audio",
            videoId = "abc",
            title = "Song",
            uploader = "Artist",
            thumbnailUrl = "thumb",
            durationMs = 1_000L,
        )

        assertEquals("abc", repo.state.value.videoId)
        assertEquals(true, repo.state.value.isBuffering)

        val fake = FakePlayerHandle()
        repo.attach(fake)

        assertEquals("abc", fake.lastLoad?.videoId)
        assertNull(fake.lastSeekToMs)
    }

    @Test
    fun `toggle while detached queues last load and seek position`() = runTest {
        val repo = PlayerRepository(UnconfinedTestDispatcher(testScheduler))
        val firstPlayer = FakePlayerHandle()
        repo.attach(firstPlayer)

        repo.load(
            audioUrl = "https://audio",
            videoId = "abc",
            title = "Song",
            uploader = "Artist",
            thumbnailUrl = "thumb",
            durationMs = 10_000L,
        )
        firstPlayer.seekTo(4_000L)
        repo.detach()

        repo.togglePlayPause()

        val secondPlayer = FakePlayerHandle()
        repo.attach(secondPlayer)

        assertEquals("abc", secondPlayer.lastLoad?.videoId)
        assertEquals(4_000L, secondPlayer.lastSeekToMs)
    }

    @Test
    fun `toggle while attached to empty player reloads last track`() = runTest {
        val repo = PlayerRepository(UnconfinedTestDispatcher(testScheduler))
        val firstPlayer = FakePlayerHandle()
        repo.attach(firstPlayer)

        repo.load(
            audioUrl = "https://audio",
            videoId = "abc",
            title = "Song",
            uploader = "Artist",
            thumbnailUrl = "thumb",
            durationMs = 10_000L,
        )
        repo.detach()

        val emptyPlayer = FakePlayerHandle()
        repo.attach(emptyPlayer)
        assertNull(emptyPlayer.lastLoad)

        repo.togglePlayPause()

        assertEquals("abc", emptyPlayer.lastLoad?.videoId)
    }
}

private class FakePlayerHandle : PlayerHandle {
    override val state = MutableStateFlow(PlayerState())
    var lastLoad: LoadCall? = null
    var lastSeekToMs: Long? = null

    override fun load(
        audioUrl: String,
        videoId: String,
        title: String,
        uploader: String,
        thumbnailUrl: String,
        durationMs: Long,
    ) {
        lastLoad = LoadCall(audioUrl, videoId, title, uploader, thumbnailUrl, durationMs)
        state.value = PlayerState(
            videoId = videoId,
            title = title,
            uploader = uploader,
            thumbnailUrl = thumbnailUrl,
            durationMs = durationMs,
            isBuffering = true,
        )
    }

    override fun play() {}
    override fun pause() {}
    override fun togglePlayPause() {}

    override fun seekTo(positionMs: Long) {
        lastSeekToMs = positionMs
        state.value = state.value.copy(positionMs = positionMs)
    }

    override fun skipForward(intervalMs: Long) {}
    override fun skipBack(intervalMs: Long) {}
    override fun setSpeed(speed: Float) {}
    override fun setSubtitleLanguage(languageCode: String) {}
    override fun setSubtitleIndex(index: Int) {}
}

private data class LoadCall(
    val audioUrl: String,
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val durationMs: Long,
)
