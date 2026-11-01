package com.example.gpstracker.domain

/** REQ-2: decides whether a GPS position is good enough to be added to the track. */
class FixFilter(
    private val maxAccuracyMeters: Float = 20f,
    private val minDistanceMeters: Double = 3.0,
) {
    fun accept(previous: LocationFix?, candidate: LocationFix, activityType: ActivityType): Boolean {
        if (candidate.accuracyMeters > maxAccuracyMeters) return false
        if (previous == null) return true

        val distance = haversineMeters(previous, candidate)
        if (distance < minDistanceMeters) return false

        val seconds = (candidate.timeMillis - previous.timeMillis) / 1000.0
        if (seconds <= 0) return false
        return distance / seconds <= activityType.maxPlausibleSpeedMps
    }
}
