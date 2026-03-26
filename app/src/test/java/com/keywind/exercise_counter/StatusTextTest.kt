package com.keywind.exercise_counter

import com.keywind.exercise_counter.data.Exercise
import com.keywind.exercise_counter.ui.exerciseSummary
import com.keywind.exercise_counter.ui.statusText
import com.keywind.exercise_counter.viewmodel.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        // During gap after set 1, currentSet == 1 (incremented before the gap)
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

    // exerciseSummary — singular/plural "set"/"sets"
    //
    // To demonstrate failure: revert to hardcoded "sets" →
    // testExerciseSummarySingularSet fails.

    @Test
    fun testExerciseSummarySingularSet() {
        val ex = Exercise(name = "Plank", sets = 1, duration = 60, gap = 5, beat = 0)
        val summary = exerciseSummary(ex)
        assertTrue("Should say '1 set' not '1 sets': $summary", summary.startsWith("1 set /"))
    }

    @Test
    fun testExerciseSummaryPluralSets() {
        val ex = Exercise(name = "Push-ups", sets = 3, duration = 30, gap = 10, beat = 1)
        val summary = exerciseSummary(ex)
        assertTrue("Should say '3 sets': $summary", summary.startsWith("3 sets /"))
    }

    @Test
    fun testExerciseSummaryFormat() {
        val ex = Exercise(name = "Squats", sets = 5, duration = 20, gap = 8, beat = 2)
        assertEquals("5 sets / 20s work / 8s rest / beat 2s", exerciseSummary(ex))
    }
}
