package com.example.gpstracker.domain

import java.util.Locale

/** REQ-4: `MM:SS`, or `H:MM:SS` from one hour. */
fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/** REQ-5: distance in km with two decimals. */
fun formatDistance(meters: Double): String = String.format(Locale.US, "%.2f km", meters / 1000)

/** REQ-5: average speed in km/h, or 0 when no time has elapsed. */
fun averageSpeedKmh(distanceMeters: Double, elapsedMillis: Long): Double =
    if (elapsedMillis <= 0) 0.0 else (distanceMeters / 1000) / (elapsedMillis / 3_600_000.0)

/** REQ-5: pace (`min:ss /km`) for jogging, speed (`km/h`) for cycling. */
fun formatSpeed(activityType: ActivityType, distanceMeters: Double, elapsedMillis: Long): String =
    when (activityType) {
        ActivityType.JOGGING -> formatPace(distanceMeters, elapsedMillis)
        ActivityType.CYCLING -> String.format(Locale.US, "%.1f km/h", averageSpeedKmh(distanceMeters, elapsedMillis))
    }

private fun formatPace(distanceMeters: Double, elapsedMillis: Long): String {
    if (distanceMeters < 10 || elapsedMillis <= 0) return "--:-- /km"
    val secondsPerKm = Math.round(elapsedMillis / 1000.0 / (distanceMeters / 1000))
    return String.format(Locale.US, "%d:%02d /km", secondsPerKm / 60, secondsPerKm % 60)
}
