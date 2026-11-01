package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsRecordingTest {
    private val clock = FakeClock()

    private fun runningSession(type: ActivityType = ActivityType.JOGGING) =
        TrackingSession(type, clock::now).apply { start() }

    @Test
    fun `REQ-2 positions are recorded while the session is running`() {
        val session = runningSession()

        assertTrue(session.onLocation(fixAt(0.0, 0)))
        assertTrue(session.onLocation(fixAt(10.0, 5)))

        assertEquals(listOf(listOf(fixAt(0.0, 0), fixAt(10.0, 5))), session.stats().track)
    }

    @Test
    fun `REQ-2 positions are ignored while idle`() {
        val session = TrackingSession(ActivityType.JOGGING, clock::now)

        assertFalse(session.onLocation(fixAt(0.0, 0)))
        assertTrue(session.stats().track.isEmpty())
    }

    @Test
    fun `REQ-2 positions are ignored while paused`() {
        val session = runningSession()
        session.onLocation(fixAt(0.0, 0))
        session.pause()

        assertFalse(session.onLocation(fixAt(10.0, 5)))
        assertEquals(1, session.stats().track.flatten().size)
    }

    @Test
    fun `REQ-2 positions are ignored after stopping`() {
        val session = runningSession()
        session.onLocation(fixAt(0.0, 0))
        session.stop()

        assertFalse(session.onLocation(fixAt(10.0, 5)))
        assertEquals(1, session.stats().track.flatten().size)
    }

    @Test
    fun `REQ-2 positions with accuracy worse than 20 m are discarded`() {
        val session = runningSession()

        assertFalse(session.onLocation(fixAt(0.0, 0, accuracy = 20.5f)))
        assertTrue(session.onLocation(fixAt(0.0, 1, accuracy = 20f)))
    }

    @Test
    fun `REQ-2 positions closer than 3 m to the previous one are discarded as jitter`() {
        val session = runningSession()
        session.onLocation(fixAt(0.0, 0))

        assertFalse(session.onLocation(fixAt(2.5, 1)))
        assertTrue(session.onLocation(fixAt(3.5, 2)))
    }

    @Test
    fun `REQ-2 a jump faster than 12 m per s is discarded when jogging`() {
        val session = runningSession(ActivityType.JOGGING)
        session.onLocation(fixAt(0.0, 0))

        assertFalse(session.onLocation(fixAt(130.0, 10)))
        assertTrue(session.onLocation(fixAt(110.0, 10)))
    }

    @Test
    fun `REQ-2 a jump faster than 30 m per s is discarded when cycling`() {
        val session = runningSession(ActivityType.CYCLING)
        session.onLocation(fixAt(0.0, 0))

        assertFalse(session.onLocation(fixAt(310.0, 10)))
        assertTrue(session.onLocation(fixAt(250.0, 10)))
    }

    @Test
    fun `REQ-2 a position that is not newer than the previous one is discarded`() {
        val session = runningSession()
        session.onLocation(fixAt(0.0, 10))

        assertFalse(session.onLocation(fixAt(10.0, 10)))
    }
}
