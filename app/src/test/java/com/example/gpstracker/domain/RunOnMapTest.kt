package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunOnMapTest {
    private val older = RunRecord(ActivityType.JOGGING, startMillis = 1_000, elapsedMillis = 60_000, distanceMeters = 200.0)
    private val newest = RunRecord(ActivityType.CYCLING, startMillis = 9_000, elapsedMillis = 60_000, distanceMeters = 900.0)
    private val history = listOf(newest, older)

    @Test
    fun `REQ-7 while a session is active the map shows that session rather than a run from the history`() {
        assertNull(mapRun(sessionActive = true, selectedStart = older.startMillis, history = history))
    }

    @Test
    fun `REQ-17 with no session active the map shows the run selected in the history`() {
        assertEquals(older, mapRun(sessionActive = false, selectedStart = older.startMillis, history = history))
    }

    @Test
    fun `REQ-17 with no run selected the map shows the newest run`() {
        assertEquals(newest, mapRun(sessionActive = false, selectedStart = null, history = history))
        assertEquals(newest, mapRun(sessionActive = false, selectedStart = 5_000, history = history))
        assertNull(mapRun(sessionActive = false, selectedStart = null, history = emptyList()))
    }

    @Test
    fun `REQ-17 a saved run is read back from its GPX file with its segments`() {
        val track = listOf(
            listOf(fixAt(0.0, 0, accuracy = 0f), fixAt(20.0, 5, accuracy = 0f)),
            listOf(fixAt(100.0, 60, accuracy = 0f)),
        )
        val read = parseGpxTrack(toGpx(ActivityType.JOGGING, track)!!)

        assertEquals(2, read.size)
        assertEquals(track.map { it.map(LocationFix::timeMillis) }, read.map { it.map(LocationFix::timeMillis) })
        track.flatten().zip(read.flatten()).forEach { (written, back) ->
            assertEquals(written.latitude, back.latitude, 1e-6)
            assertEquals(written.longitude, back.longitude, 1e-6)
        }
    }
}
