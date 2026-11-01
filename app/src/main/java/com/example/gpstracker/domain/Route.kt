package com.example.gpstracker.domain

/** The smallest latitude/longitude box that contains every recorded position. */
data class RouteBounds(val north: Double, val south: Double, val east: Double, val west: Double)

/** REQ-7: whether at least one position has been recorded, so there is something to show on the map. */
fun hasRoute(track: List<List<LocationFix>>): Boolean = track.any { it.isNotEmpty() }

/** REQ-7: the area the map zooms to so the whole path taken so far is visible, or null when nothing was recorded. */
fun routeBounds(track: List<List<LocationFix>>): RouteBounds? {
    val fixes = track.flatten()
    if (fixes.isEmpty()) return null
    return RouteBounds(
        north = fixes.maxOf { it.latitude },
        south = fixes.minOf { it.latitude },
        east = fixes.maxOf { it.longitude },
        west = fixes.minOf { it.longitude },
    )
}

/** REQ-7: the most recently recorded position, marked on the map as the current position. */
fun lastPosition(track: List<List<LocationFix>>): LocationFix? = track.lastOrNull { it.isNotEmpty() }?.last()
