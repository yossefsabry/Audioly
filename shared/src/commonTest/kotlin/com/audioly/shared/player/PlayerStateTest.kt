package com.audioly.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerStateTest {

    @Test
    fun isEmpty_is_true_when_no_videoId() {
        assertTrue(PlayerState().isEmpty)
    }

    @Test
    fun isEmpty_is_false_when_videoId_present() {
        assertFalse(PlayerState(videoId = "abc").isEmpty)
    }

    @Test
    fun progressFraction_is_0_when_durationMs_is_0() {
        val state = PlayerState(durationMs = 0L, positionMs = 500L)
        assertEquals(0f, state.progressFraction)
    }

    @Test
    fun progressFraction_calculates_correctly() {
        val state = PlayerState(durationMs = 1000L, positionMs = 250L)
        assertEquals(0.25f, state.progressFraction)
    }

    @Test
    fun progressFraction_is_1_at_end() {
        val state = PlayerState(durationMs = 300L, positionMs = 300L)
        assertEquals(1.0f, state.progressFraction)
    }

    @Test
    fun default_state_has_no_error() {
        val state = PlayerState()
        assertNull(state.error)
    }

    @Test
    fun copy_preserves_unchanged_fields() {
        val base = PlayerState(videoId = "x", title = "Test", durationMs = 1000L)
        val updated = base.copy(positionMs = 500L)
        assertEquals("x", updated.videoId)
        assertEquals(1000L, updated.durationMs)
        assertEquals(500L, updated.positionMs)
    }
}
