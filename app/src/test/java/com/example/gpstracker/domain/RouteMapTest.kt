package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMapTest {
    private val clock = FakeClock()

    @Test
    fun `REQ-7 there is no route to show before the first position is recorded`() {
        val session = TrackingSession(ActivityType.JOGGING, clock::now).apply { start() }
        val track = session.stats().track

        assertFalse(hasRoute(track))
        assertNull(routeBounds(track))
        assertNull(lastPosition(track))
    }

    @Test
    fun `REQ-7 the map shows the path recorded so far, ending at the latest position`() {
        val session = TrackingSession(ActivityType.JOGGING, clock::now).apply { start() }
        session.onLocation(fixAt(0.0, 0))
        session.onLocation(fixAt(20.0, 5))

        assertTrue(hasRoute(session.stats().track))
        assertEquals(fixAt(20.0, 5), lastPosition(session.stats().track))

        session.onLocation(fixAt(50.0, 10))

        assertEquals(listOf(listOf(fixAt(0.0, 0), fixAt(20.0, 5), fixAt(50.0, 10))), session.stats().track)
        assertEquals(fixAt(50.0, 10), lastPosition(session.stats().track))
    }

    @Test
    fun `REQ-7 the paused gap is not drawn as part of the path`() {
        val session = TrackingSession(ActivityType.CYCLING, clock::now).apply { start() }
        session.onLocation(fixAt(0.0, 0))
        session.onLocation(fixAt(100.0, 10))
        session.pause()
        session.resume()
        session.onLocation(fixAt(1_000.0, 200))

        assertEquals(listOf(listOf(fixAt(0.0, 0), fixAt(100.0, 10)), listOf(fixAt(1_000.0, 200))), session.stats().track)
    }

    @Test
    fun `REQ-7 the latest position is kept after resuming before a new position arrives`() {
        val session = TrackingSession(ActivityType.JOGGING, clock::now).apply { start() }
        session.onLocation(fixAt(0.0, 0))
        session.onLocation(fixAt(20.0, 5))
        session.pause()
        session.resume()

        assertEquals(fixAt(20.0, 5), lastPosition(session.stats().track))
    }

    @Test
    fun `REQ-7 the map zooms to include every recorded position`() {
        val track = listOf(
            listOf(LocationFix(52.0, 4.0, 5f, 0), LocationFix(52.2, 3.9, 5f, 10)),
            listOf(LocationFix(51.9, 4.1, 5f, 20)),
        )

        assertEquals(RouteBounds(north = 52.2, south = 51.9, east = 4.1, west = 3.9), routeBounds(track))
    }
}
