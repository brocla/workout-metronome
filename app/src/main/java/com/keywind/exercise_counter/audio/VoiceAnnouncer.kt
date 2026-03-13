package com.keywind.exercise_counter.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceAnnouncer(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)

    @Volatile
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setPitch(0.85f)
            tts.setSpeechRate(0.9f)
            isReady = true
        }
    }

    fun announce(text: String) {
        if (isReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "announce_${System.nanoTime()}")
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
        isReady = false
    }
}
