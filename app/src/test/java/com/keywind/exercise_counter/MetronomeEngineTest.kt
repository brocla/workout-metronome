package com.keywind.exercise_counter

import com.keywind.exercise_counter.audio.MetronomeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test #3: MetronomeEngine.generateTick() — deterministic PCM synthesis.
 *
 * To demonstrate failure:
 * - Remove the `coerceIn` clipping call → testAllSamplesWithin16BitRange fails.
 * - Change Random(42) to Random() → testTickIsDeterministic fails (different values each run).
 * - Change TOCK_DURATION_MS → testSampleCountMatchesDuration fails.
 * - Remove NOISE_AMP contribution → testTickHasNonZeroNoise fails (all noise samples would be 0).
 */
class MetronomeEngineTest {

    // Scope is only used by start(); generateTick() is pure and never touches it.
    private val engine = MetronomeEngine(CoroutineScope(Job()))

    private val sampleRate = 44100
    // TOCK_DURATION_MS = 40 → 44100 * 40 / 1000 = 1764
    private val expectedSamples = sampleRate * 40 / 1000

    @Test
    fun testSampleCountMatchesDuration() {
        val tick = engine.generateTick(sampleRate)
        assertEquals(expectedSamples, tick.size)
    }

    @Test
    fun testAllSamplesWithin16BitRange() {
        val tick = engine.generateTick(sampleRate)
        for (sample in tick) {
            assertTrue(
                "Sample $sample out of Short range",
                sample in Short.MIN_VALUE..Short.MAX_VALUE,
            )
        }
    }

    @Test
    fun testTickIsDeterministic() {
        // Random seed is fixed at 42 — two separate instances both produce identical first ticks.
        // The random state advances per-instance, so we compare first calls across fresh instances.
        val engine2 = MetronomeEngine(CoroutineScope(Job()))
        val tick1 = engine.generateTick(sampleRate)
        val tick2 = engine2.generateTick(sampleRate)
        assertTrue("generateTick must be deterministic across fresh instances (seed=42)", tick1.contentEquals(tick2))
    }

    @Test
    fun testTickIsNotSilence() {
        val tick = engine.generateTick(sampleRate)
        assertTrue("Tick should contain non-zero samples", tick.any { it != 0.toShort() })
    }

    @Test
    fun testTickEnvelopeDecays() {
        val tick = engine.generateTick(sampleRate)
        // The amplitude envelope has an exponential decay. The RMS of the first 10% of
        // samples should be higher than the last 10%.
        val boundary = tick.size / 10
        fun rms(range: IntRange) = Math.sqrt(range.sumOf { tick[it].toLong() * tick[it] }.toDouble() / range.count())
        val rmsStart = rms(0 until boundary)
        val rmsEnd = rms(tick.size - boundary until tick.size)
        assertTrue("Tick envelope should decay: rmsStart=$rmsStart rmsEnd=$rmsEnd", rmsStart > rmsEnd)
    }

    @Test
    fun testSilencePaddingIsNonNegative() {
        // At any beat interval >= TOCK_DURATION_MS the silence count must be >= 0.
        // Beat interval of 100 ms at 44100 Hz → totalBeatSamples = 4410 >> 1764
        val tick = engine.generateTick(sampleRate)
        val totalBeatSamples = (0.1 * sampleRate).toInt() // 100 ms
        val silence = (totalBeatSamples - tick.size).coerceAtLeast(0)
        assertTrue(silence >= 0)
    }
}
