package com.example.gpstracker.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val GPX_MIME_TYPE = "application/gpx+xml"

/** REQ-8: the GPX activity type recognised by common sports apps (Strava, Garmin Connect, Komoot, ...). */
fun gpxType(activityType: ActivityType): String = when (activityType) {
    ActivityType.JOGGING -> "running"
    ActivityType.CYCLING -> "cycling"
}

/**
 * REQ-8: the run as a GPX 1.1 document, or null when no position was recorded.
 *
 * Each pause/resume segment becomes its own `<trkseg>`, so importing apps do not join the paused gap either.
 */
fun toGpx(activityType: ActivityType, track: List<List<LocationFix>>): String? {
    val segments = track.filter { it.isNotEmpty() }
    if (segments.isEmpty()) return null
    val startTime = isoTime(segments.first().first().timeMillis)
    return buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine(
            """<gpx version="1.1" creator="Summit" xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""",
        )
        appendLine("  <metadata><time>$startTime</time></metadata>")
        appendLine("  <trk>")
        appendLine("    <name>${activityType.label} $startTime</name>")
        appendLine("    <type>${gpxType(activityType)}</type>")
        segments.forEach { segment ->
            appendLine("    <trkseg>")
            segment.forEach { fix ->
                // REQ-23: the altitude, when known, as `<ele>` (which GPX puts before `<time>`).
                val ele = fix.altitudeMeters?.let { String.format(Locale.US, "<ele>%.1f</ele>", it) }.orEmpty()
                appendLine(
                    String.format(
                        Locale.US,
                        "      <trkpt lat=\"%.7f\" lon=\"%.7f\">%s<time>%s</time></trkpt>",
                        fix.latitude, fix.longitude, ele, isoTime(fix.timeMillis),
                    ),
                )
            }
            appendLine("    </trkseg>")
        }
        appendLine("  </trk>")
        appendLine("</gpx>")
    }
}

/** REQ-8: a sortable file name for a run started at [startMillis], e.g. `2026-11-01_0741_jogging.gpx`. */
fun gpxFileName(activityType: ActivityType, startMillis: Long, zone: ZoneId): String {
    val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", Locale.US).format(Instant.ofEpochMilli(startMillis).atZone(zone))
    return "${stamp}_${activityType.name.lowercase(Locale.US)}.gpx"
}

private fun isoTime(millis: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))

private val TRKSEG = Regex("""<trkseg>(.*?)</trkseg>""", RegexOption.DOT_MATCHES_ALL)
private val TRKPT = Regex("""<trkpt lat="([-0-9.]+)" lon="([-0-9.]+)">(?:<ele>([^<]+)</ele>)?<time>([^<]+)</time></trkpt>""")

/** REQ-17: the track of a saved run (REQ-8), one list of positions per segment, to show it on the map (with altitudes, REQ-23). */
fun parseGpxTrack(gpx: String): List<List<LocationFix>> = TRKSEG.findAll(gpx).map { segment ->
    TRKPT.findAll(segment.groupValues[1]).mapNotNull { point ->
        val lat = point.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
        val lon = point.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
        val time = runCatching { Instant.parse(point.groupValues[4]).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
        LocationFix(lat, lon, accuracyMeters = 0f, timeMillis = time, altitudeMeters = point.groupValues[3].toDoubleOrNull())
    }.toList()
}.filter { it.isNotEmpty() }.toList()
