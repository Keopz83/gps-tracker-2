package com.example.gpstracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gpstracker.domain.Settings
import com.example.gpstracker.domain.VoiceGender

/**
 * REQ-13: the settings view. REQ-14: the kilometre voice note and whether other audio pauses for it.
 * REQ-20: the minute voice note. REQ-21: the voice.
 */
@Composable
fun SettingsScreen(settings: Settings, onChange: (Settings) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // REQ-10: the title sits below the app icon and name over the corner.
        Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 44.dp))
        SwitchSetting(
            title = "Voice note every kilometre",
            description = "Total distance, total time and last km pace",
            checked = settings.voiceNotes,
        ) { onChange(settings.copy(voiceNotes = it)) }
        SwitchSetting(
            title = "Voice note every minute",
            description = "Time, total distance and average pace",
            checked = settings.minuteNotes,
        ) { onChange(settings.copy(minuteNotes = it)) }
        SwitchSetting(
            title = "Pause other audio",
            description = "Music pauses while a note plays, instead of getting quieter",
            checked = settings.pauseOtherAudio,
        ) { onChange(settings.copy(pauseOtherAudio = it)) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Voice", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            VoiceGender.entries.forEach { gender ->
                FilterChip(
                    selected = settings.voice == gender,
                    onClick = { onChange(settings.copy(voice = gender)) },
                    label = { Text(gender.label) },
                )
            }
        }
    }
}

@Composable
private fun SwitchSetting(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
