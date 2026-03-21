package com.keywind.exercise_counter

import com.keywind.exercise_counter.viewmodel.PlaybackState
import com.keywind.exercise_counter.viewmodel.PlaybackViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test #10: Process death state recovery — PlaybackViewModel.recoverState().
 *
 * After process death the exercises list is empty and cannot be restored.
 * Any active state must reset to IDLE so the user taps play again.
 * IDLE and DONE are the only states that survive a process kill.
 *
 * To demonstrate failure:
 * - Change the condition to include DONE in the reset list → testDoneIsPreserved fails.
 * - Remove PAUSED from the reset condition → testPausedResetsToIdle fails.
 * - Return the raw deserialized state instead of IDLE for active states →
 *   testExercisingResetsToIdle, testGapResetsToIdle, testWaitingForReadyResetsToIdle,
 *   testPausedResetsToIdle all fail.
 * - Change unknown string fallback from IDLE to DONE → testUnknownStringFallsBackToIdle fails.
 */
class ProcessDeathRecoveryTest {

    @Test
    fun testIdleIsPreserved() {
        assertEquals(PlaybackState.IDLE, PlaybackViewModel.recoverState(PlaybackState.IDLE.name))
    }

    @Test
    fun testDoneIsPreserved() {
        assertEquals(PlaybackState.DONE, PlaybackViewModel.recoverState(PlaybackState.DONE.name))
    }

    @Test
    fun testExercisingResetsToIdle() {
        assertEquals(PlaybackState.IDLE, PlaybackViewModel.recoverState(PlaybackState.EXERCISING.name))
    }

    @Test
    fun testGapResetsToIdle() {
        assertEquals(PlaybackState.IDLE, PlaybackViewModel.recoverState(PlaybackState.GAP.name))
    }

    @Test
    fun testPausedResetsToIdle() {
        assertEquals(PlaybackState.IDLE, PlaybackViewModel.recoverState(PlaybackState.PAUSED.name))
    }

    @Test
    fun testWaitingForReadyResetsToIdle() {
        assertEquals(PlaybackState.IDLE, PlaybackViewModel.recoverState(PlaybackState.WAITING_FOR_READY.name))
    }

    @Test
    fun testUnknownStringFallsBackToIdle() {
        assertEquals(PlaybackState.IDLE, PlaybackViewModel.recoverState("UNKNOWN_STATE"))
        assertEquals(PlaybackState.IDLE, PlaybackViewModel.recoverState(""))
        assertEquals(PlaybackState.IDLE, PlaybackViewModel.recoverState("exercising")) // wrong case
    }
}
