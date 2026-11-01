package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunChartsTest {

    @Test
    fun `REQ-23 pace bars last whole minutes, at most 40 per run`() {
        assertEquals(60_000L, paceBarMillis(30 * 60_000L))
        assertEquals(60_000L, paceBarMillis(40 * 60_000L))
        assertEquals(120_000L, paceBarMillis(41 * 60_000L))
        assertEquals(180_000L, paceBarMillis(2 * 3_600_000L))
    }

    @Test
    fun `REQ-23 each pace bar holds the pace of its minute of active time, pauses left out`() {
        val track = listOf(
            // 200 m in the first minute (5:00 /km), 300 m in the second (3:20 /km).
            listOf(fixAt(0.0, 0, accuracy = 0f), fixAt(100.0, 30, accuracy = 0f), fixAt(200.0, 60, accuracy = 0f), fixAt(500.0, 120, accuracy = 0f)),
            // Paused for 10 minutes; then standing still for a minute.
            listOf(fixAt(600.0, 720, accuracy = 0f), fixAt(600.0, 780, accuracy = 0f)),
        )

        val bars = paceBars(track)

        assertEquals(listOf(0L, 60_000L, 120_000L), bars.map { it.startMillis })
        assertEquals(180_000L, bars.last().endMillis)
        assertEquals(300_000.0, bars[0].paceMillisPerKm!!.toDouble(), 1_000.0)
        assertEquals(200_000.0, bars[1].paceMillisPerKm!!.toDouble(), 1_000.0)
        assertNull(bars[2].paceMillisPerKm)
    }

    @Test
    fun `REQ-23 the last unfinished minute gets no bar`() {
        val track = listOf(listOf(fixAt(0.0, 0, accuracy = 0f), fixAt(200.0, 60, accuracy = 0f), fixAt(300.0, 90, accuracy = 0f)))

        assertEquals(listOf(PaceBar(0, 60_000, 300_000)), paceBars(track).map { it.copy(paceMillisPerKm = Math.round(it.paceMillisPerKm!! / 1000.0) * 1000) })
    }

    @Test
    fun `REQ-23 the time axis is labelled every whole 1, 2, 5, 10 or more minutes, about 5 labels`() {
        val minute = 60_000L
        assertEquals((1L..6).map { it * minute }, timeTicks(6 * minute))
        assertEquals(listOf(5L, 10, 15, 20, 25).map { it * minute }, timeTicks(26 * minute))
        assertEquals(listOf(10L, 20, 30, 40, 50).map { it * minute }, timeTicks(52 * minute))
        assertEquals(listOf(2L, 4, 6, 8).map { it * minute }, timeTicks(9 * minute))
        assertEquals(listOf(20L, 40, 60, 80, 100, 120).map { it * minute }, timeTicks(125 * minute))
        assertEquals(listOf("5 min", "1 h", "1 h 30"), listOf(5 * minute, 60 * minute, 90 * minute).map(::formatTimeTick))
    }

    @Test
    fun `REQ-23 a run without movement has no pace bars`() {
        assertTrue(paceBars(emptyList()).isEmpty())
        assertTrue(paceBars(listOf(listOf(fixAt(0.0, 0)))).isEmpty())
    }

    @Test
    fun `REQ-23 the pace axis is inverted, from the fastest whole minute down to a minute below the slowest`() {
        fun bar(pace: Long?) = PaceBar(0, 60_000, pace)
        assertEquals(PaceAxis(300_000, 420_000), paceAxis(listOf(bar(300_000), bar(330_000), bar(null))))
        // One minute of standing nearly still does not flatten the other bars.
        assertEquals(PaceAxis(240_000, 660_000), paceAxis(listOf(bar(290_000), bar(300_000), bar(3_000_000))))
        assertNull(paceAxis(listOf(bar(null))))
    }

    @Test
    fun `REQ-23 the elevation profile follows active time and skips positions without altitude`() {
        val track = listOf(
            listOf(fixAt(0.0, 0).copy(altitudeMeters = 400.0), fixAt(100.0, 60).copy(altitudeMeters = 410.0)),
            listOf(fixAt(200.0, 600), fixAt(300.0, 660).copy(altitudeMeters = 405.0)),
        )

        assertEquals(
            listOf(ElevationPoint(0, 400.0), ElevationPoint(60_000, 410.0), ElevationPoint(120_000, 405.0)),
            elevationProfile(track),
        )
    }

    @Test
    fun `REQ-23 the altitude is saved in the GPX file and read back`() {
        val track = listOf(listOf(fixAt(0.0, 0, accuracy = 0f).copy(altitudeMeters = 408.25), fixAt(100.0, 60, accuracy = 0f)))

        val gpx = toGpx(ActivityType.JOGGING, track)!!
        val read = parseGpxTrack(gpx).single()

        assertTrue(gpx.contains("<ele>408.3</ele><time>"))
        assertEquals(408.3, read[0].altitudeMeters!!, 0.001)
        assertNull(read[1].altitudeMeters)
    }
}
