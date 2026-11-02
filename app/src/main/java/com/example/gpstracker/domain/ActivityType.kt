package com.example.gpstracker.domain

/**
 * REQ-1: the kind of activity being tracked. REQ-2: the fastest plausible speed for it, in metres per second.
 * Not a target speed: [FixFilter] discards a GPS position that could only be reached faster than this,
 * so the caps are deliberately generous and only catch GPS jumps.
 */
enum class ActivityType(val label: String, val maxPlausibleSpeedMps: Double) {
    JOGGING("Jogging", 12.0), // ≈ 43 km/h, about a top sprinter's peak speed
    CYCLING("Cycling", 30.0), // ≈ 108 km/h, faster than a racing descent
}
