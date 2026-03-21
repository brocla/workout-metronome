package com.keywind.exercise_counter.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import java.util.Locale

class VoiceAnnouncer private constructor(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private val ready = CompletableDeferred<Boolean>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setPitch(0.85f)
            tts.setSpeechRate(0.9f)
            ready.complete(true)
        } else {
            Log.w(TAG, "TTS init failed with status $status — voice announcements disabled")
            ready.complete(false)
        }
    }

    suspend fun announce(text: String) {
        if (!ready.await()) return
        val utteranceId = "announce_${System.nanoTime()}"
        val done = CompletableDeferred<Unit>()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) done.complete(Unit)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) done.complete(Unit)
            }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        done.await()
    }

    fun stop() {
        tts.stop()
    }

    companion object {
        private const val TAG = "VoiceAnnouncer"

        @Volatile
        private var instance: VoiceAnnouncer? = null

        fun getInstance(context: Context): VoiceAnnouncer =
            instance ?: synchronized(this) {
                instance ?: VoiceAnnouncer(context).also { instance = it }
            }
    }
}
