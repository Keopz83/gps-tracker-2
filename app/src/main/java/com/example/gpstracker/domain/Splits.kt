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
 * the last kilometre in minutes and seconds per kilometre, or its speed in kilometres per hour when cycling.
 */
fun kmVoiceNote(activityType: ActivityType, km: Int, elapsedMillis: Long, lastKmMillis: Long): String {
    val distance = if (km == 1) "1 kilometre" else "$km kilometres"
    return "$distance. Time ${spokenTime(elapsedMillis)}. Last kilometre ${spokenPace(activityType, KM, lastKmMillis)}."
}

/** REQ-20: the whole minutes of active time in [elapsedMillis], each of which is announced once it is reached. */
fun elapsedMinutes(elapsedMillis: Long): Long = elapsedMillis / 60_000

/**
 * REQ-20: the spoken note when [minutes] of active time are reached: the time, the total distance in kilometres
 * and the average pace so far in minutes and seconds per kilometre, or the average speed when cycling (left out
 * before any distance is covered).
 */
fun minuteVoiceNote(activityType: ActivityType, minutes: Long, distanceMeters: Double): String {
    val time = listOfNotNull((minutes / 60).takeIf { it > 0 }?.let { unit(it, "hour") }, unit(minutes % 60, "minute"))
        .joinToString(" ")
    val km = String.format(Locale.US, "%.2f", distanceMeters / 1000)
    val note = "$time. Distance $km kilometres."
    if (distanceMeters < 1.0) return note
    return "$note ${spokenAverage(activityType, distanceMeters, minutes * 60_000)}."
}

/**
 * REQ-25: the spoken report when the screen is turned on: the active time, the total distance in kilometres
 * (two decimals) and the average pace, or speed when cycling, so far (left out before any distance is covered).
 */
fun screenOnVoiceNote(activityType: ActivityType, elapsedMillis: Long, distanceMeters: Double): String {
    val km = String.format(Locale.US, "%.2f", distanceMeters / 1000)
    val note = "Time ${spokenTime(elapsedMillis)}. Distance $km kilometres."
    if (distanceMeters < 1.0) return note
    return "$note ${spokenAverage(activityType, distanceMeters, elapsedMillis)}."
}

/** REQ-20, REQ-25: "Average pace …" when jogging, "Average speed …" when cycling (REQ-5). */
private fun spokenAverage(activityType: ActivityType, distanceMeters: Double, elapsedMillis: Long): String {
    val kind = if (activityType == ActivityType.CYCLING) "speed" else "pace"
    return "Average $kind ${spokenPace(activityType, distanceMeters, elapsedMillis)}"
}

/** REQ-5: minutes and seconds per kilometre for jogging, kilometres per hour (one decimal, as in the history) for cycling. */
private fun spokenPace(activityType: ActivityType, distanceMeters: Double, elapsedMillis: Long): String = when (activityType) {
    ActivityType.JOGGING -> {
        val paceSeconds = Math.round(elapsedMillis / distanceMeters)
        "${spokenDuration(paceSeconds / 60, paceSeconds % 60)} per kilometre"
    }
    ActivityType.CYCLING ->
        String.format(Locale.US, "%.1f kilometres per hour", averageSpeedKmh(distanceMeters, elapsedMillis))
}

private fun spokenTime(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis / 1000
    return listOfNotNull(
        (totalSeconds / 3600).takeIf { it > 0 }?.let { unit(it, "hour") },
        spokenDuration((totalSeconds % 3600) / 60, totalSeconds % 60),
    ).joinToString(" ")
}

private fun spokenDuration(minutes: Long, seconds: Long) = "${unit(minutes, "minute")} ${unit(seconds, "second")}"

private fun unit(value: Long, name: String) = if (value == 1L) "1 $name" else "$value ${name}s"
