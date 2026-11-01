package com.example.gpstracker.domain

enum class SessionState { IDLE, RUNNING, PAUSED, STOPPED }

/** Immutable view of a session for display. */
data class SessionStats(
    val activityType: ActivityType,
    val state: SessionState,
    val elapsedMillis: Long,
    val distanceMeters: Double,
    val track: List<List<LocationFix>>,
)

/**
 * A single jogging or cycling session.
 *
 * Each start/resume opens a new track segment so that the distance covered while paused is not counted (REQ-3).
 */
class TrackingSession(
    val activityType: ActivityType,
    private val clock: () -> Long,
    private val filter: FixFilter = FixFilter(),
) {
    var state: SessionState = SessionState.IDLE
        private set

    private val segments = mutableListOf<MutableList<LocationFix>>()
    private var accumulatedMillis = 0L
    private var runningSince = 0L
    private var pausedSince = 0L

    // REQ-1
    fun start() {
        check(state == SessionState.IDLE) { "Cannot start a session that is $state" }
        beginSegment()
    }

    fun pause() {
        check(state == SessionState.RUNNING) { "Cannot pause a session that is $state" }
        accumulatedMillis += clock() - runningSince
        pausedSince = clock()
        state = SessionState.PAUSED
    }

    fun resume() {
        check(state == SessionState.PAUSED) { "Cannot resume a session that is $state" }
        beginSegment()
    }

    fun stop() {
        check(state == SessionState.RUNNING || state == SessionState.PAUSED) { "Cannot stop a session that is $state" }
        if (state == SessionState.RUNNING) accumulatedMillis += clock() - runningSince
        state = SessionState.STOPPED
    }

    /** REQ-2: records [fix] if the session is running and the fix passes the filter. Returns whether it was recorded. */
    fun onLocation(fix: LocationFix): Boolean {
        if (state != SessionState.RUNNING) return false
        val segment = segments.last()
        if (!filter.accept(segment.lastOrNull(), fix, activityType)) return false
        segment += fix
        return true
    }

    /** REQ-4: active time, excluding pauses. */
    val elapsedMillis: Long
        get() = accumulatedMillis + if (state == SessionState.RUNNING) clock() - runningSince else 0L

    /** REQ-11: how long the session has been paused, or 0 when it is not paused. */
    val pausedMillis: Long
        get() = if (state == SessionState.PAUSED) clock() - pausedSince else 0L

    /** REQ-3: distance summed within each segment. */
    val distanceMeters: Double
        get() = segments.sumOf { segment -> segment.zipWithNext(::haversineMeters).sum() }

    fun stats(): SessionStats = SessionStats(
        activityType = activityType,
        state = state,
        elapsedMillis = elapsedMillis,
        distanceMeters = distanceMeters,
        track = segments.map { it.toList() },
    )

    private fun beginSegment() {
        runningSince = clock()
        segments.add(mutableListOf())
        state = SessionState.RUNNING
    }
}
