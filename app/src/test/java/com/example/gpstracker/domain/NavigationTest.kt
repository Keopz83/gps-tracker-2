package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTest {
    @Test
    fun `REQ-16 swiping right from the front view reaches the map, the history, the settings and then the front view again`() {
        assertEquals(Page.TRACKER, pageAt(START_INDEX))
        assertEquals(Page.MAP, pageAt(START_INDEX + 1))
        assertEquals(Page.HISTORY, pageAt(START_INDEX + 2))
        assertEquals(Page.SETTINGS, pageAt(START_INDEX + 3))
        assertEquals(Page.TRACKER, pageAt(START_INDEX + 4))
    }

    @Test
    fun `REQ-16 swiping left from the front view reaches the settings`() {
        assertEquals(Page.SETTINGS, pageAt(START_INDEX - 1))
        assertEquals(Page.HISTORY, pageAt(START_INDEX - 2))
    }

    @Test
    fun `REQ-16 the swipe order matches the icons at the bottom from left to right`() {
        assertEquals(listOf(Page.TRACKER, Page.MAP, Page.HISTORY, Page.SETTINGS), Page.entries)
    }

    @Test
    fun `REQ-16 picking an icon takes the shortest way round`() {
        assertEquals(START_INDEX + 1, indexOf(Page.MAP, START_INDEX))
        assertEquals(START_INDEX - 1, indexOf(Page.SETTINGS, START_INDEX))
        assertEquals(START_INDEX + 4, indexOf(Page.TRACKER, START_INDEX + 3))
        assertEquals(START_INDEX + 2, indexOf(Page.HISTORY, START_INDEX + 2))
    }
}
