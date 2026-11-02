package com.example.gpstracker.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** REQ-29: the time spans the statistics can be shown for. */
enum class StatsSpan(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year") }

/** REQ-29: the first day of the [span] that holds [date]; weeks start on Monday. */
fun periodStart(span: StatsSpan, date: LocalDate): LocalDate = when (span) {
    StatsSpan.DAY -> date
    StatsSpan.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    StatsSpan.MONTH -> date.withDayOfMonth(1)
    StatsSpan.YEAR -> date.withDayOfYear(1)
}

/** REQ-29: the first day of the period [by] periods after (or, negative, before) the one starting at [start]. */
fun shiftPeriod(span: StatsSpan, start: LocalDate, by: Long): LocalDate = when (span) {
    StatsSpan.DAY -> start.plusDays(by)
    StatsSpan.WEEK -> start.plusWeeks(by)
    StatsSpan.MONTH -> start.plusMonths(by)
    StatsSpan.YEAR -> start.plusYears(by)
}

/** REQ-29: the period's title, e.g. `Today`, `Sat 2026-10-31`, `2026-10-26 – 11-01`, `November 2026` or `2026`. */
fun formatPeriod(span: StatsSpan, start: LocalDate, today: LocalDate): String = when (span) {
    StatsSpan.DAY -> when (start) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofPattern("EEE yyyy-MM-dd", Locale.US).format(start)
    }
    StatsSpan.WEEK -> {
        val end = shiftPeriod(span, start, 1).minusDays(1)
        val endPattern = if (end.year == start.year) "MM-dd" else "yyyy-MM-dd"
        "${start} – ${DateTimeFormatter.ofPattern(endPattern, Locale.US).format(end)}"
    }
    StatsSpan.MONTH -> DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US).format(start)
    StatsSpan.YEAR -> "${start.year}"
}

/** REQ-29: what is read from a run's GPX file for the statistics: the metres climbed and descended and the calories. */
data class RunSummary(val climbMeters: Double, val descentMeters: Double, val kcal: Double)

/** REQ-29: the energy burnt running, about 1 kcal per kg of body weight and km, whatever the pace. */
private const val RUNNING_KCAL_PER_KG_KM = 1.0

/** REQ-29: the share of the energy burnt on a bike that reaches the pedals. */
private const val CYCLING_EFFICIENCY = 0.24
private const val JOULES_PER_KCAL = 4184.0

/**
 * REQ-29: the calories burnt on a run: running burns about 1 kcal per kg and km; cycling burns the work of the
 * estimated power (REQ-28) over the active time divided by a body efficiency of 24 %. The average power is that of the
 * power chart's bars, or, for a run too short for a bar, that of the run's average speed and climb.
 */
fun runKcal(activityType: ActivityType, track: List<List<LocationFix>>, distanceMeters: Double, elapsedMillis: Long, riderKg: Double, bikeKg: Double): Double {
    if (activityType == ActivityType.JOGGING) return RUNNING_KCAL_PER_KG_KM * riderKg * distanceMeters / 1000
    if (elapsedMillis <= 0) return 0.0
    val massKg = riderKg + bikeKg
    val bars = stretches(track).filter { it.meters >= MIN_BAR_METERS && it.movingMillis > 0 }
    val watts = if (bars.isNotEmpty()) {
        bars.sumOf { ridingWatts(it.meters * 1000.0 / it.movingMillis, it.riseMeters * 1000.0 / it.movingMillis, massKg) * it.movingMillis } /
            bars.sumOf { it.movingMillis }
    } else {
        val seconds = elapsedMillis / 1000.0
        val profile = elevationProfile(track)
        val rise = if (profile.size > 1) profile.last().meters - profile.first().meters else 0.0
        ridingWatts(distanceMeters / seconds, rise / seconds, massKg)
    }
    return watts * elapsedMillis / 1000.0 / CYCLING_EFFICIENCY / JOULES_PER_KCAL
}

/** REQ-29: the statistics' summary of a run with [track], its climb as on the elevation chart (REQ-23). */
fun runSummary(run: RunRecord, track: List<List<LocationFix>>, riderKg: Double, bikeKg: Double): RunSummary {
    val climb = climb(elevationProfile(track))
    return RunSummary(climb.upMeters, climb.downMeters, runKcal(run.activityType, track, run.distanceMeters, run.elapsedMillis, riderKg, bikeKg))
}

