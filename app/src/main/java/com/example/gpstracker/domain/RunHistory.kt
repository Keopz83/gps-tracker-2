package com.example.gpstracker.domain

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** REQ-9: one finished run as listed in the run history. */
data class RunRecord(
    val activityType: ActivityType,
    val startMillis: Long,
    val elapsedMillis: Long,
    val distanceMeters: Double,
)

/** REQ-9: the history entry for a stopped session, or null when no position was recorded (as for REQ-8). */
fun runRecord(stats: SessionStats): RunRecord? {
    val start = stats.track.firstOrNull { it.isNotEmpty() }?.first() ?: return null
    return RunRecord(stats.activityType, start.timeMillis, stats.elapsedMillis, stats.distanceMeters)
}

/** REQ-9: the run's start as a local date and time, e.g. `2026-11-01 07:41`. */
fun formatRunDate(startMillis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US).format(Instant.ofEpochMilli(startMillis).atZone(zone))

/**
 * REQ-9: the runs stored in [file], one tab-separated line per run, so the history survives app restarts.
 *
 * Unreadable lines are skipped rather than losing the whole history.
 */
class RunHistory(private val file: File) {

    /** All saved runs, newest first. */
    fun load(): List<RunRecord> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull(::decode).sortedByDescending { it.startMillis }
    }

    fun add(record: RunRecord) {
        file.parentFile?.mkdirs()
        file.appendText(encode(record) + "\n")
    }

    private fun encode(record: RunRecord): String = listOf(
        record.activityType.name,
        record.startMillis,
        record.elapsedMillis,
        record.distanceMeters,
    ).joinToString("\t")

    private fun decode(line: String): RunRecord? {
        val fields = line.split("\t")
        if (fields.size != 4) return null
        return RunRecord(
            activityType = ActivityType.entries.firstOrNull { it.name == fields[0] } ?: return null,
            startMillis = fields[1].toLongOrNull() ?: return null,
            elapsedMillis = fields[2].toLongOrNull() ?: return null,
            distanceMeters = fields[3].toDoubleOrNull() ?: return null,
        )
    }
}
