package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitsTest {
    private val clock = FakeClock()

    private fun runningSession() = TrackingSession(ActivityType.JOGGING, clock::now).apply { start() }

    /** Records a position every 10 s, [metersPer10s] further north, up to [totalMeters], starting at [from]. */
    private fun TrackingSession.run(from: Double, totalMeters: Double, metersPer10s: Double, startSeconds: Long): Long {
        var meters = from
        var seconds = startSeconds
        while (meters < from + totalMeters - 1e-9) {
            meters += metersPer10s
            seconds += 10
            onLocation(fixAt(meters, seconds))
        }
        return seconds
    }

    @Test
    fun `REQ-12 there is no kilometre pace before the first kilometre is completed`() {
        val session = runningSession()
        session.onLocation(fixAt(0.0, 0))
        session.run(0.0, 990.0, 30.0, 0)

        assertEquals(emptyList<Long>(), session.stats().splits)
        assertEquals(emptyList<Split>(), recentSplits(session.stats().splits))
    }

    @Test
    fun `REQ-12 each completed kilometre records the time it took`() {
        val session = runningSession()
        session.onLocation(fixAt(0.0, 0))
        // 1st km at 50 m / 10 s (200 s), 2nd km at 40 m / 10 s (250 s).
        val t = session.run(0.0, 1_000.0, 50.0, 0)
        session.run(1_000.0, 1_000.0, 40.0, t)

        assertEquals(listOf(200_000L, 250_000L), session.stats().splits)
    }

    @Test
    fun `REQ-12 the moment a kilometre is completed is interpolated between positions`() {
        val splits = KmSplits()
        splits.add(900.0, 270_000)
        // 100 m of this 200 m step finish the kilometre: half its 60 s.
        splits.add(200.0, 60_000)

        assertEquals(listOf(300_000L), splits.splits)
    }

    @Test
    fun `REQ-12 time and distance spent paused do not count towards a kilometre`() {
        val session = runningSession()
        session.onLocation(fixAt(0.0, 0))
        session.run(0.0, 500.0, 50.0, 0) // 500 m in 100 s
        session.pause()
        session.resume()
        session.onLocation(fixAt(2_000.0, 1_000))
        session.run(2_000.0, 550.0, 50.0, 1_000) // 550 m at the same speed

        assertEquals(listOf(200_000L), session.stats().splits)
    }

    @Test
    fun `REQ-12 the front view shows the last kilometre and the three before it, newest first`() {
        val splits = listOf(301_000L, 302_000L, 303_000L, 304_000L, 305_000L)

        assertEquals(
            listOf(Split(5, 305_000), Split(4, 304_000), Split(3, 303_000), Split(2, 302_000)),
            recentSplits(splits),
        )
        assertEquals(listOf(Split(2, 302_000), Split(1, 301_000)), recentSplits(splits.take(2)))
    }

    @Test
    fun `REQ-12 a kilometre pace is shown in min-ss per km`() {
        assertEquals("5:12 /km", formatKmPace(312_000))
        assertEquals("12:05 /km", formatKmPace(725_000))
    }
}
