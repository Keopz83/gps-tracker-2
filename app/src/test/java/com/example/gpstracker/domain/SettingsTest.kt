package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test
    fun `REQ-13 REQ-14 voice notes are off until turned on in the settings`() {
        assertFalse(Settings().voiceNotes)
    }

    @Test
    fun `REQ-20 minute voice notes are off until turned on in the settings`() {
        assertFalse(Settings().minuteNotes)
    }

    @Test
    fun `REQ-14 other audio is paused for a voice note unless the user turns that off`() {
        assertTrue(Settings().pauseOtherAudio)
    }

    @Test
    fun `REQ-21 notes are spoken in a female voice until the user picks the male one`() {
        assertEquals(VoiceGender.FEMALE, Settings().voice)
    }

    @Test
    fun `REQ-25 the screen-on report is off until turned on in the settings`() {
        assertFalse(Settings().screenOnNote)
    }

    @Test
    fun `REQ-26 long distance mode is off until turned on and then records a position every 10 seconds`() {
        assertFalse(Settings().longDistance)
        assertEquals(GPS_INTERVAL_MILLIS, Settings().gpsIntervalMillis)
        assertEquals(10_000L, Settings(longDistance = true).gpsIntervalMillis)
        assertEquals(60_000L, Settings(longDistance = true, longDistanceSeconds = 60).gpsIntervalMillis)
        assertEquals(listOf(5, 10, 30, 60), LONG_DISTANCE_SECONDS)
    }
}
