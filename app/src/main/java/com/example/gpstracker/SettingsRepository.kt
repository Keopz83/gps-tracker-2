package com.example.gpstracker

import android.content.Context
import com.example.gpstracker.domain.Settings
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
        _settings.value = Settings(voiceNotes = prefs(context).getBoolean(KEY_VOICE_NOTES, false))
    }

    fun update(context: Context, settings: Settings) {
        prefs(context).edit().putBoolean(KEY_VOICE_NOTES, settings.voiceNotes).apply()
        _settings.value = settings
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "settings"
    private const val KEY_VOICE_NOTES = "voiceNotes"
}
