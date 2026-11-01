package com.example.gpstracker.domain

/**
 * REQ-16: the views in swipe order, matching the icons at the bottom from left to right. Swiping right past the
 * settings returns to the front view, so the views rotate.
 */
enum class Page { TRACKER, MAP, HISTORY, SETTINGS }

/** REQ-16: the app opens on the front view. */
val START_PAGE = Page.TRACKER

/** REQ-16: the pager holds the views repeated many times over, so swiping can rotate through them in both directions. */
val PAGER_SIZE = Page.entries.size * 10_000

/** REQ-16: the first pager position, in the middle so the user can swipe either way for a long time. */
val START_INDEX = PAGER_SIZE / 2 / Page.entries.size * Page.entries.size + START_PAGE.ordinal

/** REQ-16: the view shown at pager position [index]. */
fun pageAt(index: Int): Page = Page.entries[Math.floorMod(index, Page.entries.size)]

/** REQ-16: the pager position nearest to [current] that shows [target], so picking an icon takes the shortest way round. */
fun indexOf(target: Page, current: Int): Int {
    val size = Page.entries.size
    var delta = Math.floorMod(target.ordinal - current, size)
    if (delta > size / 2) delta -= size
    return current + delta
}

/**
 * REQ-7, REQ-17: the finished run shown on the map when no session is active: the run selected in the history
 * (identified by its start), else the newest run. While a session is active the map shows that session instead (null).
 */
fun mapRun(sessionActive: Boolean, selectedStart: Long?, history: List<RunRecord>): RunRecord? {
    if (sessionActive) return null
    return history.firstOrNull { it.startMillis == selectedStart } ?: history.firstOrNull()
}
