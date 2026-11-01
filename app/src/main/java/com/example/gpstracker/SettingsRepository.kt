package com.example.gpstracker

import android.content.Context
import com.example.gpstracker.domain.Settings
import com.example.gpstracker.domain.VoiceGender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** REQ-13: the settings, kept across app restarts and shared by the service and the UI. */
object SettingsRepository {
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = prefs(context)
        val defaults = Settings()
        _settings.value = Settings(
            voiceNotes = prefs.getBoolean(KEY_VOICE_NOTES, defaults.voiceNotes),
            minuteNotes = prefs.getBoolean(KEY_MINUTE_NOTES, defaults.minuteNotes),
            voice = VoiceGender.entries.firstOrNull { it.name == prefs.getString(KEY_VOICE, null) } ?: defaults.voice,
            pauseOtherAudio = prefs.getBoolean(KEY_PAUSE_OTHER_AUDIO, defaults.pauseOtherAudio),
        )
    }

    fun update(context: Context, settings: Settings) {
        prefs(context).edit()
            .putBoolean(KEY_VOICE_NOTES, settings.voiceNotes)
            .putBoolean(KEY_MINUTE_NOTES, settings.minuteNotes)
            .putString(KEY_VOICE, settings.voice.name)
            .putBoolean(KEY_PAUSE_OTHER_AUDIO, settings.pauseOtherAudio)
            .apply()
        _settings.value = settings
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "settings"
    private const val KEY_VOICE_NOTES = "voiceNotes"
    private const val KEY_MINUTE_NOTES = "minuteNotes"
    private const val KEY_VOICE = "voice"
    private const val KEY_PAUSE_OTHER_AUDIO = "pauseOtherAudio"
}
