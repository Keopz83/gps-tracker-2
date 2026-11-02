package com.example.gpstracker.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatisticsTest {
    private val zone: ZoneId = ZoneOffset.UTC

    private fun run(date: String, hour: Int, type: ActivityType, km: Double, minutes: Long) = RunRecord(
        activityType = type,
        startMillis = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        elapsedMillis = minutes * 60_000,
        distanceMeters = km * 1000,
    )

    @Test
    fun `REQ-29 a period starts on its first day, weeks on Monday`() {
        val sunday = LocalDate.parse("2026-11-01")
        assertEquals(sunday, periodStart(StatsSpan.DAY, sunday))
        assertEquals(LocalDate.parse("2026-10-26"), periodStart(StatsSpan.WEEK, sunday))
        assertEquals(LocalDate.parse("2026-11-01"), periodStart(StatsSpan.MONTH, sunday))
        assertEquals(LocalDate.parse("2026-01-01"), periodStart(StatsSpan.YEAR, sunday))
    }

    @Test
    fun `REQ-29 the arrows step a whole period back or forward`() {
        assertEquals(LocalDate.parse("2026-10-19"), shiftPeriod(StatsSpan.WEEK, LocalDate.parse("2026-10-26"), -1))
        assertEquals(LocalDate.parse("2026-12-01"), shiftPeriod(StatsSpan.MONTH, LocalDate.parse("2026-11-01"), 1))
        assertEquals(LocalDate.parse("2025-01-01"), shiftPeriod(StatsSpan.YEAR, LocalDate.parse("2026-01-01"), -1))
    }

    @Test
    fun `REQ-29 the period is titled by its days, month or year`() {
        val today = LocalDate.parse("2026-11-02")
        assertEquals("Today", formatPeriod(StatsSpan.DAY, today, today))
        assertEquals("Yesterday", formatPeriod(StatsSpan.DAY, today.minusDays(1), today))
        assertEquals("Fri 2026-10-30", formatPeriod(StatsSpan.DAY, today.minusDays(3), today))
        assertEquals("2026-10-26 – 11-01", formatPeriod(StatsSpan.WEEK, LocalDate.parse("2026-10-26"), today))
        assertEquals("2025-12-29 – 2026-01-04", formatPeriod(StatsSpan.WEEK, LocalDate.parse("2025-12-29"), today))
        assertEquals("November 2026", formatPeriod(StatsSpan.MONTH, LocalDate.parse("2026-11-01"), today))
        assertEquals("2026", formatPeriod(StatsSpan.YEAR, LocalDate.parse("2026-01-01"), today))
    }

    @Test
    fun `REQ-29 a period totals the distance, time, climb and calories of the runs started in it`() {
        val jog = run("2026-10-27", 7, ActivityType.JOGGING, 5.0, 30)
        val ride = run("2026-10-27", 18, ActivityType.CYCLING, 20.0, 50)
        val nextWeek = run("2026-11-02", 7, ActivityType.JOGGING, 8.0, 45)
        val summaries = mapOf(
            jog.startMillis to RunSummary(40.0, 38.0, 375.0),
            ride.startMillis to RunSummary(120.0, 125.0, 600.0),
            nextWeek.startMillis to RunSummary(10.0, 10.0, 600.0),
        )

        val stats = periodStats(listOf(nextWeek, ride, jog), summaries, StatsSpan.WEEK, LocalDate.parse("2026-10-26"), zone)

        assertEquals(2, stats.runs)
        assertEquals(25_000.0, stats.distanceMeters, 0.0)
        assertEquals(80 * 60_000L, stats.elapsedMillis)
        assertEquals(160.0, stats.climbMeters, 0.0)
        assertEquals(163.0, stats.descentMeters, 0.0)
        assertEquals(975.0, stats.kcal, 0.0)
        assertEquals(1, stats.activeDays)
        assertEquals(ride, stats.longest)
        assertEquals(ActivityTotals(1, 5_000.0, 30 * 60_000L), stats.byActivity[ActivityType.JOGGING])
        assertEquals(ActivityTotals(1, 20_000.0, 50 * 60_000L), stats.byActivity[ActivityType.CYCLING])
    }

    @Test
    fun `REQ-29 a day's statistics include its calories, and a run without a summary counts as flat and burning nothing`() {
        val morning = run("2026-11-02", 7, ActivityType.JOGGING, 5.0, 30)
        val evening = run("2026-11-02", 19, ActivityType.JOGGING, 3.0, 20)
        val yesterday = run("2026-11-01", 19, ActivityType.JOGGING, 3.0, 20)

        val stats = periodStats(listOf(morning, evening, yesterday), mapOf(morning.startMillis to RunSummary(30.0, 30.0, 375.0)), StatsSpan.DAY, LocalDate.parse("2026-11-02"), zone)

        assertEquals(2, stats.runs)
        assertEquals(375.0, stats.kcal, 0.0)
        assertEquals(30.0, stats.climbMeters, 0.0)
    }

    @Test
    fun `REQ-29 an empty period has no runs and no longest run`() {
        val stats = periodStats(emptyList(), emptyMap(), StatsSpan.MONTH, LocalDate.parse("2026-02-01"), zone)

        assertEquals(0, stats.runs)
        assertNull(stats.longest)
        assertEquals(emptyMap<ActivityType, ActivityTotals>(), stats.byActivity)
        assertEquals(28, stats.bars.size)
    }

    @Test
    fun `REQ-29 the distance chart has a bar per 3 hours of a day, day of a week or month, and month of a year`() {
        val runs = listOf(
            run("2026-10-27", 7, ActivityType.JOGGING, 5.0, 30),
            run("2026-10-27", 8, ActivityType.JOGGING, 2.0, 12),
            run("2026-11-01", 18, ActivityType.CYCLING, 20.0, 50),
        )

        val day = periodStats(runs, emptyMap(), StatsSpan.DAY, LocalDate.parse("2026-10-27"), zone).bars
        assertEquals(listOf("0", "3", "6", "9", "12", "15", "18", "21"), day.map { it.label })
        assertEquals(7_000.0, day[2].distanceMeters, 0.0)

        val week = periodStats(runs, emptyMap(), StatsSpan.WEEK, LocalDate.parse("2026-10-26"), zone).bars
        assertEquals(listOf(0.0, 7_000.0, 0.0, 0.0, 0.0, 0.0, 20_000.0), week.map { it.distanceMeters })

        val year = periodStats(runs, emptyMap(), StatsSpan.YEAR, LocalDate.parse("2026-01-01"), zone).bars
        assertEquals(12, year.size)
        assertEquals(7_000.0, year[9].distanceMeters, 0.0)
        assertEquals(20_000.0, year[10].distanceMeters, 0.0)
    }

    @Test
    fun `REQ-29 running burns about 1 kcal per kg and km`() {
        assertEquals(375.0, runKcal(ActivityType.JOGGING, emptyList(), 5_000.0, 30 * 60_000L, riderKg = 75.0, bikeKg = 10.0), 0.001)
    }

    @Test
    fun `REQ-29 cycling burns the work of the estimated power through a body efficiency of 24 percent`() {
        // 30 km/h on the flat for 2 minutes with 85 kg: 151.7 W (REQ-28) × 120 s / 0.24 / 4184 J/kcal.
        val speed = 30 / 3.6
        val track = listOf((0..120 step 5).map { s -> fixAt(speed * s, s.toLong(), accuracy = 0f).copy(altitudeMeters = 100.0) })

        val kcal = runKcal(ActivityType.CYCLING, track, speed * 120, 120_000L, riderKg = 75.0, bikeKg = 10.0)

        assertEquals(151.7 * 120 / 0.24 / 4184, kcal, 0.2)
    }

    @Test
    fun `REQ-29 total times are shown in minutes, and hours from one hour`() {
        assertEquals("45 min", formatTotalTime(45 * 60_000L + 59_000))
        assertEquals("12 h 05 min", formatTotalTime((12 * 60 + 5) * 60_000L))
    }
}
