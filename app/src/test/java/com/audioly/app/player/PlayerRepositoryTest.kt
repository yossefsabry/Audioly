package com.audioly.app.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerRepositoryTest {

    private fun makeRepo() = PlayerRepository(UnconfinedTestDispatcher())

    private fun item(id: String, audioUrl: String? = "https://audio/$id") = QueueItem(
        videoId = id,
        title = "Title $id",
        uploader = "Uploader",
        thumbnailUrl = "thumb/$id",
        durationSeconds = 60L,
        audioUrl = audioUrl,
    )

    // ─── Attach / Detach ─────────────────────────────────────────────────────

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

    // ─── Queue: Add / Remove / Set ───────────────────────────────────────────

    @Test
    fun `setQueue populates queue and index`() {
        val repo = makeRepo()
        val items = listOf(item("a"), item("b"), item("c"))

        repo.setQueue(items, startIndex = 1)

        assertEquals(3, repo.queue.value.size)
        assertEquals(1, repo.queueIndex.value)
        assertEquals("b", repo.queue.value[repo.queueIndex.value].videoId)
    }

    @Test
    fun `setQueue with empty list resets index to -1`() {
        val repo = makeRepo()
        repo.setQueue(emptyList())
        assertEquals(-1, repo.queueIndex.value)
    }

    @Test
    fun `addToQueue appends item and sets index to 0 if was empty`() {
        val repo = makeRepo()
        assertEquals(-1, repo.queueIndex.value)

        repo.addToQueue(item("x"))
        assertEquals(1, repo.queue.value.size)
        assertEquals(0, repo.queueIndex.value)
    }

    @Test
    fun `addToQueue appends without changing index`() {
        val repo = makeRepo()
        repo.setQueue(listOf(item("a"), item("b")), startIndex = 0)

        repo.addToQueue(item("c"))
        assertEquals(3, repo.queue.value.size)
        assertEquals(0, repo.queueIndex.value) // unchanged
        assertEquals("c", repo.queue.value[2].videoId)
    }

    @Test
    fun `playNext inserts after current index`() {
        val repo = makeRepo()
        repo.setQueue(listOf(item("a"), item("c")), startIndex = 0)

        repo.playNext(item("b"))
        assertEquals(listOf("a", "b", "c"), repo.queue.value.map { it.videoId })
    }

    @Test
    fun `removeFromQueue adjusts index when removing before current`() {
        val repo = makeRepo()
        repo.setQueue(listOf(item("a"), item("b"), item("c")), startIndex = 2)

        repo.removeFromQueue(0) // remove "a", which is before current
        assertEquals(listOf("b", "c"), repo.queue.value.map { it.videoId })
        assertEquals(1, repo.queueIndex.value) // was 2, now 1
    }

    @Test
    fun `removeFromQueue at current index clamps`() {
        val repo = makeRepo()
        repo.setQueue(listOf(item("a"), item("b")), startIndex = 1)

        repo.removeFromQueue(1) // remove current "b"
        assertEquals(listOf("a"), repo.queue.value.map { it.videoId })
        assertEquals(0, repo.queueIndex.value)
    }

    @Test
    fun `clearQueue resets everything`() {
        val repo = makeRepo()
        repo.setQueue(listOf(item("a"), item("b")), startIndex = 0)

        repo.clearQueue()
        assertTrue(repo.queue.value.isEmpty())
        assertEquals(-1, repo.queueIndex.value)
    }

    // ─── Repeat mode ─────────────────────────────────────────────────────────

    @Test
    fun `toggleRepeatMode cycles OFF - ALL - ONE - OFF`() {
        val repo = makeRepo()
        assertEquals(RepeatMode.OFF, repo.repeatMode.value)

        repo.toggleRepeatMode()
        assertEquals(RepeatMode.ALL, repo.repeatMode.value)

        repo.toggleRepeatMode()
        assertEquals(RepeatMode.ONE, repo.repeatMode.value)

        repo.toggleRepeatMode()
        assertEquals(RepeatMode.OFF, repo.repeatMode.value)
    }

    @Test
    fun `setRepeatMode sets directly`() {
        val repo = makeRepo()
        repo.setRepeatMode(RepeatMode.ONE)
        assertEquals(RepeatMode.ONE, repo.repeatMode.value)
    }

    // ─── Shuffle ─────────────────────────────────────────────────────────────

    @Test
    fun `toggleShuffle flips enabled state`() {
        val repo = makeRepo()
        assertFalse(repo.shuffleEnabled.value)

        repo.toggleShuffle()
        assertTrue(repo.shuffleEnabled.value)

        repo.toggleShuffle()
        assertFalse(repo.shuffleEnabled.value)
    }

    // ─── Queue advancement: onTrackCompleted ─────────────────────────────────

    @Test
    fun `onTrackCompleted with RepeatMode OFF advances to next`() {
        val repo = makeRepo()
        val player = FakePlayerHandle()
        repo.attach(player)
        repo.setQueue(listOf(item("a"), item("b"), item("c")), startIndex = 0)
        repo.setRepeatMode(RepeatMode.OFF)

        repo.onTrackCompleted()
        assertEquals(1, repo.queueIndex.value)
        assertEquals("b", player.lastLoad?.videoId)
    }

    @Test
    fun `onTrackCompleted at end of queue with RepeatMode OFF does not advance`() {
        val repo = makeRepo()
        val player = FakePlayerHandle()
        repo.attach(player)
        repo.setQueue(listOf(item("a"), item("b")), startIndex = 1)
        repo.setRepeatMode(RepeatMode.OFF)

        player.lastLoad = null
        repo.onTrackCompleted()
        assertEquals(1, repo.queueIndex.value) // stays at end
        assertNull(player.lastLoad) // no new load
    }

    @Test
    fun `onTrackCompleted at end of queue with RepeatMode ALL wraps to start`() {
        val repo = makeRepo()
        val player = FakePlayerHandle()
        repo.attach(player)
        repo.setQueue(listOf(item("a"), item("b")), startIndex = 1)
        repo.setRepeatMode(RepeatMode.ALL)

        repo.onTrackCompleted()
        assertEquals(0, repo.queueIndex.value) // wrapped to start
        assertEquals("a", player.lastLoad?.videoId)
    }

    @Test
    fun `onTrackCompleted with RepeatMode ONE replays same track`() {
        val repo = makeRepo()
        val player = FakePlayerHandle()
        repo.attach(player)
        repo.setQueue(listOf(item("a"), item("b")), startIndex = 0)
        repo.setRepeatMode(RepeatMode.ONE)

        repo.onTrackCompleted()
        assertEquals(0, repo.queueIndex.value) // stays at 0
        assertEquals("a", player.lastLoad?.videoId)
    }

    @Test
    fun `onTrackCompleted with empty queue does nothing`() {
        val repo = makeRepo()
        repo.onTrackCompleted() // should not throw
    }

    // ─── skipToNext / skipToPrevious ─────────────────────────────────────────

    @Test
    fun `skipToNext advances index`() {
        val repo = makeRepo()
        val player = FakePlayerHandle()
        repo.attach(player)
        repo.setQueue(listOf(item("a"), item("b"), item("c")), startIndex = 0)

        repo.skipToNext()
        assertEquals(1, repo.queueIndex.value)
        assertEquals("b", player.lastLoad?.videoId)
    }

    @Test
    fun `skipToNext at end with RepeatMode OFF does nothing`() {
        val repo = makeRepo()
        val player = FakePlayerHandle()
        repo.attach(player)
        repo.setQueue(listOf(item("a"), item("b")), startIndex = 1)
        repo.setRepeatMode(RepeatMode.OFF)

        player.lastLoad = null
        repo.skipToNext()
        assertEquals(1, repo.queueIndex.value)
        assertNull(player.lastLoad)
    }

    @Test
    fun `skipToPrevious restarts if position gt 3s`() {
        val repo = makeRepo()
        val player = FakePlayerHandle()
        repo.attach(player)
        repo.setQueue(listOf(item("a"), item("b")), startIndex = 1)
        // Simulate playing at 5 seconds
        player.state.value = player.state.value.copy(positionMs = 5_000L)

        repo.skipToPrevious()
        assertEquals(1, repo.queueIndex.value) // stays same
        assertEquals(0L, player.lastSeekToMs) // seeked to 0
    }

    @Test
    fun `skipToPrevious goes to previous track if position lt 3s`() {
        val repo = makeRepo()
        val player = FakePlayerHandle()
        repo.attach(player)
        repo.setQueue(listOf(item("a"), item("b")), startIndex = 1)
        // Simulate near start of track
        player.state.value = player.state.value.copy(positionMs = 1_000L)

        repo.skipToPrevious()
        assertEquals(0, repo.queueIndex.value)
        assertEquals("a", player.lastLoad?.videoId)
    }

    // ─── Queue advance request for tracks needing extraction ─────────────────

    @Test
    fun `onTrackCompleted emits queueAdvanceRequest for items without audioUrl`() = runTest {
        val repo = PlayerRepository(UnconfinedTestDispatcher(testScheduler))
        repo.setQueue(listOf(item("a"), item("b", audioUrl = null)), startIndex = 0)
        repo.setRepeatMode(RepeatMode.OFF)

        var emittedItem: QueueItem? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.queueAdvanceRequests.collect { emittedItem = it }
        }

        repo.onTrackCompleted()

        assertEquals("b", emittedItem?.videoId)
        assertNull(emittedItem?.audioUrl)
        job.cancel()
    }
}

// ─── Fake ─────────────────────────────────────────────────────────────────────

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
