package com.capstone.datara.ui.tracking

import com.capstone.datara.telemetry.TrackingAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingSetupUiStateTest {

    private val required = TrackingAccess(usageAccess = true, notifications = true)

    @Test
    fun `start needs usage access and notifications, not phone state or battery`() {
        assertTrue(TrackingSetupUiState(required).canStart)
        assertFalse(TrackingSetupUiState(required.copy(usageAccess = false)).canStart)
        assertFalse(TrackingSetupUiState(required.copy(notifications = false)).canStart)
    }

    @Test
    fun `blocked start explains what is missing`() {
        assertEquals("Turn on Usage Access and allow notifications to start.", TrackingSetupUiState().startBlockedReason)
        assertEquals("Turn on Usage Access to start.", TrackingSetupUiState(required.copy(usageAccess = false)).startBlockedReason)
        assertEquals("Allow notifications to start.", TrackingSetupUiState(required.copy(notifications = false)).startBlockedReason)
        assertNull(TrackingSetupUiState(required).startBlockedReason)
    }

    @Test
    fun `status distinguishes on, off and paused`() {
        assertEquals(TrackingStatus.OFF, TrackingSetupUiState(required).status)
        assertEquals(TrackingStatus.ON, TrackingSetupUiState(required, isRunning = true, shouldCollect = true).status)
        // Left on by the user, but Android stopped it
        assertEquals(TrackingStatus.PAUSED, TrackingSetupUiState(required, isRunning = false, shouldCollect = true).status)
    }

    @Test
    fun `running tracking can be stopped but not started again`() {
        val running = TrackingSetupUiState(required, isRunning = true, shouldCollect = true)
        assertFalse(running.canStart)
        assertTrue(running.canStop)
        assertNull(running.startBlockedReason)
        assertFalse(TrackingSetupUiState(required).canStop)
    }
}
