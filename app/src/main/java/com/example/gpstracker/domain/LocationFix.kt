package com.example.gpstracker.domain

/** A single GPS position as reported by the location provider, with its altitude when known (REQ-23). */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timeMillis: Long,
    val altitudeMeters: Double? = null,
)
