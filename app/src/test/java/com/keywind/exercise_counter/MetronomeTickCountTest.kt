package com.keywind.exercise_counter

import com.keywind.exercise_counter.audio.MetronomeEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the 11-tock bug: a 10 s exercise with beat = 1 s should
 * produce exactly 10 ticks, not 11.
 *
 * The fix: `runTickLoop` accepts a `maxTicks` parameter and the loop exits
 * after that many ticks, rather than relying on external cancellation that
 * races with the blocking `AudioTrack.write()`.
 *
 * The first test verifies the fix works with `maxTicks`.  The second test
 * proves the bug still exists without `maxTicks` (cancellation-only), guarding
 * against regressions where callers forget to pass `maxTicks`.
 */
class MetronomeTickCountTest {

    @Test
    fun `maxTicks limits loop to exactly 10 ticks`() = runBlocking {
        val engine = MetronomeEngine(CoroutineScope(Job()))

        val loopJob = launch(Dispatchers.Default) {
            engine.runTickLoop(maxTicks = 10) {
                Thread.sleep(50)
            }
        }

        loopJob.join()

        assertEquals(
            "runTickLoop(maxTicks=10) should produce exactly 10 ticks",
            10,
            engine.tickCount,
        )
    }

    @Test
    fun `without maxTicks cancellation races and produces extra tick`() = runBlocking {
        val engine = MetronomeEngine(CoroutineScope(Job()))
        val tenthTickDone = CompletableDeferred<Unit>()

        val loopJob = launch(Dispatchers.Default) {
            engine.runTickLoop {
                Thread.sleep(50)
                if (engine.tickCount >= 10) tenthTickDone.complete(Unit)
            }
        }

        tenthTickDone.await()
        delay(30)
        loopJob.cancel()
        loopJob.join()

        // The cancel-only path produces 11 — this documents the race condition.
        assertEquals(
            "Without maxTicks, the cancellation race produces an extra tick",
            11,
            engine.tickCount,
        )
    }
}
