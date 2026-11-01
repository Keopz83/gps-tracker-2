package com.example.gpstracker.domain

/** REQ-24: what the round button in the middle of the bottom bar does. */
enum class RecordAction { START, PAUSE, RESUME }

/** REQ-24: record (pick an activity to start) while no session is active, pause while running, resume while paused. */
fun recordAction(state: SessionState): RecordAction = when (state) {
    SessionState.RUNNING -> RecordAction.PAUSE
    SessionState.PAUSED -> RecordAction.RESUME
    SessionState.IDLE, SessionState.STOPPED -> RecordAction.START
}
