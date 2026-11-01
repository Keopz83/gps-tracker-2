package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ElapsedTimeTest {
    private val clock = FakeClock(now = 1_000_000)
    private val session = TrackingSession(ActivityType.JOGGING, clock::now)

    @Test
    fun `REQ-4 elapsed time is zero before starting`() {
        clock.advanceSeconds(30)

        assertEquals(0L, session.elapsedMillis)
    }

    @Test
    fun `REQ-4 elapsed time grows while running`() {
        session.start()
        clock.advanceSeconds(90)

        assertEquals(90_000L, session.elapsedMillis)
    }

    @Test
    fun `REQ-4 time spent paused is excluded`() {
        session.start()
        clock.advanceSeconds(60)
        session.pause()
        clock.advanceSeconds(300)
        assertEquals(60_000L, session.elapsedMillis)

        session.resume()
        clock.advanceSeconds(30)
        assertEquals(90_000L, session.elapsedMillis)
    }

    @Test
    fun `REQ-4 elapsed time is frozen after stopping`() {
        session.start()
        clock.advanceSeconds(45)
        session.stop()
        clock.advanceSeconds(600)

        assertEquals(45_000L, session.elapsedMillis)
    }

    @Test
    fun `REQ-4 stopping while paused does not count the pause`() {
        session.start()
        clock.advanceSeconds(20)
        session.pause()
        clock.advanceSeconds(100)
        session.stop()

        assertEquals(20_000L, session.elapsedMillis)
    }

    @Test
    fun `REQ-4 durations under an hour are formatted as MM SS`() {
        assertEquals("00:00", formatDuration(0))
        assertEquals("00:59", formatDuration(59_999))
        assertEquals("12:05", formatDuration(725_000))
        assertEquals("59:59", formatDuration(3_599_000))
    }

    @Test
    fun `REQ-4 durations from an hour are formatted as H MM SS`() {
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("2:03:04", formatDuration(7_384_000))
    }
}
