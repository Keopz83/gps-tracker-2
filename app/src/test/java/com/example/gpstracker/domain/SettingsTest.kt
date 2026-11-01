package com.example.gpstracker.domain

import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsTest {
    @Test
    fun `REQ-13 REQ-14 voice notes are off until turned on in the settings`() {
        assertFalse(Settings().voiceNotes)
    }
}
