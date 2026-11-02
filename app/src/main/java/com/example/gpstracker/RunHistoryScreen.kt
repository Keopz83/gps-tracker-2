package com.example.gpstracker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gpstracker.domain.ActivityType
import com.example.gpstracker.domain.LocationFix
import com.example.gpstracker.domain.RunRecord
import com.example.gpstracker.domain.formatKmPace
import com.example.gpstracker.domain.formatDistance
import com.example.gpstracker.domain.formatDuration
import com.example.gpstracker.domain.formatRunDate
import com.example.gpstracker.domain.formatSpeed
import com.example.gpstracker.domain.elevationProfile
import com.example.gpstracker.domain.paceBars
import com.example.gpstracker.domain.powerBars
import com.example.gpstracker.domain.runDetails
import com.example.gpstracker.domain.runNumber
import com.example.gpstracker.domain.selectedRuns
import com.example.gpstracker.domain.toggleSelection
import java.time.ZoneId

/**
 * REQ-9: the finished runs, newest first, one numbered box each, with a delete icon (REQ-18).
 * REQ-17: the map icon of a run shows it on the map; the run shown there is highlighted.
 * REQ-22: a tap opens or closes the details of a run; its share icon shares its GPX file. A long press selects runs
 * (then a tap selects or unselects one), and the share icon at the top right shares the selected runs together.
 * REQ-28: a cycling run's power is estimated for a rider and bike of [cyclingKg].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RunHistoryScreen(
    runs: List<RunRecord>,
    selected: RunRecord?,
    cyclingKg: Double,
    onShowOnMap: (RunRecord) -> Unit,
    onShare: (List<RunRecord>) -> Unit,
    onDelete: (RunRecord) -> Unit,
) {
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
    // REQ-22: the run whose details are open, and the runs selected to share (both by their start).
    var expanded by remember { mutableStateOf<Long?>(null) }
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    val chosen = selectedRuns(runs, selection)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // REQ-10: the title sits below the app icon and name over the corner.
            Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 44.dp))
            if (runs.isEmpty()) {
                Text("No runs yet", modifier = Modifier.padding(top = 24.dp))
                return@Column
            }
            LazyColumn(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(runs, key = { _, run -> "${run.activityType}-${run.startMillis}" }) { index, run ->
                    val checked = run.startMillis in selection
                    RunBox(
                        run = run,
                        number = runNumber(index, runs.size),
                        checked = checked,
                        expanded = expanded == run.startMillis,
                        cyclingKg = cyclingKg,
                        onShowOnMap = { onShowOnMap(run) },
                        onShare = { onShare(listOf(run)) },
                        onDelete = { deleting = run },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = if (run == selected) 0.25f else 0.1f))
                            .then(if (checked) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                            .combinedClickable(
                                onClick = {
                                    if (chosen.isNotEmpty()) {
                                        selection = toggleSelection(selection, run.startMillis)
                                    } else {
                                        expanded = run.startMillis.takeIf { it != expanded }
                                    }
                                },
                                onLongClick = { selection = toggleSelection(selection, run.startMillis) },
                            )
                            .padding(bottom = 2.dp),
                    )
                }
            }
        }
        // REQ-22: the selected runs, shared together from the top right corner.
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (chosen.isNotEmpty()) {
                Text("${chosen.size} selected", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { selection = emptySet() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                }
            }
            IconButton(onClick = { onShare(chosen) }, enabled = chosen.isNotEmpty()) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "Share selected runs as GPX",
                    tint = Color.White.copy(alpha = if (chosen.isNotEmpty()) 1f else 0.4f),
                )
            }
        }
    }
}

/**
 * REQ-9: one run: its line (see [RunLine]) above the icons to show it on the map (REQ-17), share its GPX file (REQ-22)
 * and delete it (REQ-18), next to its average pace or speed (REQ-5).
 * REQ-22: below them, when [expanded], details read from its GPX file.
 */
@Composable
private fun RunBox(
    run: RunRecord,
    number: Int,
    checked: Boolean,
    expanded: Boolean,
    cyclingKg: Double,
    onShowOnMap: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // REQ-22: while the details are open, the run's line and icons stand out above them as a less see-through header.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (expanded) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                .padding(top = 2.dp, start = 8.dp, end = 8.dp),
        ) {
            RunLine(run = run, number = number, checked = checked, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Average: ${formatSpeed(run.activityType, run.distanceMeters, run.elapsedMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onShowOnMap, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Map, contentDescription = "Show run on map", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share run as GPX", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete run", modifier = Modifier.size(20.dp))
                }
            }
        }
        if (expanded) RunDetailsView(run, cyclingKg, modifier = Modifier.padding(top = 10.dp, bottom = 14.dp, start = 16.dp, end = 16.dp))
    }
}

/**
 * REQ-23: the pace chart and the elevation chart of a run, above the time and pace of each kilometre (REQ-22).
 * REQ-28: a cycling run's estimated power chart, for a rider and bike of [cyclingKg], between the two.
 */
@Composable
private fun RunDetailsView(run: RunRecord, cyclingKg: Double, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val track by produceState<List<List<LocationFix>>?>(null, run.startMillis) {
        value = TrackingRepository.loadTrack(context.filesDir, run)
    }
    val shown = track ?: return
    val details = remember(shown) { runDetails(shown) } ?: return
    val bars = remember(shown) { paceBars(shown) }
    val elevation = remember(shown) { elevationProfile(shown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val style = MaterialTheme.typography.bodyMedium
        PaceChart(bars, run.activityType, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp))
        SectionDivider()
        if (run.activityType == ActivityType.CYCLING) {
            val power = remember(shown, cyclingKg) { powerBars(shown, cyclingKg) }
            PowerChart(power, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp))
            SectionDivider()
        }
        ElevationChart(elevation, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp))
        SectionDivider()
        details.splits.forEachIndexed { i, millis ->
            val pace = when (run.activityType) {
                ActivityType.JOGGING -> formatKmPace(millis)
                ActivityType.CYCLING -> formatSpeed(ActivityType.CYCLING, 1_000.0, millis)
            }
            Text("km ${i + 1}  ${formatDuration(millis)}  ($pace)", style = style)
        }
    }
}

/** REQ-23: the see-through line between the charts and the kilometre list. */
@Composable
private fun SectionDivider() = HorizontalDivider(
    modifier = Modifier.padding(bottom = 6.dp),
    thickness = 1.dp,
    color = Color.White.copy(alpha = 0.25f),
)

/**
 * REQ-9: one run on a single line, from left to right: order number (a check mark while selected, REQ-22), activity
 * icon, start date and time, place, and distance and duration (together, slightly highlighted, in bold).
 * REQ-17: also under the map, without the order number.
 */
@Composable
fun RunLine(run: RunRecord, modifier: Modifier = Modifier, number: Int? = null, checked: Boolean = false) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.bodyMedium
        if (checked) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", modifier = Modifier.size(18.dp))
        } else {
            number?.let { Text("$it", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
        }
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
    }
}
