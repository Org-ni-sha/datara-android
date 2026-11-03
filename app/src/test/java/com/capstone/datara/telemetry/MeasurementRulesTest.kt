package com.capstone.datara.telemetry

import com.capstone.datara.telemetry.MeasurementRules.APP_GAP_THRESHOLD_MS
import com.capstone.datara.telemetry.MeasurementRules.GAP_THRESHOLD_MS
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same cases as data-harvester's MeasurementRulesTest, so a drift from the harvester fails here. */
class MeasurementRulesTest {

    private val radio = { "LTE" }

    @Test
    fun `intervals and thresholds match the harvester`() {
        assertEquals(30_000L, MeasurementRules.COLLECTION_INTERVAL_MS)
        assertEquals(600_000L, MeasurementRules.APP_COLLECTION_INTERVAL_MS)
        assertEquals(75_000L, GAP_THRESHOLD_MS)
        assertEquals(1_260_000L, APP_GAP_THRESHOLD_MS)
        assertEquals(3, MeasurementRules.SCHEMA_VERSION)
    }

    // Network label of primary intervals

    @Test
    fun `any mobile bytes on wifi get the mobile label`() {
        assertEquals("LTE", MeasurementRules.primaryNetworkLabel("WIFI", false, 2_048L, radio))
        assertEquals("LTE", MeasurementRules.primaryNetworkLabel("WIFI", false, 1L, radio))
    }

    @Test
    fun `wifi without mobile bytes stays wifi`() {
        assertEquals("WIFI", MeasurementRules.primaryNetworkLabel("WIFI", false, 0L, radio))
    }

    @Test
    fun `mobile default network during the interval gets the mobile label even without bytes`() {
        assertEquals("LTE", MeasurementRules.primaryNetworkLabel("WIFI", true, 0L, radio))
    }

    @Test
    fun `bytes with no active network get the mobile label`() {
        assertEquals("LTE", MeasurementRules.primaryNetworkLabel("NONE", false, 500L, radio))
        assertEquals("NONE", MeasurementRules.primaryNetworkLabel("NONE", false, 0L, radio))
    }

    @Test
    fun `cellular label is kept`() {
        assertEquals("5G", MeasurementRules.primaryNetworkLabel("5G", false, 10L) { "LTE" })
    }

    // Status of primary intervals

    @Test
    fun `ordinary interval is OK`() {
        assertEquals("OK", MeasurementRules.primaryStatus("OK", 30_000L, false))
    }

    @Test
    fun `recovered after a failed reading is INTERRUPTED`() {
        assertEquals("INTERRUPTED", MeasurementRules.primaryStatus("OK", 60_000L, true))
    }

    @Test
    fun `long or unknown span is a GAP`() {
        assertEquals("GAP", MeasurementRules.primaryStatus("OK", GAP_THRESHOLD_MS + 1, false))
        assertEquals("GAP", MeasurementRules.primaryStatus("OK", -1L, false))
        assertEquals("GAP", MeasurementRules.primaryStatus("OK", GAP_THRESHOLD_MS + 1, true))
        assertEquals("OK", MeasurementRules.primaryStatus("OK", GAP_THRESHOLD_MS, false))
    }

    @Test
    fun `counter problems keep their own status`() {
        for (s in listOf("BASELINE", "COUNTER_RESET", "COUNTER_JUMP", "UNSUPPORTED")) {
            assertEquals(s, MeasurementRules.primaryStatus(s, 30_000L, true))
        }
    }

    // Status of 10-minute intervals

    @Test
    fun `ten minute interval is OK`() {
        assertEquals("OK", MeasurementRules.appStatus(0L, 600_000L, false))
    }

    @Test
    fun `interval after a long interruption is a GAP`() {
        assertEquals("GAP", MeasurementRules.appStatus(0L, 3 * 3_600_000L, false))
        assertEquals("OK", MeasurementRules.appStatus(0L, APP_GAP_THRESHOLD_MS, false))
    }

    @Test
    fun `clock running backwards is a CLOCK_ERROR`() {
        assertEquals("CLOCK_ERROR", MeasurementRules.appStatus(600_000L, 0L, false))
    }

    @Test
    fun `counter reset wins over span checks`() {
        assertEquals("COUNTER_RESET", MeasurementRules.appStatus(600_000L, 0L, true))
    }
}
