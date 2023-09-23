package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

class TextToSpeechPlayer(private val context: Context) {

    private var textToSpeech: TextToSpeech? = null
    private var previousString: String? = null
    private var ongoingUtterance: String? = null
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

        // Set up an UtteranceProgressListener to detect when speaking is done
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {
                // Call the shutdown function when speaking is done
                shutdown()
                ongoingUtterance = null
                Log.d("TST_AUDIO", "Done Playing for:  $utteranceId")
            }

            override fun onError(utteranceId: String?) {
                // Handle TTS errors if needed
            }

            override fun onStart(utteranceId: String?) {
                // Utterance started
            }
        })
    }

    fun playText(text: String) {
        Log.d("TST_AUDIO", "playText")
        //we start playing TTS only if the previous one has already done playing
        if(ongoingUtterance == null) {
            // Use a unique utterance ID to identify this utterance
            val utteranceId = UUID.randomUUID().toString()

            //uncomment if we want to avoid repeating the same msg
            if(text != previousString) {
                Log.d("TST_AUDIO", "Playing audio")
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                previousString = text
            }
        }


    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
