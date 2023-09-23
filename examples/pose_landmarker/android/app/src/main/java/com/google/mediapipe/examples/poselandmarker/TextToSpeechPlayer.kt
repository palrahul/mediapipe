package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class TextToSpeechPlayer(private val context: Context) {

    private var textToSpeech: TextToSpeech? = null
    private var previousString: String? = null
    init {
        initializeTextToSpeech()
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale.getDefault()
                if (textToSpeech?.isLanguageAvailable(locale) == TextToSpeech.LANG_AVAILABLE) {
                    textToSpeech?.language = locale
                }
            }
        }
    }

    fun playText(text: String) {
        if(text != previousString) {
            Log.d("TST_AUDIO", "Playing audio")
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            previousString = text
        }

    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
