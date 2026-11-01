package com.example.gpstracker.domain

import java.io.File
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ActiveRunTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock = FakeClock()
    private val zone = ZoneId.of("UTC")

    private fun runningSession() = TrackingSession(ActivityType.JOGGING, clock::now).apply {
        start()
        onLocation(fixAt(0.0, 1_000))
        clock.advanceSeconds(60)
        onLocation(fixAt(200.0, 1_060))
    }

    @Test
    fun `REQ-27 the session in progress is written to file at least every 10 seconds`() {
        assertTrue(ACTIVE_RUN_SAVE_MILLIS <= 10_000L)
    }

    @Test
    fun `REQ-27 a session left unfinished when the app closed is added to the history with its GPX file`() {
        val stats = runningSession().stats()
        ActiveRun(folder.newFolder("active")).save(stats)

        val history = RunHistory(File(folder.root, "history.tsv"))
        val runs = File(folder.root, "runs")
        val recovered = ActiveRun(File(folder.root, "active")).recover(history, runs, zone)

        assertEquals(runRecord(stats), recovered)
        assertEquals(listOf(runRecord(stats)), history.load())
        assertEquals(toGpx(ActivityType.JOGGING, stats.track), File(runs, gpxFileName(ActivityType.JOGGING, 1_000_000, zone)).readText())
        // Recovered once only.
        assertNull(ActiveRun(File(folder.root, "active")).recover(history, runs, zone))
    }

    @Test
    fun `REQ-27 the newest write replaces the earlier one`() {
        val session = runningSession()
        val active = ActiveRun(folder.newFolder("active"))
        active.save(session.stats())
        clock.advanceSeconds(60)
        session.onLocation(fixAt(400.0, 1_120))
        active.save(session.stats())

        val history = RunHistory(File(folder.root, "history.tsv"))
        active.recover(history, File(folder.root, "runs"), zone)

        assertEquals(listOf(runRecord(session.stats())), history.load())
    }

    @Test
    fun `REQ-27 a stopped run is not added again and nothing is written before the first position`() {
        val stats = runningSession().stats()
        val active = ActiveRun(folder.newFolder("active"))
        active.save(stats)
        active.clear()
        active.save(TrackingSession(ActivityType.CYCLING, clock::now).apply { start() }.stats())

        val history = RunHistory(File(folder.root, "history.tsv"))
        assertNull(active.recover(history, File(folder.root, "runs"), zone))
        assertEquals(emptyList<RunRecord>(), history.load())
    }

    @Test
    fun `REQ-27 a run already in the history is not added twice`() {
        val stats = runningSession().stats()
        val active = ActiveRun(folder.newFolder("active"))
        active.save(stats)
        val history = RunHistory(File(folder.root, "history.tsv")).apply { add(runRecord(stats)!!) }

        assertNull(active.recover(history, File(folder.root, "runs"), zone))
        assertEquals(1, history.load().size)
    }
}
