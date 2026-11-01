package com.example.gpstracker.domain

/** REQ-21: the voice the notes are spoken in. */
enum class VoiceGender(val label: String) { FEMALE("Female"), MALE("Male") }

/**
 * REQ-13: the user's choices from the settings view. REQ-14, REQ-20: voice notes are off until the user turns them on.
 * REQ-14: other audio is paused while a note plays, unless the user prefers it only lowered. REQ-21: a female voice.
 * REQ-25: no report when the screen is turned on, until the user turns it on.
 */
data class Settings(
    val voiceNotes: Boolean = false,
    val minuteNotes: Boolean = false,
    val voice: VoiceGender = VoiceGender.FEMALE,
    val pauseOtherAudio: Boolean = true,
    val screenOnNote: Boolean = false,
) {
    /** REQ-14, REQ-20: whether any voice note is on, so positions are batched briefly enough for it to come on time. */
    val anyVoiceNotes: Boolean get() = voiceNotes || minuteNotes
}
