package com.example.gpstracker.domain

/** REQ-1: the kind of activity being tracked. REQ-2: the fastest plausible speed for it. */
enum class ActivityType(val label: String, val maxPlausibleSpeedMps: Double) {
    JOGGING("Jogging", 12.0),
    CYCLING("Cycling", 30.0),
}
