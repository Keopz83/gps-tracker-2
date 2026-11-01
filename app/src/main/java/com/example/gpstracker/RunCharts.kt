package com.example.gpstracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gpstracker.domain.ElevationPoint
import com.example.gpstracker.domain.PaceBar
import com.example.gpstracker.domain.formatTimeTick
import com.example.gpstracker.domain.paceAxis
import com.example.gpstracker.domain.timeTicks
import java.util.Locale
import kotlin.math.roundToInt

private val AXIS_LABEL = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
private val GRID = Color.White.copy(alpha = 0.15f)
private val TIME_LABEL = TextStyle(color = Color.White, fontSize = 11.sp)
private const val Y_LABEL_WIDTH = 40f
private const val X_LABEL_HEIGHT = 22f

/** REQ-23: the pace (min/km) of each stretch of the run, as bars against active time. */
@Composable
fun PaceChart(bars: List<PaceBar>, modifier: Modifier = Modifier) = Column(modifier = modifier) {
    Text("Pace (min/km)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
    val axis = paceAxis(bars)
    if (axis == null) {
        Text("No pace data", style = MaterialTheme.typography.bodySmall)
        return@Column
    }
    val measurer = rememberTextMeasurer()
    val end = bars.last().endMillis
    val range = axis.slowestMillis - axis.fastestMillis
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val plot = plotArea()
        // Inverted: the fastest pace at the top. A grid line each minute of pace (each two over a range above 8 minutes).
        fun y(pace: Long) = plot.top + plot.height * (pace - axis.fastestMillis) / range
        val step = if (range > 8 * 60_000L) 120_000L else 60_000L
        for (pace in axis.fastestMillis..axis.slowestMillis step step) {
            drawLine(GRID, Offset(plot.left, y(pace)), Offset(plot.right, y(pace)))
            yLabel(measurer, formatPaceTick(pace), y(pace))
        }
        bars.forEach { bar ->
            val pace = bar.paceMillisPerKm ?: return@forEach
            val top = y(pace.coerceIn(axis.fastestMillis, axis.slowestMillis))
            val left = plot.left + plot.width * bar.startMillis / end
            val width = plot.width * (bar.endMillis - bar.startMillis) / end
            drawRect(
                Color.White.copy(alpha = 0.85f),
                topLeft = Offset(left + 1f, top),
                size = Size((width - 2f).coerceAtLeast(1f), plot.bottom - top),
            )
        }
        timeAxis(measurer, end, plot)
    }
}

/** REQ-23: the altitude of the run against active time, between its lowest and highest point. */
@Composable
fun ElevationChart(points: List<ElevationPoint>, modifier: Modifier = Modifier) = Column(modifier = modifier) {
    Text("Elevation (m)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
    if (points.size < 2) {
        Text("No elevation data", style = MaterialTheme.typography.bodySmall)
        return@Column
    }
    val measurer = rememberTextMeasurer()
    val end = points.last().activeMillis.coerceAtLeast(1)
    val low = points.minOf { it.meters }
    // At least 10 m of range, so a flat run is not drawn as hills.
    val high = maxOf(points.maxOf { it.meters }, low + 10)
    Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
        val plot = plotArea()
        fun x(millis: Long) = plot.left + plot.width * millis / end
        fun y(meters: Double) = plot.bottom - (plot.height * (meters - low) / (high - low)).toFloat()
        listOf(low, high).forEach { meters ->
            drawLine(GRID, Offset(plot.left, y(meters)), Offset(plot.right, y(meters)))
            yLabel(measurer, "${meters.roundToInt()}", y(meters))
        }
        val line = Path().apply {
            moveTo(x(points.first().activeMillis), y(points.first().meters))
            points.drop(1).forEach { lineTo(x(it.activeMillis), y(it.meters)) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(points.last().activeMillis), plot.bottom)
            lineTo(x(points.first().activeMillis), plot.bottom)
            close()
        }
        drawPath(area, Color.White.copy(alpha = 0.25f))
        drawPath(line, Color.White, style = Stroke(width = 2.dp.toPx()))
        timeAxis(measurer, end, plot)
    }
}

/** The plot inside the canvas, leaving room for the y labels at the left and the time labels below. */
private class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

private fun DrawScope.plotArea() = PlotArea(
    left = Y_LABEL_WIDTH.dp.toPx(),
    top = 6.dp.toPx(),
    right = size.width - 4.dp.toPx(),
    bottom = size.height - X_LABEL_HEIGHT.dp.toPx(),
)

private fun DrawScope.yLabel(measurer: TextMeasurer, text: String, y: Float) {
    val layout = measurer.measure(text, AXIS_LABEL)
    drawText(layout, topLeft = Offset(Y_LABEL_WIDTH.dp.toPx() - layout.size.width - 4.dp.toPx(), y - layout.size.height / 2f))
}

/** Active time labels at whole minutes along the x axis (see [timeTicks]), each with a small tick mark. */
private fun DrawScope.timeAxis(measurer: TextMeasurer, end: Long, plot: PlotArea) {
    drawLine(Color.White.copy(alpha = 0.5f), Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom))
    timeTicks(end).forEach { millis ->
        val tick = plot.left + plot.width * millis / end
        drawLine(Color.White, Offset(tick, plot.bottom), Offset(tick, plot.bottom + 5.dp.toPx()), strokeWidth = 1.5.dp.toPx())
        val layout = measurer.measure(formatTimeTick(millis), TIME_LABEL)
        val x = (plot.left + plot.width * millis / end - layout.size.width / 2f)
            .coerceIn(plot.left - layout.size.width / 2f, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(x, plot.bottom + 6.dp.toPx()))
    }
}

private fun formatPaceTick(millis: Long): String {
    val seconds = millis / 1000
    return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}
