package com.keywind.exercise_counter.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.DurationUnit

class MetronomeEngine(private val scope: CoroutineScope) {

    private val random = Random(42)
    private var playbackJob: Job? = null

    fun start(beatInterval: Duration) {
        stop()

        playbackJob = scope.launch(Dispatchers.IO) {
            val tickSamples = generateTick(SAMPLE_RATE)
            val beatSeconds = beatInterval.toDouble(DurationUnit.SECONDS)
            val totalBeatSamples = (beatSeconds * SAMPLE_RATE).toInt()
            val silenceCount = (totalBeatSamples - tickSamples.size).coerceAtLeast(0)

            val beatBuffer = ShortArray(tickSamples.size + silenceCount)
            tickSamples.copyInto(beatBuffer, 0)

            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(beatBuffer.size * Short.SIZE_BYTES)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()

            try {
                while (isActive) {
                    track.write(beatBuffer, 0, beatBuffer.size)
                }
            } finally {
                track.stop()
                track.release()
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
    }

    private fun generateTick(sampleRate: Int): ShortArray {
        val partial2Hz = FUNDAMENTAL_HZ * PARTIAL2_RATIO
        val partial3Hz = FUNDAMENTAL_HZ * PARTIAL3_RATIO
        val numSamples = (sampleRate * TOCK_DURATION_MS) / 1000
        val samples = ShortArray(numSamples)
        for (i in samples.indices) {
            val t = i.toDouble() / sampleRate

            val decay = exp(-t * TONE_DECAY)

            val noiseEnvelope = exp(-t * NOISE_DECAY)
            val noise = (random.nextDouble() * 2.0 - 1.0) * NOISE_AMP * noiseEnvelope

            val tone = sin(2.0 * PI * FUNDAMENTAL_HZ * t) * FUNDAMENTAL_AMP +
                sin(2.0 * PI * partial2Hz * t) * PARTIAL2_AMP +
                sin(2.0 * PI * partial3Hz * t) * PARTIAL3_AMP

            val sample = TOCK_AMPLITUDE * decay * (tone + noise)
            samples[i] = (sample * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return samples
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val TOCK_DURATION_MS = 40
        private const val TOCK_AMPLITUDE = 0.75

        // Hollow woodblock sound — tune by adjusting FUNDAMENTAL_HZ only.
        // The partials are defined as ratios of the fundamental, modeled after
        // the inharmonic resonance modes of a hollow cylindrical body.
        private const val FUNDAMENTAL_HZ = 500.0
        private const val PARTIAL2_RATIO = 1.4   // Closer partials = woody, not bell-like
        private const val PARTIAL3_RATIO = 2.2   // Lower than metal; reduces ringing

        // Amplitude mix — strong fundamental, subdued upper partials
        private const val FUNDAMENTAL_AMP = 0.65
        private const val PARTIAL2_AMP = 0.25
        private const val PARTIAL3_AMP = 0.08

        // Decay rates — wood damps faster than metal
        private const val TONE_DECAY = 145.0
        private const val NOISE_DECAY = 1800.0
        private const val NOISE_AMP = 0.25

    }
}
