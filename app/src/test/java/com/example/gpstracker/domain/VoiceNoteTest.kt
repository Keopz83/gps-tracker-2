package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceNoteTest {
    @Test
    fun `REQ-14 the note gives total distance, total time and last km pace in minutes and seconds per km`() {
        assertEquals(
            "3 kilometres. Time 16 minutes 5 seconds. Last kilometre 5 minutes 12 seconds per kilometre.",
            kmVoiceNote(km = 3, elapsedMillis = (16 * 60 + 5) * 1000L, lastKmMillis = 312_000),
        )
    }

    @Test
    fun `REQ-14 singular units and hours are spoken correctly`() {
        assertEquals(
            "1 kilometre. Time 1 hour 1 minute 1 second. Last kilometre 6 minutes 0 seconds per kilometre.",
            kmVoiceNote(km = 1, elapsedMillis = 3_661_000, lastKmMillis = 360_000),
        )
    }

    @Test
    fun `REQ-14 with voice notes on, screen-off batches are short so the kilometre is announced on time`() {
        assertEquals(VOICE_NOTES_BATCH_MILLIS, gpsRequest(SessionState.RUNNING, 0, screenOn = false, voiceNotes = true)?.maxDelayMillis)
        assertEquals(0L, gpsRequest(SessionState.RUNNING, 0, screenOn = true, voiceNotes = true)?.maxDelayMillis)
        assertEquals(GPS_INTERVAL_MILLIS, gpsRequest(SessionState.RUNNING, 0, screenOn = false, voiceNotes = true)?.intervalMillis)
    }
}
