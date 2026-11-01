package com.example.gpstracker.domain

/** Metres per degree of latitude for the haversine earth radius (6 371 km). */
const val METERS_PER_DEGREE_LAT = 6_371_000.0 * Math.PI / 180

class FakeClock(var now: Long = 0L) {
    fun advanceSeconds(seconds: Long) {
        now += seconds * 1000
    }
}

/** A fix [northMeters] north of (52.0, 4.0) at [timeSeconds]. */
fun fixAt(northMeters: Double, timeSeconds: Long, accuracy: Float = 5f) = LocationFix(
    latitude = 52.0 + northMeters / METERS_PER_DEGREE_LAT,
    longitude = 4.0,
    accuracyMeters = accuracy,
    timeMillis = timeSeconds * 1000,
)
