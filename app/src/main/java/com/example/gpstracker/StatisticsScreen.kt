package com.example.gpstracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gpstracker.domain.ActivityTotals
import com.example.gpstracker.domain.ActivityType
import com.example.gpstracker.domain.DistanceBar
import com.example.gpstracker.domain.RunRecord
import com.example.gpstracker.domain.RunSummary
import com.example.gpstracker.domain.Settings
import com.example.gpstracker.domain.StatsSpan
import com.example.gpstracker.domain.formatDistance
import com.example.gpstracker.domain.formatPeriod
import com.example.gpstracker.domain.formatRunDate
import com.example.gpstracker.domain.formatSpeed
import com.example.gpstracker.domain.formatTotalTime
import com.example.gpstracker.domain.periodStart
import com.example.gpstracker.domain.periodStats
import com.example.gpstracker.domain.shiftPeriod
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/**
 * REQ-29: the statistics of the runs of a day, week, month or year: totals, the distance over the period and the
 * totals of each activity. The arrows step to the previous or next period, up to the current one.
 */
@Composable
fun StatisticsScreen(runs: List<RunRecord>, settings: Settings) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var span by rememberSaveable { mutableStateOf(StatsSpan.WEEK) }
    var start by rememberSaveable { mutableStateOf(periodStart(StatsSpan.WEEK, today)) }
    val riderKg = settings.riderKg.toDouble()
    val bikeKg = settings.bikeKg.toDouble()
    val summaries by produceState(emptyMap<Long, RunSummary>(), runs, riderKg, bikeKg) {
        value = TrackingRepository.summaries(context.filesDir, runs, riderKg, bikeKg)
    }
    val stats = remember(runs, summaries, span, start) { periodStats(runs, summaries, span, start, zone) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // REQ-10: the title sits below the app icon and name over the corner.
        Text("Statistics", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 44.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsSpan.entries.forEach { choice ->
                FilterChip(
                    selected = choice == span,
                    onClick = {
                        // Each span opens on its current period.
                        span = choice
                        start = periodStart(choice, today)
                    },
                    label = { Text(choice.label) },
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { start = shiftPeriod(span, start, -1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous ${span.label.lowercase()}")
            }
            Text(
                formatPeriod(span, start, today),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            val hasNext = shiftPeriod(span, start, 1) <= today
            IconButton(onClick = { start = shiftPeriod(span, start, 1) }, enabled = hasNext) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next ${span.label.lowercase()}",
                    tint = Color.White.copy(alpha = if (hasNext) 1f else 0.3f),
                )
            }
        }

        StatsCard("Totals") {
            TileRow(
                "Distance" to formatDistance(stats.distanceMeters),
                "Time" to formatTotalTime(stats.elapsedMillis),
            )
            TileRow(
                "Climbed" to "${stats.climbMeters.roundToInt()} m",
                "Descended" to "${stats.descentMeters.roundToInt()} m",
            )
            // Calories only for a day; longer periods show the days with a run instead.
            TileRow(
                "Runs" to "${stats.runs}",
                if (span == StatsSpan.DAY) "Calories" to "${stats.kcal.roundToInt()} kcal" else "Active days" to "${stats.activeDays}",
            )
            stats.longest?.let { longest ->
                Text(
                    "Longest: ${formatDistance(longest.distanceMeters)}, ${formatRunDate(longest.startMillis, zone)}" +
                        (longest.place?.let { " $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        StatsCard("Distance (km)") {
            DistanceChart(stats.bars, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }

        if (stats.runs == 0) {
            Text("No runs in this ${span.label.lowercase()}", modifier = Modifier.padding(top = 16.dp))
        }
        stats.byActivity.forEach { (type, totals) -> ActivityCard(type, totals) }
    }
}

/** REQ-29: the totals of one activity: runs, distance, time and the average pace (speed when cycling, REQ-5). */
@Composable
private fun ActivityCard(type: ActivityType, totals: ActivityTotals) = StatsCard(type.label, type.icon) {
    TileRow(
        "Runs" to "${totals.runs}",
        "Distance" to formatDistance(totals.distanceMeters),
    )
    TileRow(
        "Time" to formatTotalTime(totals.elapsedMillis),
        "Average" to formatSpeed(type, totals.distanceMeters, totals.elapsedMillis),
    )
}

/** REQ-29: statistics grouped in a box like a run in the history (REQ-9), under a less see-through header. */
@Composable
private fun StatsCard(
    title: String,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) { content() }
    }
}

/** REQ-29: two figures side by side, each a small label above a large, bold value. */
@Composable
private fun TileRow(vararg tiles: Pair<String, String>) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        tiles.forEach { (label, value) ->
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

private val BAR_LABEL = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)

/** REQ-29: the distance of each part of the period as bars, the longest labelled with its km. */
@Composable
private fun DistanceChart(bars: List<DistanceBar>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val highest = bars.maxOfOrNull { it.distanceMeters } ?: 0.0
    Canvas(modifier = modifier.height(110.dp)) {
        val labelHeight = 16.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - labelHeight
        val slot = size.width / bars.size
        drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, bottom), Offset(size.width, bottom))
        // Months have up to 31 days, so only every fifth day (and the first) is labelled there.
        val labelEvery = if (bars.size > 12) 5 else 1
        bars.forEachIndexed { i, bar ->
            val left = i * slot
            if (bar.distanceMeters > 0) {
                val height = ((bottom - top) * bar.distanceMeters / highest).toFloat()
                drawRect(
                    Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(left + slot * 0.15f, bottom - height),
                    size = Size(slot * 0.7f, height),
                )
                if (bar.distanceMeters == highest) {
                    val layout = measurer.measure(String.format(Locale.US, "%.1f", highest / 1000), BAR_LABEL)
                    val x = (left + slot / 2 - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(x, bottom - height - layout.size.height))
                }
            }
            if (i == 0 || (i + 1) % labelEvery == 0) {
                val layout = measurer.measure(bar.label, BAR_LABEL)
                drawText(layout, topLeft = Offset(left + slot / 2 - layout.size.width / 2f, bottom + 2.dp.toPx()))
            }
        }
    }
}
