package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceNoteTest {
    @Test
    fun `REQ-14 the note gives total distance, total time and last km pace in minutes and seconds per km`() {
        assertEquals(
            "3 kilometres. Time 16 minutes 5 seconds. Last kilometre 5 minutes 12 seconds per kilometre.",
            kmVoiceNote(ActivityType.JOGGING, km = 3, elapsedMillis = (16 * 60 + 5) * 1000L, lastKmMillis = 312_000),
        )
    }

    @Test
    fun `REQ-14 singular units and hours are spoken correctly`() {
        assertEquals(
            "1 kilometre. Time 1 hour 1 minute 1 second. Last kilometre 6 minutes 0 seconds per kilometre.",
            kmVoiceNote(ActivityType.JOGGING, km = 1, elapsedMillis = 3_661_000, lastKmMillis = 360_000),
        )
    }

    @Test
    fun `REQ-14 with voice notes on, screen-off batches are short so the kilometre is announced on time`() {
        assertEquals(VOICE_NOTES_BATCH_MILLIS, gpsRequest(SessionState.RUNNING, 0, screenOn = false, voiceNotes = true)?.maxDelayMillis)
        assertEquals(0L, gpsRequest(SessionState.RUNNING, 0, screenOn = true, voiceNotes = true)?.maxDelayMillis)
        assertEquals(GPS_INTERVAL_MILLIS, gpsRequest(SessionState.RUNNING, 0, screenOn = false, voiceNotes = true)?.intervalMillis)
    }

    @Test
    fun `REQ-20 the minute note gives the time, total distance and average pace`() {
        assertEquals(
            "5 minutes. Distance 0.84 kilometres. Average pace 5 minutes 57 seconds per kilometre.",
            minuteVoiceNote(ActivityType.JOGGING, minutes = 5, distanceMeters = 840.0),
        )
    }

    @Test
    fun `REQ-20 the minute note speaks hours and leaves the pace out before any distance`() {
        assertEquals("1 hour 1 minute. Distance 0.00 kilometres.", minuteVoiceNote(ActivityType.JOGGING, minutes = 61, distanceMeters = 0.0))
    }

    @Test
    fun `REQ-20 each whole minute of active time is announced once`() {
        assertEquals(0L, elapsedMinutes(59_999))
        assertEquals(1L, elapsedMinutes(60_000))
        assertEquals(2L, elapsedMinutes(179_999))
    }

    @Test
    fun `REQ-20 with only minute notes on, screen-off batches are short so the minute is announced on time`() {
        val settings = Settings(minuteNotes = true)
        assertEquals(VOICE_NOTES_BATCH_MILLIS, gpsRequest(SessionState.RUNNING, 0, screenOn = false, voiceNotes = settings.anyVoiceNotes)?.maxDelayMillis)
    }

    @Test
    fun `REQ-25 the screen-on report gives the time, total distance and average pace`() {
        assertEquals(
            "Time 16 minutes 5 seconds. Distance 3.02 kilometres. Average pace 5 minutes 20 seconds per kilometre.",
            screenOnVoiceNote(ActivityType.JOGGING, elapsedMillis = (16 * 60 + 5) * 1000L, distanceMeters = 3015.6),
        )
    }

    @Test
    fun `REQ-25 the screen-on report speaks hours and leaves the pace out before any distance`() {
        assertEquals(
            "Time 1 hour 0 minutes 1 second. Distance 0.00 kilometres.",
            screenOnVoiceNote(ActivityType.JOGGING, elapsedMillis = 3_601_000, distanceMeters = 0.0),
        )
    }

    @Test
    fun `REQ-14 when cycling the note gives the last km speed in km per hour`() {
        assertEquals(
            "3 kilometres. Time 9 minutes 30 seconds. Last kilometre 24.0 kilometres per hour.",
            kmVoiceNote(ActivityType.CYCLING, km = 3, elapsedMillis = 570_000, lastKmMillis = 150_000),
        )
    }

    @Test
    fun `REQ-20 when cycling the minute note gives the average speed in km per hour`() {
        assertEquals(
            "10 minutes. Distance 3.20 kilometres. Average speed 19.2 kilometres per hour.",
            minuteVoiceNote(ActivityType.CYCLING, minutes = 10, distanceMeters = 3200.0),
        )
    }

    @Test
    fun `REQ-25 when cycling the screen-on report gives the average speed in km per hour`() {
        assertEquals(
            "Time 16 minutes 5 seconds. Distance 4.02 kilometres. Average speed 15.0 kilometres per hour.",
            screenOnVoiceNote(ActivityType.CYCLING, elapsedMillis = (16 * 60 + 5) * 1000L, distanceMeters = 4020.0),
        )
    }
}
