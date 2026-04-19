package com.audioly.app.player

import com.audioly.shared.player.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStateTest {

    @Test
    fun `isEmpty is true when no videoId`() {
        assertTrue(PlayerState().isEmpty)
    }

    @Test
    fun `isEmpty is false when videoId present`() {
        assertFalse(PlayerState(videoId = "abc").isEmpty)
    }

    @Test
    fun `progressFraction is 0 when durationMs is 0`() {
        val state = PlayerState(durationMs = 0L, positionMs = 500L)
        assertEquals(0f, state.progressFraction)
    }

    @Test
    fun `progressFraction calculates correctly`() {
        val state = PlayerState(durationMs = 1000L, positionMs = 250L)
        assertEquals(0.25f, state.progressFraction)
    }

    @Test
    fun `progressFraction is 1 at end`() {
        val state = PlayerState(durationMs = 300L, positionMs = 300L)
        assertEquals(1.0f, state.progressFraction)
    }

    @Test
    fun `default state has no error`() {
        val state = PlayerState()
        assertEquals(null, state.error)
    }

    @Test
    fun `copy preserves unchanged fields`() {
        val base = PlayerState(videoId = "x", title = "Test", durationMs = 1000L)
        val updated = base.copy(positionMs = 500L)
        assertEquals("x", updated.videoId)
        assertEquals(1000L, updated.durationMs)
        assertEquals(500L, updated.positionMs)
    }
}
