package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceTest {
    private val clock = FakeClock()

    @Test
    fun `REQ-3 haversine distance matches exact great-circle references`() {
        val earthRadius = 6_371_000.0
        val origin = LocationFix(0.0, 0.0, 5f, 0)

        // A quarter of the equator and equator-to-pole are both a quarter of the circumference.
        assertEquals(Math.PI * earthRadius / 2, haversineMeters(origin, LocationFix(0.0, 90.0, 5f, 0)), 1e-6)
        assertEquals(Math.PI * earthRadius / 2, haversineMeters(origin, LocationFix(90.0, 0.0, 5f, 0)), 1e-6)
        // One degree of longitude at 60° latitude is half as long as on the equator (small-arc approximation).
        val atSixty = haversineMeters(LocationFix(60.0, 0.0, 5f, 0), LocationFix(60.0, 1.0, 5f, 0))
        assertEquals(METERS_PER_DEGREE_LAT / 2, atSixty, 1.0)
    }

    @Test
    fun `REQ-3 haversine distance between identical positions is zero`() {
        val fix = fixAt(0.0, 0)

        assertEquals(0.0, haversineMeters(fix, fix), 0.0)
    }

    @Test
    fun `REQ-3 a session with no or one position has zero distance`() {
        val session = TrackingSession(ActivityType.JOGGING, clock::now).apply { start() }
        assertEquals(0.0, session.distanceMeters, 0.0)

        session.onLocation(fixAt(0.0, 0))
        assertEquals(0.0, session.distanceMeters, 0.0)
    }

    @Test
    fun `REQ-3 total distance is the sum of the distances between consecutive positions`() {
        val session = TrackingSession(ActivityType.JOGGING, clock::now).apply { start() }

        listOf(0.0 to 0L, 20.0 to 5L, 50.0 to 10L, 100.0 to 20L).forEach { (m, s) -> session.onLocation(fixAt(m, s)) }

        assertEquals(100.0, session.distanceMeters, 0.01)
    }

    @Test
    fun `REQ-3 discarded positions do not add distance`() {
        val session = TrackingSession(ActivityType.JOGGING, clock::now).apply { start() }

        session.onLocation(fixAt(0.0, 0))
        session.onLocation(fixAt(500.0, 5)) // implausible jump
        session.onLocation(fixAt(40.0, 6, accuracy = 50f)) // inaccurate
        session.onLocation(fixAt(30.0, 10))

        assertEquals(30.0, session.distanceMeters, 0.01)
    }

    @Test
    fun `REQ-3 the gap between pausing and resuming is not counted`() {
        val session = TrackingSession(ActivityType.CYCLING, clock::now).apply { start() }
        session.onLocation(fixAt(0.0, 0))
        session.onLocation(fixAt(100.0, 10))
        session.pause()
        session.resume()
        session.onLocation(fixAt(1_000.0, 200))
        session.onLocation(fixAt(1_050.0, 205))

        assertEquals(150.0, session.distanceMeters, 0.01)
        assertEquals(2, session.stats().track.size)
    }
}
