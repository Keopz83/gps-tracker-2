package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTest {
    @Test
    fun `REQ-16 REQ-29 swiping right from the front view reaches the map, the history, the statistics, the settings and then the front view again`() {
        assertEquals(Page.TRACKER, pageAt(START_INDEX))
        assertEquals(Page.MAP, pageAt(START_INDEX + 1))
        assertEquals(Page.HISTORY, pageAt(START_INDEX + 2))
        assertEquals(Page.STATS, pageAt(START_INDEX + 3))
        assertEquals(Page.SETTINGS, pageAt(START_INDEX + 4))
        assertEquals(Page.TRACKER, pageAt(START_INDEX + 5))
    }

    @Test
    fun `REQ-16 swiping left from the front view reaches the settings`() {
        assertEquals(Page.SETTINGS, pageAt(START_INDEX - 1))
        assertEquals(Page.STATS, pageAt(START_INDEX - 2))
    }

    @Test
    fun `REQ-16 REQ-29 the swipe order is the front view, then the icons at the bottom from left to right`() {
        assertEquals(listOf(Page.TRACKER, Page.MAP, Page.HISTORY, Page.STATS, Page.SETTINGS), Page.entries)
    }

    @Test
    fun `REQ-16 picking an icon takes the shortest way round`() {
        assertEquals(START_INDEX + 1, indexOf(Page.MAP, START_INDEX))
        assertEquals(START_INDEX - 1, indexOf(Page.SETTINGS, START_INDEX))
        assertEquals(START_INDEX + 5, indexOf(Page.TRACKER, START_INDEX + 4))
        assertEquals(START_INDEX - 2, indexOf(Page.STATS, START_INDEX))
        assertEquals(START_INDEX + 2, indexOf(Page.HISTORY, START_INDEX + 2))
    }

    @Test
    fun `REQ-19 a one-finger drag on the map past half the view moves to the next or previous view`() {
        assertEquals(1, mapSwipeStep(dragPx = -600f, widthPx = 1000f, velocityPxPerSecond = 0f, minFlingVelocity = 100f))
        assertEquals(-1, mapSwipeStep(dragPx = 600f, widthPx = 1000f, velocityPxPerSecond = 0f, minFlingVelocity = 100f))
    }

    @Test
    fun `REQ-19 a short slow drag on the map stays on the map`() {
        assertEquals(0, mapSwipeStep(dragPx = -300f, widthPx = 1000f, velocityPxPerSecond = -50f, minFlingVelocity = 100f))
    }

    @Test
    fun `REQ-19 a flick on the map moves on in its direction`() {
        assertEquals(1, mapSwipeStep(dragPx = -100f, widthPx = 1000f, velocityPxPerSecond = -800f, minFlingVelocity = 100f))
        assertEquals(-1, mapSwipeStep(dragPx = 100f, widthPx = 1000f, velocityPxPerSecond = 800f, minFlingVelocity = 100f))
    }
}
