package com.example.gpstracker.domain

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** REQ-9: one finished run as listed in the run history, with where it started and the name of that place once known. */
data class RunRecord(
    val activityType: ActivityType,
    val startMillis: Long,
    val elapsedMillis: Long,
    val distanceMeters: Double,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val place: String? = null,
)

/** REQ-9: the history entry for a stopped session, or null when no position was recorded (as for REQ-8). */
fun runRecord(stats: SessionStats): RunRecord? {
    val start = stats.track.firstOrNull { it.isNotEmpty() }?.first() ?: return null
    return RunRecord(stats.activityType, start.timeMillis, stats.elapsedMillis, stats.distanceMeters, start.latitude, start.longitude)
}

/** REQ-9: the run's start as a local date and time, e.g. `2026-11-01 07:41`. */
fun formatRunDate(startMillis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US).format(Instant.ofEpochMilli(startMillis).atZone(zone))

/** REQ-9: the most specific place name of an address: town, then district, region or country. */
fun placeName(locality: String?, subAdminArea: String?, adminArea: String?, countryName: String?): String? =
    listOf(locality, subAdminArea, adminArea, countryName).firstOrNull { !it.isNullOrBlank() }?.trim()

private val FIRST_TRKPT = Regex("""<trkpt lat="([-0-9.]+)" lon="([-0-9.]+)"""")

/** REQ-9: the first position of a saved GPX run (REQ-8), used to name the place of runs recorded before it was stored. */
fun firstGpxPosition(gpx: String): Pair<Double, Double>? {
    val match = FIRST_TRKPT.find(gpx) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lon = match.groupValues[2].toDoubleOrNull() ?: return null
    return lat to lon
}

/**
 * REQ-9: the runs stored in [file], one tab-separated line per run, so the history survives app restarts.
 *
 * Unreadable lines are skipped rather than losing the whole history. Lines written before the start position and
 * place were stored (4 fields) are still read.
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

    /** Replaces the stored run that has the same start and activity as [record], e.g. once its place is known. */
    fun update(record: RunRecord) {
        val runs = load().map { if (it.startMillis == record.startMillis && it.activityType == record.activityType) record else it }
        file.parentFile?.mkdirs()
        file.writeText(runs.sortedBy { it.startMillis }.joinToString("") { encode(it) + "\n" })
    }

    private fun encode(record: RunRecord): String = listOf(
        record.activityType.name,
        record.startMillis,
        record.elapsedMillis,
        record.distanceMeters,
        record.startLatitude ?: "",
        record.startLongitude ?: "",
        record.place?.replace(Regex("\\s+"), " ") ?: "",
    ).joinToString("\t")

    private fun decode(line: String): RunRecord? {
        val fields = line.split("\t")
        if (fields.size != 4 && fields.size != 7) return null
        return RunRecord(
            activityType = ActivityType.entries.firstOrNull { it.name == fields[0] } ?: return null,
            startMillis = fields[1].toLongOrNull() ?: return null,
            elapsedMillis = fields[2].toLongOrNull() ?: return null,
            distanceMeters = fields[3].toDoubleOrNull() ?: return null,
            startLatitude = fields.getOrNull(4)?.toDoubleOrNull(),
            startLongitude = fields.getOrNull(5)?.toDoubleOrNull(),
            place = fields.getOrNull(6)?.takeIf { it.isNotBlank() },
        )
    }
}
