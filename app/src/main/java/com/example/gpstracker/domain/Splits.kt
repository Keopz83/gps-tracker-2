package com.example.gpstracker.domain

import java.util.Locale

private const val KM = 1_000.0

/**
 * REQ-12: the time taken for each completed kilometre, measured between recorded positions so pauses are excluded.
 * The moment a kilometre is completed is interpolated between the two positions on either side of it.
 */
class KmSplits {
    private val completed = mutableListOf<Long>()
    private var metersIntoKm = 0.0
    private var millisIntoKm = 0.0

    /** Milliseconds per completed kilometre, first kilometre first. */
    val splits: List<Long> get() = completed.toList()

    val count: Int get() = completed.size

    /** Adds the step between two consecutive positions of a segment. */
    fun add(meters: Double, millis: Long) {
        var stepMeters = meters
        var stepMillis = millis.toDouble()
        while (stepMeters > 0 && metersIntoKm + stepMeters >= KM) {
            val neededMeters = KM - metersIntoKm
            val neededMillis = stepMillis * neededMeters / stepMeters
            completed += Math.round(millisIntoKm + neededMillis)
            stepMeters -= neededMeters
            stepMillis -= neededMillis
            metersIntoKm = 0.0
            millisIntoKm = 0.0
        }
        metersIntoKm += stepMeters
        millisIntoKm += stepMillis
    }
}

/** REQ-12: a completed kilometre ([km] counts from 1) and the time it took. */
data class Split(val km: Int, val millis: Long)

/** REQ-12: the last completed kilometre followed by up to [previous] kilometres before it, newest first. */
fun recentSplits(splits: List<Long>, previous: Int = 3): List<Split> =
    splits.mapIndexed { index, millis -> Split(index + 1, millis) }.takeLast(previous + 1).reversed()

/** REQ-12: the pace of one kilometre that took [millis], as `min:ss /km`. */
fun formatKmPace(millis: Long): String = formatSpeed(ActivityType.JOGGING, KM, millis)

/**
 * REQ-14: the spoken note when kilometre [km] is completed: total distance, total active time and the pace of
 * the last kilometre in minutes and seconds per kilometre.
 */
fun kmVoiceNote(km: Int, elapsedMillis: Long, lastKmMillis: Long): String {
    val distance = if (km == 1) "1 kilometre" else "$km kilometres"
    val paceSeconds = Math.round(lastKmMillis / 1000.0)
    val pace = spokenDuration(paceSeconds / 60, paceSeconds % 60)
    val totalSeconds = elapsedMillis / 1000
    val time = listOfNotNull(
        (totalSeconds / 3600).takeIf { it > 0 }?.let { unit(it, "hour") },
        spokenDuration((totalSeconds % 3600) / 60, totalSeconds % 60),
    ).joinToString(" ")
    return "$distance. Time $time. Last kilometre $pace per kilometre."
}

/** REQ-20: the whole minutes of active time in [elapsedMillis], each of which is announced once it is reached. */
fun elapsedMinutes(elapsedMillis: Long): Long = elapsedMillis / 60_000

/**
 * REQ-20: the spoken note when [minutes] of active time are reached: the time, the total distance in kilometres
 * and the average pace so far in minutes and seconds per kilometre (left out before any distance is covered).
 */
fun minuteVoiceNote(minutes: Long, distanceMeters: Double): String {
    val time = listOfNotNull((minutes / 60).takeIf { it > 0 }?.let { unit(it, "hour") }, unit(minutes % 60, "minute"))
        .joinToString(" ")
    val km = String.format(Locale.US, "%.2f", distanceMeters / 1000)
    val note = "$time. Distance $km kilometres."
    if (distanceMeters < 1.0) return note
    val paceSeconds = Math.round(minutes * 60 * 1000.0 / distanceMeters)
    return "$note Average pace ${spokenDuration(paceSeconds / 60, paceSeconds % 60)} per kilometre."
}

private fun spokenDuration(minutes: Long, seconds: Long) = "${unit(minutes, "minute")} ${unit(seconds, "second")}"

private fun unit(value: Long, name: String) = if (value == 1L) "1 $name" else "$value ${name}s"
