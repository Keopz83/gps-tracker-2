package com.example.gpstracker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gpstracker.domain.Settings

/** REQ-13: the settings view. REQ-14: the voice note option. */
@Composable
fun SettingsScreen(settings: Settings, onChange: (Settings) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onClose) { Text("Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Voice note every kilometre", style = MaterialTheme.typography.titleMedium)
                Text("Total distance, total time and last km pace", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = settings.voiceNotes, onCheckedChange = { onChange(settings.copy(voiceNotes = it)) })
        }
    }
}
