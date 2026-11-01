package com.example.gpstracker.domain

import java.io.File
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RunHistoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock = FakeClock()

    @Test
    fun `REQ-9 a stopped run is recorded with its start time, active duration and distance`() {
        val session = TrackingSession(ActivityType.JOGGING, clock::now).apply { start() }
        session.onLocation(fixAt(0.0, 1_000))
        clock.advanceSeconds(60)
        session.onLocation(fixAt(200.0, 1_060))
        session.pause()
        clock.advanceSeconds(300)
        session.resume()
        clock.advanceSeconds(30)
        session.onLocation(fixAt(1_000.0, 1_390))
        session.stop()

        val record = runRecord(session.stats())!!

        assertEquals(ActivityType.JOGGING, record.activityType)
        assertEquals(1_000_000L, record.startMillis)
        assertEquals(90_000L, record.elapsedMillis)
        assertEquals(200.0, record.distanceMeters, 0.01)
        assertEquals(fixAt(0.0, 1_000).latitude, record.startLatitude!!, 0.0)
        assertEquals(fixAt(0.0, 1_000).longitude, record.startLongitude!!, 0.0)
        assertNull(record.place)
    }

    @Test
    fun `REQ-9 a run without recorded positions is not recorded`() {
        val session = TrackingSession(ActivityType.CYCLING, clock::now).apply { start() }
        clock.advanceSeconds(60)
        session.stop()

        assertNull(runRecord(session.stats()))
    }

    @Test
    fun `REQ-9 saved runs are read back newest first`() {
        val file = File(folder.root, "history.tsv")
        val older = RunRecord(ActivityType.JOGGING, 1_793_000_000_000, 1_800_000, 5_012.5)
        val newer = RunRecord(ActivityType.CYCLING, 1_793_100_000_000, 3_725_000, 42_195.0)
        RunHistory(file).add(older)
        RunHistory(file).add(newer)

        assertEquals(listOf(newer, older), RunHistory(file).load())
    }

    @Test
    fun `REQ-9 the history is empty before the first run`() {
        assertTrue(RunHistory(File(folder.root, "history.tsv")).load().isEmpty())
    }

    @Test
    fun `REQ-9 an unreadable line does not hide the other runs`() {
        val file = File(folder.root, "history.tsv")
        val run = RunRecord(ActivityType.JOGGING, 1_793_000_000_000, 1_800_000, 5_000.0)
        RunHistory(file).add(run)
        file.appendText("garbage\nSWIMMING\t1\t2\t3.0\n")

        assertEquals(listOf(run), RunHistory(file).load())
    }

    @Test
    fun `REQ-9 the run date is shown in local time`() {
        assertEquals("2026-10-26 08:33", formatRunDate(1_793_000_000_000, ZoneId.of("Europe/Amsterdam")))
    }

    @Test
    fun `REQ-9 the start position and place name are saved and read back`() {
        val file = File(folder.root, "history.tsv")
        val run = RunRecord(ActivityType.JOGGING, 1_793_000_000_000, 1_800_000, 5_000.0, 47.3769, 8.5417, "Zürich")
        RunHistory(file).add(run)

        assertEquals(listOf(run), RunHistory(file).load())
    }

    @Test
    fun `REQ-9 runs saved before places were stored are still read`() {
        val file = File(folder.root, "history.tsv")
        file.writeText("JOGGING\t1793000000000\t1800000\t5000.0\n")

        assertEquals(listOf(RunRecord(ActivityType.JOGGING, 1_793_000_000_000, 1_800_000, 5_000.0)), RunHistory(file).load())
    }

    @Test
    fun `REQ-9 a run's place name is added to the saved history`() {
        val file = File(folder.root, "history.tsv")
        val older = RunRecord(ActivityType.JOGGING, 1_793_000_000_000, 1_800_000, 5_000.0)
        val newer = RunRecord(ActivityType.CYCLING, 1_793_100_000_000, 3_725_000, 42_195.0, 46.0, 7.7)
        RunHistory(file).add(older)
        RunHistory(file).add(newer)

        val named = newer.copy(place = "Zermatt")
        RunHistory(file).update(named)

        assertEquals(listOf(named, older), RunHistory(file).load())
    }

    @Test
    fun `REQ-9 the place name is the town, else the district, region or country`() {
        assertEquals("Zermatt", placeName("Zermatt", "Visp", "Valais", "Switzerland"))
        assertEquals("Visp", placeName(null, "Visp", "Valais", "Switzerland"))
        assertEquals("Switzerland", placeName(" ", null, "", "Switzerland"))
        assertNull(placeName(null, null, null, null))
    }

    @Test
    fun `REQ-9 the start of an older run is read from its GPX file`() {
        val gpx = toGpx(ActivityType.JOGGING, listOf(listOf(fixAt(0.0, 1_000), fixAt(200.0, 1_060))))!!

        val (lat, lon) = firstGpxPosition(gpx)!!

        assertEquals(fixAt(0.0, 1_000).latitude, lat, 1e-6)
        assertEquals(fixAt(0.0, 1_000).longitude, lon, 1e-6)
    }
}
