package com.example.gpstracker.domain

const val PERMISSION_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
const val PERMISSION_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
const val PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

private const val TIRAMISU = 33

/** REQ-6: runtime permissions to request before tracking on the given Android API level. */
fun requiredPermissions(sdkInt: Int): List<String> = buildList {
    add(PERMISSION_FINE_LOCATION)
    add(PERMISSION_COARSE_LOCATION)
    if (sdkInt >= TIRAMISU) add(PERMISSION_POST_NOTIFICATIONS)
}

/** REQ-6: tracking may only start with precise location; notifications are optional. */
fun canStartTracking(granted: Map<String, Boolean>): Boolean = granted[PERMISSION_FINE_LOCATION] == true
