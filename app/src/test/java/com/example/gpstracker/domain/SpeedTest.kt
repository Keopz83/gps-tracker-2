package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedTest {

    @Test
    fun `REQ-5 average speed is distance over active time in km per h`() {
        assertEquals(10.0, averageSpeedKmh(5_000.0, 30 * 60_000L), 1e-9)
    }

    @Test
    fun `REQ-5 average speed is zero when no time has elapsed`() {
        assertEquals(0.0, averageSpeedKmh(100.0, 0), 0.0)
    }

    @Test
    fun `REQ-5 jogging shows pace in min per km`() {
        // 5 km in 27:30 -> 5:30 /km
        assertEquals("5:30 /km", formatSpeed(ActivityType.JOGGING, 5_000.0, (27 * 60 + 30) * 1000L))
    }

    @Test
    fun `REQ-5 jogging pace is a placeholder until there is distance`() {
        assertEquals("--:-- /km", formatSpeed(ActivityType.JOGGING, 0.0, 60_000))
        assertEquals("--:-- /km", formatSpeed(ActivityType.JOGGING, 1_000.0, 0))
    }

    @Test
    fun `REQ-5 cycling shows speed in km per h`() {
        // 25 km in 1 hour
        assertEquals("25.0 km/h", formatSpeed(ActivityType.CYCLING, 25_000.0, 3_600_000))
        assertEquals("0.0 km/h", formatSpeed(ActivityType.CYCLING, 0.0, 0))
    }

    @Test
    fun `REQ-5 distance is shown in km with two decimals`() {
        assertEquals("0.00 km", formatDistance(0.0))
        assertEquals("1.23 km", formatDistance(1_234.0))
        assertEquals("42.20 km", formatDistance(42_195.0))
    }

    @Test
    fun `REQ-5 session stats feed the speed display`() {
        val clock = FakeClock()
        val session = TrackingSession(ActivityType.CYCLING, clock::now).apply { start() }
        session.onLocation(fixAt(0.0, 0))
        session.onLocation(fixAt(100.0, 10))
        clock.advanceSeconds(10)

        val stats = session.stats()

        assertEquals("36.0 km/h", formatSpeed(stats.activityType, stats.distanceMeters, stats.elapsedMillis))
    }
}
