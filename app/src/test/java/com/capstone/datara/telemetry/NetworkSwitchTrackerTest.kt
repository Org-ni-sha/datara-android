package com.capstone.datara.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSwitchTrackerTest {

    private fun tracker(initial: String) = NetworkSwitchTracker().apply { initialize(initial) }

    @Test
    fun `first report of the same network is not a switch`() {
        assertNull(tracker("WIFI").onDefaultNetworkChanged("WIFI", 1_000))
    }

    @Test
    fun `wifi to mobile closes the interval under wifi and runs the durable read`() {
        val switch = tracker("WIFI").onDefaultNetworkChanged("MOBILE", 1_000)
        assertEquals(NetworkSwitchTracker.Switch(from = "WIFI", runDurableRead = true), switch)
    }

    @Test
    fun `coming back from no network skips the durable read`() {
        val switch = tracker("NONE").onDefaultNetworkChanged("MOBILE", 1_000)
        assertEquals("NONE", switch?.from)
        assertFalse(switch!!.runDurableRead)
    }

    @Test
    fun `switches inside the debounce are not closed but mark mobile as seen`() {
        val t = tracker("WIFI")
        t.onDefaultNetworkChanged("NONE", 10_000)
        t.onIntervalClosed()
        assertFalse(t.cellularSeenThisInterval)
        assertNull(t.onDefaultNetworkChanged("MOBILE", 12_000))
        assertTrue(t.cellularSeenThisInterval)
        // After the debounce a switch closes again
        assertEquals("MOBILE", t.onDefaultNetworkChanged("WIFI", 20_000)?.from)
    }

    @Test
    fun `closing an interval resets mobile seen to the current network`() {
        val t = tracker("MOBILE")
        assertTrue(t.cellularSeenThisInterval)
        t.onDefaultNetworkChanged("WIFI", 1_000)
        t.onIntervalClosed()
        assertFalse(t.cellularSeenThisInterval)
    }

    @Test
    fun `interval network follows the network it ran on`() {
        val radio = { "LTE" }
        assertEquals("5G", NetworkSwitchTracker.intervalNetwork(null, "5G", radio))
        assertEquals("WIFI", NetworkSwitchTracker.intervalNetwork("WIFI", "LTE", radio))
        assertEquals("LTE", NetworkSwitchTracker.intervalNetwork("MOBILE", "WIFI", radio))
        assertEquals("NONE", NetworkSwitchTracker.intervalNetwork("NONE", "WIFI", radio))
        assertEquals("WIFI", NetworkSwitchTracker.intervalNetwork("OTHER", "WIFI", radio))
    }
}
