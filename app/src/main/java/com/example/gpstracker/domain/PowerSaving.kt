package com.example.gpstracker.domain

/** `PowerManager.LOCATION_MODE_NO_CHANGE`: power saving leaves location alone. */
const val LOCATION_POWER_SAVE_NO_CHANGE = 0

/** `PowerManager.LOCATION_MODE_GPS_DISABLED_WHEN_SCREEN_OFF`: power saving switches the GPS off with the screen. */
const val LOCATION_POWER_SAVE_GPS_DISABLED_WHEN_SCREEN_OFF = 1

private const val PIE = 28

/**
 * REQ-15: whether power saving may stop or limit GPS positions while the screen is off, leaving gaps in the track.
 * Before Android 9 the phone does not tell what power saving does to location, so it is assumed to limit it.
 */
fun powerSavingLimitsGps(sdkInt: Int, powerSaveMode: Boolean, locationPowerSaveMode: Int): Boolean =
    powerSaveMode && (sdkInt < PIE || locationPowerSaveMode != LOCATION_POWER_SAVE_NO_CHANGE)
