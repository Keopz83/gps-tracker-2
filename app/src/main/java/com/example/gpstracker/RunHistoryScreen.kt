package com.example.gpstracker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gpstracker.domain.RunRecord
import com.example.gpstracker.domain.formatDistance
import com.example.gpstracker.domain.formatDuration
import com.example.gpstracker.domain.formatRunDate
import java.time.ZoneId

/** REQ-9: the finished runs, newest first, with their date, distance and duration. */
@Composable
fun RunHistoryScreen(runs: List<RunRecord>, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val zone = ZoneId.systemDefault()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onClose) { Text("Back") }
            Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp))
        }
        if (runs.isEmpty()) {
            Text("No runs yet", modifier = Modifier.padding(top = 24.dp))
            return@Column
        }
        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            items(runs) { run ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("${formatRunDate(run.startMillis, zone)} · ${run.activityType.label}", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(formatDistance(run.distanceMeters))
                        Text(formatDuration(run.elapsedMillis))
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
