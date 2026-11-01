package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareRunsTest {
    private val oldest = RunRecord(ActivityType.JOGGING, startMillis = 1_000, elapsedMillis = 60_000, distanceMeters = 200.0)
    private val middle = RunRecord(ActivityType.CYCLING, startMillis = 5_000, elapsedMillis = 60_000, distanceMeters = 900.0)
    private val newest = RunRecord(ActivityType.JOGGING, startMillis = 9_000, elapsedMillis = 60_000, distanceMeters = 400.0)
    private val history = listOf(newest, middle, oldest)

    @Test
    fun `REQ-22 a long press selects a run and another one unselects it`() {
        val selected = toggleSelection(emptySet(), middle.startMillis)
        assertEquals(setOf(middle.startMillis), selected)
        assertEquals(setOf(middle.startMillis, oldest.startMillis), toggleSelection(selected, oldest.startMillis))
        assertEquals(emptySet<Long>(), toggleSelection(selected, middle.startMillis))
    }

    @Test
    fun `REQ-22 the selected runs are shared in the order of the history`() {
        val selection = setOf(oldest.startMillis, newest.startMillis)
        assertEquals(listOf(newest, oldest), selectedRuns(history, selection))
    }

    @Test
    fun `REQ-22 a deleted run drops out of the selection`() {
        val selection = setOf(oldest.startMillis, middle.startMillis)
        assertEquals(listOf(middle), selectedRuns(listOf(newest, middle), selection))
    }

    @Test
    fun `REQ-22 the details of a run count its pauses, its time from start to finish and its kilometres`() {
        val track = listOf(
            listOf(fixAt(0.0, 0, accuracy = 0f), fixAt(600.0, 180, accuracy = 0f), fixAt(1_200.0, 360, accuracy = 0f)),
            // Paused for 10 minutes; the gap of 300 m while paused is not counted.
            listOf(fixAt(1_500.0, 960, accuracy = 0f), fixAt(2_300.0, 1_200, accuracy = 0f)),
        )

        val details = runDetails(track)!!

        assertEquals(1, details.pauses)
        assertEquals(1_200_000L, details.totalMillis)
        assertEquals(listOf(300_000L, 300_000L), details.splits)
    }

    @Test
    fun `REQ-22 a run without positions has no details`() {
        assertNull(runDetails(emptyList()))
        assertNull(runDetails(listOf(emptyList())))
    }
}
