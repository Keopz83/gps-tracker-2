package com.example.gpstracker.domain

/** REQ-23: one bar of the pace chart: the active time it covers (from the start, pauses left out) and its pace, if moving. */
data class PaceBar(val startMillis: Long, val endMillis: Long, val paceMillisPerKm: Long?) {
    /** REQ-23: the bar's speed in km/h, shown instead of the pace when cycling. */
    val speedKmh: Double? get() = paceMillisPerKm?.let { 3_600_000.0 / it }
}

/** REQ-23: one point of the elevation chart: the active time from the start (pauses left out) and the altitude. */
data class ElevationPoint(val activeMillis: Long, val meters: Double)

private const val MAX_BARS = 40
private const val MINUTE = 60_000L

/** REQ-23: the length of a pace bar for a run of [activeMillis]: whole minutes, so the chart has at most 40 bars. */
fun paceBarMillis(activeMillis: Long): Long = maxOf(1, (activeMillis + MAX_BARS * MINUTE - 1) / (MAX_BARS * MINUTE)) * MINUTE

/**
 * REQ-23: the pace of each full stretch of [paceBarMillis] active time of a run's [track]; a bar covering less than 10 m
 * has no pace. Each step between two positions counts towards the bar its middle falls in. The last, unfinished stretch
 * gets no bar.
 */
fun paceBars(track: List<List<LocationFix>>): List<PaceBar> = stretches(track).map { stretch ->
    val pace = if (stretch.meters >= MIN_BAR_METERS) Math.round(stretch.movingMillis * 1000.0 / stretch.meters) else null
    PaceBar(stretch.startMillis, stretch.endMillis, pace)
}

/**
 * REQ-23, REQ-28: what was covered in one bar of the charts: the distance, the time spent on it and the net change of
 * altitude (counting only steps between two positions with an altitude).
 */
internal class Stretch(val startMillis: Long, val endMillis: Long, val meters: Double, val movingMillis: Long, val riseMeters: Double)

/** REQ-23: a bar covering less than this many metres has no pace (or power, REQ-28). */
internal const val MIN_BAR_METERS = 10.0

/**
 * REQ-23, REQ-28: the full stretches of [paceBarMillis] active time of a run's [track]. Each step between two positions
 * counts towards the stretch its middle falls in. The last, unfinished stretch is left out.
 */
internal fun stretches(track: List<List<LocationFix>>): List<Stretch> {
    val segments = track.filter { it.size > 1 }
    val activeMillis = segments.sumOf { it.last().timeMillis - it.first().timeMillis }
    val barMillis = paceBarMillis(activeMillis)
    val count = (activeMillis / barMillis).toInt()
    if (count == 0) return emptyList()
    val meters = DoubleArray(count)
    val millis = LongArray(count)
    val rise = DoubleArray(count)
    var offset = 0L
    segments.forEach { segment ->
        val start = segment.first().timeMillis
        segment.zipWithNext { from, to ->
            val middle = offset + (from.timeMillis + to.timeMillis) / 2 - start
            val bar = (middle / barMillis).toInt()
            if (bar < count) {
                meters[bar] += haversineMeters(from, to)
                millis[bar] += to.timeMillis - from.timeMillis
                if (from.altitudeMeters != null && to.altitudeMeters != null) rise[bar] += to.altitudeMeters - from.altitudeMeters
            }
        }
        offset += segment.last().timeMillis - start
    }
    return (0 until count).map { i -> Stretch(i * barMillis, (i + 1) * barMillis, meters[i], millis[i], rise[i]) }
}

private val TICK_MINUTES = listOf(1L, 2, 5, 10, 15, 20, 30, 60, 120, 180, 240, 360)

/**
 * REQ-23: where the time axis of a chart running to [endMillis] is labelled: every whole number of minutes from the
 * steps 1, 2, 5, 10, 15, 20, 30 minutes and 1, 2, 3, 4, 6 hours, the smallest giving at most 6 labels (aiming at 5),
 * e.g. every minute for 6 minutes, every 5 minutes for 26 and every 10 minutes for 52. The start (0) is not labelled.
 */
fun timeTicks(endMillis: Long): List<Long> {
    val step = (TICK_MINUTES.firstOrNull { endMillis / (it * MINUTE) <= 6 } ?: TICK_MINUTES.last()) * MINUTE
    return (step..endMillis step step).toList()
}

/** REQ-23: a time axis label, e.g. `5 min`, `1 h` or `1 h 30`. */
fun formatTimeTick(millis: Long): String {
    val minutes = millis / MINUTE
    return when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60}"
    }
}

/** REQ-23: the altitude of each position of a run's [track] that has one, against active time; empty without altitudes. */
fun elevationProfile(track: List<List<LocationFix>>): List<ElevationPoint> {
    var offset = 0L
    return track.filter { it.isNotEmpty() }.flatMap { segment ->
        val start = segment.first().timeMillis
        val points = segment.mapNotNull { fix -> fix.altitudeMeters?.let { ElevationPoint(offset + fix.timeMillis - start, it) } }
        offset += segment.last().timeMillis - start
        points
    }
}

/** REQ-23: the metres climbed and descended over a run. */
data class Climb(val upMeters: Double, val downMeters: Double)

private const val CLIMB_THRESHOLD_METERS = 5.0

/**
 * REQ-23: the total ascent and descent of an elevation profile. GPS altitude jitters by a few metres, so a change only
 * counts once the altitude has moved at least 5 m away from the last counted point.
 */
fun climb(points: List<ElevationPoint>): Climb {
    var up = 0.0
    var down = 0.0
    var reference = points.firstOrNull()?.meters ?: return Climb(0.0, 0.0)
    points.forEach { point ->
        val change = point.meters - reference
        if (change >= CLIMB_THRESHOLD_METERS) up += change
        if (change <= -CLIMB_THRESHOLD_METERS) down -= change
        if (Math.abs(change) >= CLIMB_THRESHOLD_METERS) reference = point.meters
    }
    return Climb(up, down)
}

/** REQ-23: the pace chart's y axis, inverted: [fastestMillis] per km at the top, [slowestMillis] per km at the bottom. */
data class PaceAxis(val fastestMillis: Long, val slowestMillis: Long)

/**
 * REQ-23: the pace chart's y axis, so faster stretches get taller bars: the top is the fastest pace rounded down to a whole
 * minute; the bottom is one minute slower than the slowest pace rounded up, so every bar shows. The slowest pace counts as
 * at most twice the median pace, so a few slow bars (e.g. standing at a crossing) do not flatten the others. Null without
 * any pace.
 */
fun paceAxis(bars: List<PaceBar>): PaceAxis? {
    val paces = bars.mapNotNull { it.paceMillisPerKm }.sorted()
    if (paces.isEmpty()) return null
    val slowest = minOf(paces.last(), 2 * paces[paces.size / 2])
    return PaceAxis(paces.first() / MINUTE * MINUTE, (slowest + MINUTE - 1) / MINUTE * MINUTE + MINUTE)
}

/** REQ-23: the cycling chart's y axis, from 0 km/h at the bottom to [topKmh] at the top. */
data class SpeedAxis(val topKmh: Int)

/** REQ-23: the cycling chart's y axis: its top is the fastest speed rounded up to 5 km/h. Null without any speed. */
fun speedAxis(bars: List<PaceBar>): SpeedAxis? {
    val fastest = bars.mapNotNull { it.speedKmh }.maxOrNull() ?: return null
    return SpeedAxis(maxOf(5, (Math.ceil(fastest / 5) * 5).toInt()))
}
