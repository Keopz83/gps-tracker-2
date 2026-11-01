package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionControlTest {
    private val clock = FakeClock()

    private fun session(type: ActivityType = ActivityType.JOGGING) = TrackingSession(type, clock::now)

    @Test
    fun `REQ-1 a new session keeps the chosen activity type and starts idle`() {
        assertEquals(ActivityType.JOGGING, session(ActivityType.JOGGING).activityType)
        assertEquals(ActivityType.CYCLING, session(ActivityType.CYCLING).activityType)
        assertEquals(SessionState.IDLE, session().state)
    }

    @Test
    fun `REQ-1 start pause resume and stop move through the session states`() {
        val session = session()

        session.start()
        assertEquals(SessionState.RUNNING, session.state)
        session.pause()
        assertEquals(SessionState.PAUSED, session.state)
        session.resume()
        assertEquals(SessionState.RUNNING, session.state)
        session.stop()
        assertEquals(SessionState.STOPPED, session.state)
    }

    @Test
    fun `REQ-1 a paused session can be stopped`() {
        val session = session().apply { start(); pause() }

        session.stop()

        assertEquals(SessionState.STOPPED, session.state)
    }

    @Test
    fun `REQ-1 pausing or stopping a session that has not started is rejected`() {
        assertThrows(IllegalStateException::class.java) { session().pause() }
        assertThrows(IllegalStateException::class.java) { session().stop() }
    }

    @Test
    fun `REQ-1 resuming a running session is rejected`() {
        val session = session().apply { start() }

        assertThrows(IllegalStateException::class.java) { session.resume() }
    }

    @Test
    fun `REQ-1 a stopped session cannot be restarted`() {
        val session = session().apply { start(); stop() }

        assertThrows(IllegalStateException::class.java) { session.start() }
        assertThrows(IllegalStateException::class.java) { session.resume() }
    }
}
