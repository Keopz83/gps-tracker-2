package com.example.gpstracker.domain

/** REQ-13: the user's choices from the settings view. REQ-14: voice notes are off until the user turns them on. */
data class Settings(val voiceNotes: Boolean = false)
