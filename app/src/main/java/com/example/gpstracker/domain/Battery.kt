package com.example.gpstracker.domain

/** REQ-11: the GPS reports a position every second at the highest accuracy, screen on or off. */
const val GPS_INTERVAL_MILLIS = 1_000L

/** REQ-11: with the screen off, positions may be held back and delivered together at most this late. */
const val SCREEN_OFF_BATCH_MILLIS = 30_000L

/** REQ-14: with voice notes on, the batches are kept short so a kilometre is announced at most this late. */
const val VOICE_NOTES_BATCH_MILLIS = 5_000L

/** REQ-11: the GPS stays on this long into a pause so a short stop resumes without waiting for a fix. */
const val PAUSE_GPS_GRACE_MILLIS = 60_000L

/**
 * REQ-11: how the GPS is asked for positions. [maxDelayMillis] lets the system collect positions in the GPS chip
 * and deliver them in one batch, so the phone can sleep in between; every position is still measured and recorded.
 */
data class GpsRequest(val intervalMillis: Long, val maxDelayMillis: Long)

/**
 * REQ-11: the GPS request for the session [state], or null when the GPS should be off.
 * REQ-26: [intervalMillis] is longer in long distance mode.
 */
fun gpsRequest(
    state: SessionState,
    pausedMillis: Long,
    screenOn: Boolean,
    voiceNotes: Boolean = false,
    intervalMillis: Long = GPS_INTERVAL_MILLIS,
): GpsRequest? = when (state) {
    SessionState.RUNNING -> GpsRequest(
        intervalMillis,
        when {
            screenOn -> 0L
            voiceNotes -> VOICE_NOTES_BATCH_MILLIS
            else -> SCREEN_OFF_BATCH_MILLIS
        },
    )
    // Not batched while paused: positions held back during the pause must not arrive after resuming and be recorded.
    SessionState.PAUSED -> if (pausedMillis < PAUSE_GPS_GRACE_MILLIS) GpsRequest(intervalMillis, 0L) else null
    SessionState.IDLE, SessionState.STOPPED -> null
}

/**
 * REQ-11: what the tracking notification shows. While running, the system counts the elapsed time itself
 * ([chronometer]), so the content only changes, and the notification is only refreshed, when the distance or state does.
 */
data class NotificationContent(val title: String, val text: String, val chronometer: Boolean)

fun notificationContent(stats: SessionStats): NotificationContent = when (stats.state) {
    SessionState.RUNNING -> NotificationContent(stats.activityType.label, formatDistance(stats.distanceMeters), true)
    else -> NotificationContent(
        stats.activityType.label,
        "${formatDuration(stats.elapsedMillis)} · ${formatDistance(stats.distanceMeters)}" +
            if (stats.state == SessionState.PAUSED) " · Paused" else "",
        false,
    )
}
