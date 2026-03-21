package com.keywind.exercise_counter

import com.keywind.exercise_counter.ui.statusText
import com.keywind.exercise_counter.viewmodel.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test #5: PlaybackScreen.statusText() — all states produce correct strings.
 *
 * To demonstrate failure:
 * - Change "Set ${currentSet + 1}" to "Set $currentSet" → testExercisingShowsCurrentSet fails.
 * - Change "Rest — $currentSet" to "Rest — ${currentSet + 1}" → testGapShowsCompletedSets fails.
 * - Remove the DONE branch or change the string → testDoneShowsComplete fails.
 * - Swap IDLE and PAUSED returns → testIdleIsBlank and testPausedShowsPaused fail.
 */
class StatusTextTest {

    @Test
    fun testIdleIsBlank() {
        assertEquals("", statusText(PlaybackState.IDLE, currentSet = 0, totalSets = 3))
    }

    @Test
    fun testWaitingForReadyIsBlank() {
        assertEquals("", statusText(PlaybackState.WAITING_FOR_READY, currentSet = 0, totalSets = 3))
    }

    @Test
    fun testExercisingShowsCurrentSet() {
        // currentSet is 0-indexed internally; displayed as set 1 of N
        assertEquals("Set 1 of 3", statusText(PlaybackState.EXERCISING, currentSet = 0, totalSets = 3))
        assertEquals("Set 3 of 3", statusText(PlaybackState.EXERCISING, currentSet = 2, totalSets = 3))
    }

    @Test
    fun testGapShowsCompletedSets() {
        // After completing set 1, currentSet == 1 (the count of completed sets)
        assertEquals("Rest — 1 of 3 complete", statusText(PlaybackState.GAP, currentSet = 1, totalSets = 3))
        assertEquals("Rest — 2 of 3 complete", statusText(PlaybackState.GAP, currentSet = 2, totalSets = 3))
    }

    @Test
    fun testPausedShowsPaused() {
        assertEquals("Paused", statusText(PlaybackState.PAUSED, currentSet = 1, totalSets = 3))
    }

    @Test
    fun testDoneShowsComplete() {
        assertEquals("Routine complete!", statusText(PlaybackState.DONE, currentSet = 3, totalSets = 3))
    }

    @Test
    fun testSingleSetExercise() {
        assertEquals("Set 1 of 1", statusText(PlaybackState.EXERCISING, currentSet = 0, totalSets = 1))
    }
}
