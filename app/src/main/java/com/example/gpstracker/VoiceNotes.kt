package com.example.gpstracker

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.gpstracker.domain.VoiceGender
import com.example.gpstracker.domain.VoiceOption
import com.example.gpstracker.domain.pickVoice
import java.util.Locale

/**
 * REQ-14, REQ-20: speaks the voice notes one after the other. While they play, other audio (e.g. music) is paused,
 * or only lowered when the user prefers that. REQ-21: in the female or male voice chosen.
 */
class VoiceNotes(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<String>()

    /** REQ-21: the engine in use (null: the phone's default one) and, per gender, the engines found without such a voice. */
    private var enginePackage: String? = null
    private val enginesWithout = mutableMapOf<VoiceGender, MutableSet<String>>()

    // Audio focus is held from the first note until the last queued one is done; the counter is only used on the main thread.
    private var focus: AudioFocusRequest? = null
    private var speaking = 0
    private var nextId = 0

    fun speak(text: String) {
        if (!ready) {
            pending += text
            if (tts == null) tts = TextToSpeech(context, ::onInit, enginePackage)
            return
        }
        val settings = SettingsRepository.settings.value
        if (!applyVoice(settings.voice) && switchEngine(settings.voice)) {
            pending += text
            return
        }
        if (focus == null) {
            // REQ-14: the system decides how far ducked audio is lowered, so pausing it is the only way to go quieter.
            val gain = if (settings.pauseOtherAudio) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            focus = AudioFocusRequest.Builder(gain).setAudioAttributes(attributes).build().also { audioManager.requestAudioFocus(it) }
        }
        speaking++
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "note-${nextId++}")
    }

    /** REQ-21: switches to an installed voice of [gender]; false (keeping the current voice) when the engine has none. */
    private fun applyVoice(gender: VoiceGender): Boolean {
        val engine = tts ?: return false
        val voices = engine.voices.orEmpty()
        val options = voices.map {
            VoiceOption(
                name = it.name,
                languageTag = it.locale.toLanguageTag(),
                needsNetwork = it.isNetworkConnectionRequired,
                installed = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty(),
            )
        }
        val chosen = pickVoice(options, gender)?.let { option -> voices.first { it.name == option.name } }
        if (chosen != null && chosen.name != engine.voice?.name) engine.voice = chosen
        return chosen != null
    }

    /**
     * REQ-21: the phone's default engine may lack a voice of [gender] (e.g. Samsung's only ships female English voices),
     * so another installed engine, such as Google's, is tried; false when every engine has been tried.
     */
    private fun switchEngine(gender: VoiceGender): Boolean {
        val engine = tts ?: return false
        val tried = enginesWithout.getOrPut(gender) { mutableSetOf() }
        tried += enginePackage ?: engine.defaultEngine
        val next = engine.engines.map { it.name }.firstOrNull { it !in tried } ?: return false
        engine.shutdown()
        enginePackage = next
        ready = false
        tts = TextToSpeech(context, ::onInit, next)
        return true
    }

    private fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            // REQ-21: an engine switched to that does not start is given up for the phone's default one.
            val failed = enginePackage ?: return
            VoiceGender.entries.forEach { enginesWithout.getOrPut(it) { mutableSetOf() } += failed }
            enginePackage = null
            tts = TextToSpeech(context, ::onInit)
            return
        }
        engine.language = Locale.UK
        engine.setAudioAttributes(attributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finished()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished()
        })
        ready = true
        pending.toList().also { pending.clear() }.forEach(::speak)
    }

    /** Called on a text-to-speech thread; the focus is given back on the main thread once nothing is left to say. */
    private fun finished() {
        context.mainExecutor.execute {
            speaking = (speaking - 1).coerceAtLeast(0)
            if (speaking == 0) releaseFocus()
        }
    }

    private fun releaseFocus() {
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
        speaking = 0
        releaseFocus()
    }
}
