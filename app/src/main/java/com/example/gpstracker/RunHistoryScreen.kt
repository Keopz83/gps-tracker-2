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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gpstracker.domain.RunRecord
import com.example.gpstracker.domain.formatDistance
import com.example.gpstracker.domain.formatDuration
import com.example.gpstracker.domain.formatRunDate
import com.example.gpstracker.domain.runNumber
import java.time.ZoneId

/**
 * REQ-9: the finished runs, newest first, one numbered line each, with a delete icon (REQ-18).
 * REQ-17: tapping a run selects it to be shown on the map; the selected run is highlighted.
 */
@Composable
fun RunHistoryScreen(runs: List<RunRecord>, selected: RunRecord?, onSelect: (RunRecord) -> Unit, onDelete: (RunRecord) -> Unit) {
    // REQ-18: the run waiting for the user to confirm its deletion.
    var deleting by remember { mutableStateOf<RunRecord?>(null) }
    deleting?.let { run ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = colorResource(R.color.gradient_purple),
            title = { Text("Delete run") },
            text = { Text("Delete the run of ${formatRunDate(run.startMillis, ZoneId.systemDefault())}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    onDelete(run)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // REQ-10: the title sits below the app icon and name over the corner.
        Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 44.dp))
        if (runs.isEmpty()) {
            Text("No runs yet", modifier = Modifier.padding(top = 24.dp))
            return@Column
        }
        LazyColumn(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(runs, key = { _, run -> "${run.activityType}-${run.startMillis}" }) { index, run ->
                RunLine(
                    run = run,
                    number = runNumber(index, runs.size),
                    onDelete = { deleting = run },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = if (run == selected) 0.25f else 0.1f))
                        .clickable { onSelect(run) }
                        .padding(vertical = 2.dp, horizontal = 8.dp),
                )
            }
        }
    }
}

/**
 * REQ-9: one run on a single line, from left to right: order number, activity icon, start date and time, place,
 * distance and duration (together, slightly highlighted, in bold), and the delete icon (REQ-18).
 * REQ-17: also under the map, without the order number and delete icon.
 */
@Composable
fun RunLine(run: RunRecord, modifier: Modifier = Modifier, number: Int? = null, onDelete: (() -> Unit)? = null) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.bodyMedium
        number?.let { Text("$it", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
        Icon(run.activityType.icon, contentDescription = run.activityType.label, modifier = Modifier.size(18.dp))
        Text(formatRunDate(run.startMillis, ZoneId.systemDefault()), style = style, maxLines = 1)
        Text(
            run.place.orEmpty(),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val figures = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            Text(formatDistance(run.distanceMeters), style = figures, maxLines = 1)
            Text(formatDuration(run.elapsedMillis), style = figures, maxLines = 1)
        }
        onDelete?.let {
            IconButton(onClick = it, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete run", modifier = Modifier.size(20.dp))
            }
        }
    }
}
