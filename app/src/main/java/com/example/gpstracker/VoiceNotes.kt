package com.example.gpstracker

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** REQ-14: speaks the kilometre notes, lowering other audio (e.g. music) while speaking. */
class VoiceNotes(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun speak(text: String) {
        if (!ready) {
            pending = text
            if (tts == null) tts = TextToSpeech(context, ::onInit)
            return
        }
        audioManager.requestAudioFocus(focus)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        engine.language = Locale.UK
        engine.setAudioAttributes(attributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                audioManager.abandonAudioFocusRequest(focus)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                audioManager.abandonAudioFocusRequest(focus)
            }
        })
        ready = true
        pending?.let { pending = null; speak(it) }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }

    private companion object {
        const val UTTERANCE_ID = "km"
    }
}
