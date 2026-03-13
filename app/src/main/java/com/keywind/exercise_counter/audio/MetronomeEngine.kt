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
import kotlin.math.sin

class MetronomeEngine {

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var isPlaying = false

    fun start(beatIntervalSeconds: Int) {
        stop()
        isPlaying = true

        playbackJob = scope.launch {
            val tickSamples = generateTick(SAMPLE_RATE)
            val totalBeatSamples = beatIntervalSeconds * SAMPLE_RATE
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

            audioTrack = track
            track.play()

            try {
                while (isActive && isPlaying) {
                    track.write(beatBuffer, 0, beatBuffer.size)
                }
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: IllegalStateException) {
                    // Already stopped/released by stop()
                }
                audioTrack = null
            }
        }
    }

    fun stop() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: IllegalStateException) {
            // Already stopped or released
        }
        audioTrack = null
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

        private val random = java.util.Random(42)

        private fun generateTick(sampleRate: Int): ShortArray {
            val partial2Hz = FUNDAMENTAL_HZ * PARTIAL2_RATIO
            val partial3Hz = FUNDAMENTAL_HZ * PARTIAL3_RATIO
            val numSamples = (sampleRate * TOCK_DURATION_MS) / 1000
            val samples = ShortArray(numSamples)
            for (i in samples.indices) {
                val t = i.toDouble() / sampleRate

                // Wood damps faster than metal
                val decay = kotlin.math.exp(-t * 145.0)

                // Short, dull impact transient (wood stick, not metal)
                val noiseEnvelope = kotlin.math.exp(-t * 1800.0)
                val noise = (random.nextDouble() * 2.0 - 1.0) * 0.25 * noiseEnvelope

                // Hollow body resonance: strong fundamental, subdued upper partials
                val tone = sin(2.0 * PI * FUNDAMENTAL_HZ * t) * 0.65 +
                    sin(2.0 * PI * partial2Hz * t) * 0.25 +
                    sin(2.0 * PI * partial3Hz * t) * 0.08

                val sample = TOCK_AMPLITUDE * decay * (tone + noise)
                samples[i] = (sample * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            return samples
        }
    }
}
