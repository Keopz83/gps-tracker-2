package com.example.gpstracker

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gpstracker.domain.BIKE_KG_RANGE
import com.example.gpstracker.domain.LONG_DISTANCE_SECONDS
import com.example.gpstracker.domain.RIDER_KG_RANGE
import com.example.gpstracker.domain.Settings
import com.example.gpstracker.domain.VoiceGender
import com.example.gpstracker.domain.parseWeightKg

/**
 * REQ-13: the settings view. REQ-14: the kilometre voice note and whether other audio pauses for it.
 * REQ-20: the minute voice note. REQ-25: the report when the screen is turned on. REQ-21: the voice.
 * REQ-26: long distance mode. REQ-28: the rider's and the bike's weight, for the estimated cycling power.
 */
@Composable
fun SettingsScreen(settings: Settings, onChange: (Settings) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // REQ-10: the title sits below the app icon and name over the corner.
        Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 44.dp))
        SettingsCard("Voice") {
            SwitchSetting(
                title = "Note every km",
                checked = settings.voiceNotes,
            ) { onChange(settings.copy(voiceNotes = it)) }
            SwitchSetting(
                title = "Note every minute",
                checked = settings.minuteNotes,
            ) { onChange(settings.copy(minuteNotes = it)) }
            SwitchSetting(
                title = "Report on power button",
                checked = settings.screenOnNote,
            ) { onChange(settings.copy(screenOnNote = it)) }
            SwitchSetting(
                title = "Pause music",
                checked = settings.pauseOtherAudio,
            ) { onChange(settings.copy(pauseOtherAudio = it)) }
            ChoiceSetting("Speaker", VoiceGender.entries, settings.voice, { it.label }) { onChange(settings.copy(voice = it)) }
        }
        // REQ-26: long distance mode, and how often it records a position.
        SettingsCard("GPS") {
            SwitchSetting(
                title = "Long distance",
                checked = settings.longDistance,
            ) { onChange(settings.copy(longDistance = it)) }
            if (settings.longDistance) {
                ChoiceSetting("Every", LONG_DISTANCE_SECONDS, settings.longDistanceSeconds, { "$it s" }) {
                    onChange(settings.copy(longDistanceSeconds = it))
                }
            }
        }
        // REQ-28: the weights the cycling power is estimated for.
        SettingsCard("Cycling") {
            WeightSetting("Rider", settings.riderKg, RIDER_KG_RANGE) { onChange(settings.copy(riderKg = it)) }
            WeightSetting("Bike", settings.bikeKg, BIKE_KG_RANGE) { onChange(settings.copy(bikeKg = it)) }
        }
    }
}

/** Settings grouped in a box like a run in the history (REQ-9), under a less see-through header (REQ-22). */
@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f)),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) { content() }
    }
}

@Composable
private fun <T> ChoiceSetting(title: String, choices: List<T>, selected: T, label: (T) -> String, onChange: (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        choices.forEach { choice ->
            FilterChip(selected = choice == selected, onClick = { onChange(choice) }, label = { Text(label(choice)) })
        }
    }
}

@Composable
private fun SwitchSetting(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** REQ-28: a weight in whole kg, typed in; only a number within [range] is kept, anything else is marked as wrong. */
@Composable
private fun WeightSetting(title: String, kg: Int, range: IntRange, onChange: (Int) -> Unit) {
    var text by remember(kg) { mutableStateOf("$kg") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                text = typed
                parseWeightKg(typed, range)?.let { if (it != kg) onChange(it) }
            },
            singleLine = true,
            isError = parseWeightKg(text, range) == null,
            suffix = { Text("kg") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(110.dp),
        )
    }
}
