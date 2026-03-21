package com.keywind.exercise_counter.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechRecognitionHelper(
    private val context: Context,
    private val onReady: () -> Unit,
) {

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()

            if (containsGoWord(matches)) {
                Log.d(TAG, "Go-word detected: $matches")
                onReady()
            } else {
                Log.d(TAG, "No go-word in: $matches")
                // The session ended naturally — reuse the same recognizer instance.
                // Do NOT call cancel() here; calling it on a completed session triggers
                // ERROR_RECOGNIZER_BUSY on many OEMs, which destroys the new session
                // before it starts and leaves nothing listening.
                if (listening) recognizer?.startListening(createIntent())
            }
        }

        override fun onError(error: Int) {
            Log.d(TAG, "Speech recognition error: $error")
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                ERROR_SERVER_DISCONNECTED,
                -> restartListening()
                else -> {
                    Log.w(TAG, "Speech recognition fatal error: $error")
                    listening = false
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            if (containsGoWord(matches)) {
                Log.d(TAG, "Go-word detected in partial: $matches")
                onReady()
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening(): Boolean {
        if (listening) return true
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available on this device")
            return false
        }

        listening = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
            startListening(createIntent())
        }
        return true
    }

    fun stopListening() {
        listening = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    fun destroy() {
        stopListening()
    }

    private fun restartListening() {
        if (!listening) return
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
            startListening(createIntent())
        }
    }

    private fun createIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

    companion object {
        private const val TAG = "SpeechRecognition"
        // ERROR_SERVER_DISCONNECTED added in API 31, value = 11
        private const val ERROR_SERVER_DISCONNECTED = 11
        internal val GO_WORDS = listOf("ready", "go", "next", "okay", "ok", "when")

        /** Returns true if any result contains a go-word (case-insensitive substring match). */
        internal fun containsGoWord(results: List<String>): Boolean =
            results.any { result -> GO_WORDS.any { word -> result.contains(word, ignoreCase = true) } }
    }
}
