package com.example.gpstracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gpstracker.domain.RunRecord
import com.example.gpstracker.domain.formatDistance
import com.example.gpstracker.domain.formatDuration
import com.example.gpstracker.domain.formatRunDate
import java.time.ZoneId

/**
 * REQ-9: the finished runs, newest first, one line each with date, activity, distance, duration and place.
 * REQ-17: tapping a run selects it to be shown on the map; the selected run is highlighted.
 */
@Composable
fun RunHistoryScreen(runs: List<RunRecord>, selected: RunRecord?, onSelect: (RunRecord) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("History", style = MaterialTheme.typography.titleLarge)
        if (runs.isEmpty()) {
            Text("No runs yet", modifier = Modifier.padding(top = 24.dp))
            return@Column
        }
        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            items(runs) { run ->
                RunLine(
                    run = run,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (run == selected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { onSelect(run) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                )
                HorizontalDivider()
            }
        }
    }
}

/** REQ-9: one run on a single line: start date and time, activity icon, distance, duration and place. REQ-17: also under the map. */
@Composable
fun RunLine(run: RunRecord, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.bodyMedium
        Text(formatRunDate(run.startMillis, ZoneId.systemDefault()), style = style, maxLines = 1)
        Icon(run.activityType.icon, contentDescription = run.activityType.label, modifier = Modifier.size(18.dp))
        Text(formatDistance(run.distanceMeters), style = style, maxLines = 1)
        Text(formatDuration(run.elapsedMillis), style = style, maxLines = 1)
        Text(
            run.place.orEmpty(),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
