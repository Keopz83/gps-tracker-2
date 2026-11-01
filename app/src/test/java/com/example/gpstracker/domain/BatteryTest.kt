package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryTest {
    private val clock = FakeClock()

    @Test
    fun `REQ-11 while running the GPS reports every second whether the screen is on or off`() {
        assertEquals(GPS_INTERVAL_MILLIS, gpsRequest(SessionState.RUNNING, 0, screenOn = true)?.intervalMillis)
        assertEquals(GPS_INTERVAL_MILLIS, gpsRequest(SessionState.RUNNING, 0, screenOn = false)?.intervalMillis)
        assertEquals(1_000L, GPS_INTERVAL_MILLIS)
    }

    @Test
    fun `REQ-11 with the screen off positions are batched, with the screen on they are delivered at once`() {
        assertEquals(0L, gpsRequest(SessionState.RUNNING, 0, screenOn = true)?.maxDelayMillis)
        assertEquals(SCREEN_OFF_BATCH_MILLIS, gpsRequest(SessionState.RUNNING, 0, screenOn = false)?.maxDelayMillis)
    }

    @Test
    fun `REQ-11 the GPS stays on unbatched for a short pause and is switched off after the grace period`() {
        val session = TrackingSession(ActivityType.JOGGING, clock::now).apply { start() }
        clock.advanceSeconds(120)
        session.pause()
        clock.advanceSeconds(59)

        assertEquals(GpsRequest(GPS_INTERVAL_MILLIS, 0L), gpsRequest(session.state, session.pausedMillis, screenOn = false))

        clock.advanceSeconds(1)

        assertNull(gpsRequest(session.state, session.pausedMillis, screenOn = false))
    }

    @Test
    fun `REQ-11 the pause duration starts again on every pause`() {
        val session = TrackingSession(ActivityType.JOGGING, clock::now).apply { start() }
        session.pause()
        clock.advanceSeconds(300)
        session.resume()

        assertEquals(0L, session.pausedMillis)

        session.pause()
        clock.advanceSeconds(10)

        assertEquals(10_000L, session.pausedMillis)
    }

    @Test
    fun `REQ-11 the GPS is off before starting and after stopping`() {
        assertNull(gpsRequest(SessionState.IDLE, 0, screenOn = true))
        assertNull(gpsRequest(SessionState.STOPPED, 0, screenOn = true))
    }

    @Test
    fun `REQ-11 the running notification does not change as time passes without a new position`() {
        val session = TrackingSession(ActivityType.CYCLING, clock::now).apply { start() }
        session.onLocation(fixAt(0.0, 0))
        val before = notificationContent(session.stats())

        clock.advanceSeconds(5)

        assertEquals(before, notificationContent(session.stats()))
        assertEquals(NotificationContent("Cycling", "0.00 km", chronometer = true), before)
    }

    @Test
    fun `REQ-11 the running notification changes when the distance does`() {
        val session = TrackingSession(ActivityType.CYCLING, clock::now).apply { start() }
        session.onLocation(fixAt(0.0, 0))
        val before = notificationContent(session.stats())

        session.onLocation(fixAt(50.0, 5))

        assertNotEquals(before, notificationContent(session.stats()))
    }

    @Test
    fun `REQ-11 the paused notification shows the frozen elapsed time`() {
        val session = TrackingSession(ActivityType.JOGGING, clock::now).apply { start() }
        clock.advanceSeconds(75)
        session.pause()
        clock.advanceSeconds(30)

        assertEquals(
            NotificationContent("Jogging", "01:15 · 0.00 km · Paused", chronometer = false),
            notificationContent(session.stats()),
        )
    }
}