/** REQ-29: the totals of one activity type within a period. */
data class ActivityTotals(val runs: Int, val distanceMeters: Double, val elapsedMillis: Long)

/** REQ-29: the distance covered in one part of a period: an hour of a day, a day of a week or month, a month of a year. */
data class DistanceBar(val label: String, val distanceMeters: Double)

/** REQ-29: the statistics of the runs started within a period. */
data class PeriodStats(
    val runs: Int,
    val distanceMeters: Double,
    val elapsedMillis: Long,
    val climbMeters: Double,
    val descentMeters: Double,
    val kcal: Double,
    val activeDays: Int,
    val longest: RunRecord?,
    val byActivity: Map<ActivityType, ActivityTotals>,
    val bars: List<DistanceBar>,
)

/**
 * REQ-29: the statistics of the [runs] that started within the [span] beginning on [start] (local dates in [zone]).
 * Climb and calories come from [summaries] (by run start); a run without one counts as flat and burning nothing.
 */
fun periodStats(runs: List<RunRecord>, summaries: Map<Long, RunSummary>, span: StatsSpan, start: LocalDate, zone: ZoneId): PeriodStats {
    val end = shiftPeriod(span, start, 1)
    fun dateOf(run: RunRecord) = Instant.ofEpochMilli(run.startMillis).atZone(zone).toLocalDate()
    val inPeriod = runs.filter { dateOf(it) >= start && dateOf(it) < end }
    return PeriodStats(
        runs = inPeriod.size,
        distanceMeters = inPeriod.sumOf { it.distanceMeters },
        elapsedMillis = inPeriod.sumOf { it.elapsedMillis },
        climbMeters = inPeriod.sumOf { summaries[it.startMillis]?.climbMeters ?: 0.0 },
        descentMeters = inPeriod.sumOf { summaries[it.startMillis]?.descentMeters ?: 0.0 },
        kcal = inPeriod.sumOf { summaries[it.startMillis]?.kcal ?: 0.0 },
        activeDays = inPeriod.map(::dateOf).distinct().size,
        longest = inPeriod.maxByOrNull { it.distanceMeters },
        byActivity = ActivityType.entries.associateWith { type ->
            val ofType = inPeriod.filter { it.activityType == type }
            ActivityTotals(ofType.size, ofType.sumOf { it.distanceMeters }, ofType.sumOf { it.elapsedMillis })
        }.filterValues { it.runs > 0 },
        bars = distanceBars(inPeriod, span, start, zone),
    )
}

/**
 * REQ-29: the distance of each part of the period, by the start of each run: each 3 hours of a day (labelled 0, 3 … 21),
 * each day of a week (M T W T F S S) or month (1 … 31), each month of a year (J F M … D).
 */
private fun distanceBars(runs: List<RunRecord>, span: StatsSpan, start: LocalDate, zone: ZoneId): List<DistanceBar> {
    val labels = when (span) {
        StatsSpan.DAY -> (0 until 24 step 3).map { "$it" }
        StatsSpan.WEEK -> listOf("M", "T", "W", "T", "F", "S", "S")
        StatsSpan.MONTH -> (1..start.lengthOfMonth()).map { "$it" }
        StatsSpan.YEAR -> listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    }
    val meters = DoubleArray(labels.size)
    runs.forEach { run ->
        val time = Instant.ofEpochMilli(run.startMillis).atZone(zone)
        val index = when (span) {
            StatsSpan.DAY -> time.hour / 3
            StatsSpan.WEEK -> time.dayOfWeek.value - 1
            StatsSpan.MONTH -> time.dayOfMonth - 1
            StatsSpan.YEAR -> time.monthValue - 1
        }
        meters[index] += run.distanceMeters
    }
    return labels.mapIndexed { i, label -> DistanceBar(label, meters[i]) }
}

/** REQ-29: a total time, e.g. `45 min` or `12 h 05 min`. */
fun formatTotalTime(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes < 60) "$minutes min" else String.format(Locale.US, "%d h %02d min", minutes / 60, minutes % 60)
}
