package com.example.gpstracker.domain

/** REQ-21: the voice the notes are spoken in. */
enum class VoiceGender(val label: String) { FEMALE("Female"), MALE("Male") }

/** REQ-26: how often a position can be recorded in long distance mode, in seconds. */
val LONG_DISTANCE_SECONDS = listOf(5, 10, 30, 60)

/** REQ-28: the rider's weight that can be entered, in kg. */
val RIDER_KG_RANGE = 30..200

/** REQ-28: the bike's weight that can be entered, in kg. */
val BIKE_KG_RANGE = 3..40

/** REQ-28: a weight typed in the settings, in whole kg, or null unless it is a number within [range]. */
fun parseWeightKg(text: String, range: IntRange): Int? = text.trim().toIntOrNull()?.takeIf { it in range }

/**
 * REQ-13: the user's choices from the settings view. REQ-14, REQ-20: voice notes are off until the user turns them on.
 * REQ-14: other audio is paused while a note plays, unless the user prefers it only lowered. REQ-21: a female voice.
 * REQ-25: no report when the screen is turned on, until the user turns it on.
 * REQ-26: long distance mode is off, and records a position every 10 s once turned on.
 * REQ-28: a 75 kg rider on a 10 kg bike, for the estimated cycling power.
 */
data class Settings(
    val voiceNotes: Boolean = false,
    val minuteNotes: Boolean = false,
    val voice: VoiceGender = VoiceGender.FEMALE,
    val pauseOtherAudio: Boolean = true,
    val screenOnNote: Boolean = false,
    val longDistance: Boolean = false,
    val longDistanceSeconds: Int = 10,
    val riderKg: Int = 75,
    val bikeKg: Int = 10,
) {
    /** REQ-11, REQ-26: how often the GPS reports a position: every second, or less often in long distance mode. */
    val gpsIntervalMillis: Long get() = if (longDistance) longDistanceSeconds * 1_000L else GPS_INTERVAL_MILLIS

    /** REQ-14, REQ-20: whether any voice note is on, so positions are batched briefly enough for it to come on time. */
    val anyVoiceNotes: Boolean get() = voiceNotes || minuteNotes

    /** REQ-28: the weight of the rider and the bike together, which the cycling power is estimated for. */
    val cyclingKg: Double get() = (riderKg + bikeKg).toDouble()
}
