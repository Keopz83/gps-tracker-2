package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordButtonTest {
    @Test
    fun `REQ-24 the record button starts while no session is active`() {
        assertEquals(RecordAction.START, recordAction(SessionState.IDLE))
        assertEquals(RecordAction.START, recordAction(SessionState.STOPPED))
    }

    @Test
    fun `REQ-24 the record button pauses a running session and resumes a paused one`() {
        assertEquals(RecordAction.PAUSE, recordAction(SessionState.RUNNING))
        assertEquals(RecordAction.RESUME, recordAction(SessionState.PAUSED))
    }
}
