package com.audioly.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.audioly.app.data.db.entities.PlaylistEntity
import com.audioly.app.data.db.entities.PlaylistTrackEntity
import com.audioly.app.data.db.entities.SubtitleCacheEntity
import com.audioly.app.data.db.entities.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudiolyDatabaseTest {

    private lateinit var db: AudiolyDatabase

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AudiolyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() = db.close()

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun track(videoId: String, lastPlayedAt: Long = 0L) = TrackEntity(
        videoId = videoId,
        title = "Title $videoId",
        uploader = "Uploader",
        thumbnailUrl = "https://img/$videoId",
        durationSeconds = 300L,
        audioFilePath = null,
        lastPlayedAt = lastPlayedAt,
        addedAt = System.currentTimeMillis(),
    )

    // ─── Track tests ──────────────────────────────────────────────────────────

    @Test
    fun upsertAndGetTrack() = runTest {
        db.trackDao().upsert(track("aaa"))
        val result = db.trackDao().getById("aaa")
        assertNotNull(result)
        assertEquals("aaa", result!!.videoId)
    }

    @Test
    fun recordPlayIncrements() = runTest {
        db.trackDao().upsert(track("bbb"))
        db.trackDao().recordPlay("bbb", nowMs = 1000L)
        db.trackDao().recordPlay("bbb", nowMs = 2000L)
        val result = db.trackDao().getById("bbb")!!
        assertEquals(2, result.playCount)
        assertEquals(2000L, result.lastPlayedAt)
    }

    @Test
    fun historyOrderedByLastPlayedAt() = runTest {
        db.trackDao().upsert(track("c1", lastPlayedAt = 100L))
        db.trackDao().upsert(track("c2", lastPlayedAt = 300L))
        db.trackDao().upsert(track("c3", lastPlayedAt = 200L))
        val history = db.trackDao().observeHistory(10).first()
        assertEquals(listOf("c2", "c3", "c1"), history.map { it.videoId })
    }

    @Test
    fun setAudioFilePathAndObserveCached() = runTest {
        db.trackDao().upsert(track("d1"))
        db.trackDao().setAudioFilePath("d1", "/cache/d1.mp3")
        val cached = db.trackDao().observeCached().first()
        assertEquals(1, cached.size)
        assertEquals("/cache/d1.mp3", cached[0].audioFilePath)
    }

    @Test
    fun deleteTrackRemovesRow() = runTest {
        db.trackDao().upsert(track("e1"))
        db.trackDao().delete("e1")
        assertNull(db.trackDao().getById("e1"))
    }

    // ─── Playlist tests ───────────────────────────────────────────────────────

    @Test
    fun createAndObservePlaylists() = runTest {
        db.playlistDao().createPlaylist(PlaylistEntity(name = "Favs"))
        val playlists = db.playlistDao().observePlaylists().first()
        assertEquals(1, playlists.size)
        assertEquals("Favs", playlists[0].name)
    }

    @Test
    fun playlistTrackOrderIsPreserved() = runTest {
        db.trackDao().upsert(track("v1"))
        db.trackDao().upsert(track("v2"))
        db.trackDao().upsert(track("v3"))
        val pid = db.playlistDao().createPlaylist(PlaylistEntity(name = "Mix"))
        db.playlistDao().addTrack(PlaylistTrackEntity(pid, "v1", 0))
        db.playlistDao().addTrack(PlaylistTrackEntity(pid, "v2", 1))
        db.playlistDao().addTrack(PlaylistTrackEntity(pid, "v3", 2))
        val tracks = db.playlistDao().observePlaylistTracks(pid).first()
        assertEquals(listOf("v1", "v2", "v3"), tracks.map { it.videoId })
    }

    @Test
    fun deletePlaylistCascadesToTracks() = runTest {
        db.trackDao().upsert(track("w1"))
        val pid = db.playlistDao().createPlaylist(PlaylistEntity(name = "Temp"))
        db.playlistDao().addTrack(PlaylistTrackEntity(pid, "w1", 0))
        db.playlistDao().deletePlaylist(pid)
        val tracks = db.playlistDao().observePlaylistTracks(pid).first()
        assertEquals(0, tracks.size)
        // Track itself should still exist
        assertNotNull(db.trackDao().getById("w1"))
    }

    // ─── Subtitle cache tests ─────────────────────────────────────────────────

    @Test
    fun subtitleCacheUpsertAndLookup() = runTest {
        db.trackDao().upsert(track("s1"))
        db.subtitleCacheDao().upsert(
            SubtitleCacheEntity(
                videoId = "s1",
                languageCode = "en",
                languageName = "English",
                filePath = "/cache/s1/en.vtt",
                format = "vtt",
                isAutoGenerated = false,
            ),
        )
        val entry = db.subtitleCacheDao().get("s1", "en")
        assertNotNull(entry)
        assertEquals("/cache/s1/en.vtt", entry!!.filePath)
    }

    @Test
    fun subtitleCacheDeletedWithTrack() = runTest {
        db.trackDao().upsert(track("s2"))
        db.subtitleCacheDao().upsert(
            SubtitleCacheEntity(
                videoId = "s2",
                languageCode = "ar",
                languageName = "Arabic",
                filePath = "/cache/s2/ar.vtt",
                format = "vtt",
                isAutoGenerated = true,
            ),
        )
        db.trackDao().delete("s2")
        assertNull(db.subtitleCacheDao().get("s2", "ar"))
    }
}
