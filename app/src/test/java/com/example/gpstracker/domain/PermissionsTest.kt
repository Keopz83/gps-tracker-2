package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsTest {

    @Test
    fun `REQ-6 location permissions are requested on every Android version`() {
        assertEquals(listOf(PERMISSION_FINE_LOCATION, PERMISSION_COARSE_LOCATION), requiredPermissions(26))
        assertEquals(listOf(PERMISSION_FINE_LOCATION, PERMISSION_COARSE_LOCATION), requiredPermissions(32))
    }

    @Test
    fun `REQ-6 notification permission is also requested from Android 13`() {
        assertEquals(
            listOf(PERMISSION_FINE_LOCATION, PERMISSION_COARSE_LOCATION, PERMISSION_POST_NOTIFICATIONS),
            requiredPermissions(33),
        )
    }

    @Test
    fun `REQ-6 tracking starts when precise location is granted even without notifications`() {
        assertTrue(canStartTracking(mapOf(PERMISSION_FINE_LOCATION to true, PERMISSION_POST_NOTIFICATIONS to false)))
    }

    @Test
    fun `REQ-6 tracking does not start without precise location`() {
        assertFalse(canStartTracking(mapOf(PERMISSION_FINE_LOCATION to false, PERMISSION_COARSE_LOCATION to true)))
        assertFalse(canStartTracking(emptyMap()))
    }
}
