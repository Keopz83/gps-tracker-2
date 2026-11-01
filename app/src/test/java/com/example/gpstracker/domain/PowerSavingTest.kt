package com.example.gpstracker.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerSavingTest {

    @Test
    fun `REQ-15 the user is warned when power saving switches the GPS off with the screen`() {
        assertTrue(powerSavingLimitsGps(31, powerSaveMode = true, LOCATION_POWER_SAVE_GPS_DISABLED_WHEN_SCREEN_OFF))
    }

    @Test
    fun `REQ-15 the user is not warned when power saving is off`() {
        assertFalse(powerSavingLimitsGps(31, powerSaveMode = false, LOCATION_POWER_SAVE_GPS_DISABLED_WHEN_SCREEN_OFF))
        assertFalse(powerSavingLimitsGps(26, powerSaveMode = false, LOCATION_POWER_SAVE_NO_CHANGE))
    }

    @Test
    fun `REQ-15 the user is not warned when power saving leaves location alone`() {
        assertFalse(powerSavingLimitsGps(31, powerSaveMode = true, LOCATION_POWER_SAVE_NO_CHANGE))
    }

    @Test
    fun `REQ-15 the user is warned about power saving before Android 9, where its effect on location is unknown`() {
        assertTrue(powerSavingLimitsGps(27, powerSaveMode = true, LOCATION_POWER_SAVE_NO_CHANGE))
    }
}
