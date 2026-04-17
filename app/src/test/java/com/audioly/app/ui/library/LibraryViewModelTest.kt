package com.audioly.app.ui.library

import com.audioly.app.data.db.entities.TrackEntity
import com.audioly.app.data.db.dao.TrackDao
import com.audioly.app.data.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for library data layer behavior (history ordering, cached-filter).
 * Exercises TrackRepository with a fake DAO — no Android dependencies.
 */
class LibraryViewModelTest {

    private fun track(
        videoId: String,
        lastPlayedAt: Long,
        audioFilePath: String? = null,
    ) = TrackEntity(
        videoId = videoId,
        title = "T $videoId",
        uploader = "U",
        thumbnailUrl = "",
        durationSeconds = 60L,
        audioFilePath = audioFilePath,
        lastPlayedAt = lastPlayedAt,
        addedAt = 0L,
    )

    @Test
    fun `history is sorted by lastPlayedAt descending`() = runTest {
        val dao = FakeTrackDao(
            listOf(
                track("a", lastPlayedAt = 100L),
                track("b", lastPlayedAt = 300L),
                track("c", lastPlayedAt = 200L),
            ),
        )
        val repo = TrackRepository(dao)
        val history = repo.observeHistory().first()
        assertEquals(listOf("b", "c", "a"), history.map { it.videoId })
    }

    @Test
    fun `cached returns only tracks with audioFilePath`() = runTest {
        val dao = FakeTrackDao(
            listOf(
                track("x", lastPlayedAt = 1L, audioFilePath = "/cache/x.mp3"),
                track("y", lastPlayedAt = 2L, audioFilePath = null),
            ),
        )
        val repo = TrackRepository(dao)
        val cached = repo.observeCached().first()
        assertEquals(1, cached.size)
        assertEquals("x", cached[0].videoId)
    }

    @Test
    fun `observeHistory excludes never-played tracks`() = runTest {
        val dao = FakeTrackDao(
            listOf(
                track("p", lastPlayedAt = 0L),  // never played
                track("q", lastPlayedAt = 5L),
            ),
        )
        val repo = TrackRepository(dao)
        val history = repo.observeHistory().first()
        assertTrue(history.none { it.videoId == "p" })
    }
}

// ─── Fake DAO ─────────────────────────────────────────────────────────────────

private class FakeTrackDao(private val initial: List<TrackEntity>) : TrackDao {

    private val state = MutableStateFlow(initial)

    override suspend fun upsert(track: TrackEntity): Long {
        state.value = state.value.filterNot { it.videoId == track.videoId } + track
        return 0L
    }

    override suspend fun getById(videoId: String): TrackEntity? =
        state.value.find { it.videoId == videoId }

    override fun observeHistory(limit: Int): Flow<List<TrackEntity>> =
        MutableStateFlow(state.value.filter { it.lastPlayedAt > 0 }.sortedByDescending { it.lastPlayedAt }.take(limit))

    override fun observeCached(): Flow<List<TrackEntity>> =
        MutableStateFlow(state.value.filter { it.audioFilePath != null }.sortedByDescending { it.addedAt })

    override fun observeAll(): Flow<List<TrackEntity>> = state

    override suspend fun recordPlay(videoId: String, nowMs: Long) {}

    override suspend fun setAudioFilePath(videoId: String, path: String?) {}

    override suspend fun delete(videoId: String) {
        state.value = state.value.filterNot { it.videoId == videoId }
    }
}
