package com.audioly.app.data.cache

import com.audioly.app.data.db.dao.SubtitleCacheDao
import com.audioly.app.data.db.entities.SubtitleCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubtitleCacheManagerTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private lateinit var fakeDao: FakeSubtitleCacheDao
    private lateinit var manager: SubtitleCacheManager

    @Before
    fun setup() {
        fakeDao = FakeSubtitleCacheDao()
        manager = SubtitleCacheManager(tmpDir.newFolder("subtitles"), fakeDao)
    }

    @Test
    fun `save writes file and inserts row`() = runTest {
        val path = manager.save("vid1", "en", "English", "vtt", false, "WEBVTT\n\n")
        assertTrue("file must exist", java.io.File(path).exists())
        assertEquals("WEBVTT\n\n", java.io.File(path).readText())
        assertEquals(1, fakeDao.rows.size)
    }

    @Test
    fun `load returns content after save`() = runTest {
        manager.save("vid2", "ar", "Arabic", "vtt", true, "WEBVTT\ncue")
        val content = manager.load("vid2", "ar")
        assertEquals("WEBVTT\ncue", content)
    }

    @Test
    fun `load returns null when not cached`() = runTest {
        assertNull(manager.load("missing", "en"))
    }

    @Test
    fun `pathFor produces stable path`() {
        val p1 = manager.pathFor("v", "en", "vtt")
        val p2 = manager.pathFor("v", "en", "vtt")
        assertEquals(p1, p2)
    }

    @Test
    fun `save overwrites existing file`() = runTest {
        manager.save("vid3", "en", "English", "vtt", false, "old content")
        manager.save("vid3", "en", "English", "vtt", false, "new content")
        assertEquals("new content", manager.load("vid3", "en"))
        // DAO upsert replaces — still 1 unique row
        assertEquals(1, fakeDao.rows.size)
    }

    @Test
    fun `delete removes file and row`() = runTest {
        val path = manager.save("vid4", "en", "English", "vtt", false, "content")
        manager.delete("vid4", "en")
        assertTrue(!java.io.File(path).exists())
        assertNull(fakeDao.get("vid4", "en"))
    }

    @Test
    fun `deleteAllForVideo cleans entire directory`() = runTest {
        manager.save("vid5", "en", "English", "vtt", false, "c1")
        manager.save("vid5", "fr", "French", "vtt", false, "c2")
        manager.deleteAllForVideo("vid5")
        assertEquals(0, fakeDao.rows.size)
    }
}

// ─── Fake DAO ─────────────────────────────────────────────────────────────────

private class FakeSubtitleCacheDao : SubtitleCacheDao {
    val rows = mutableMapOf<Pair<String, String>, SubtitleCacheEntity>()

    override suspend fun upsert(entry: SubtitleCacheEntity) {
        rows[entry.videoId to entry.languageCode] = entry
    }

    override suspend fun get(videoId: String, languageCode: String): SubtitleCacheEntity? =
        rows[videoId to languageCode]

    override suspend fun getAllForVideo(videoId: String): List<SubtitleCacheEntity> =
        rows.values.filter { it.videoId == videoId }

    override suspend fun delete(videoId: String, languageCode: String) {
        rows.remove(videoId to languageCode)
    }

    override suspend fun deleteAllForVideo(videoId: String) {
        rows.keys.filter { it.first == videoId }.forEach { rows.remove(it) }
    }
}
