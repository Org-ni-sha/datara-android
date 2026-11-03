package com.capstone.datara.telemetry

import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioLabelsTest {

    @Test
    fun `network types use the harvester's labels`() {
        assertEquals("5G", RadioLabels.cellularRadioLabel(TelephonyManager.NETWORK_TYPE_NR))
        assertEquals("LTE", RadioLabels.cellularRadioLabel(TelephonyManager.NETWORK_TYPE_LTE))
        assertEquals("3G", RadioLabels.cellularRadioLabel(TelephonyManager.NETWORK_TYPE_HSPAP))
        assertEquals("2G", RadioLabels.cellularRadioLabel(TelephonyManager.NETWORK_TYPE_EDGE))
        assertEquals("CELLULAR", RadioLabels.cellularRadioLabel(TelephonyManager.NETWORK_TYPE_UNKNOWN))
    }

    @Test
    fun `unavailable signal becomes the unknown sentinel`() {
        assertEquals(-97, RadioLabels.signalDbm(-97))
        assertEquals(RadioLabels.SIGNAL_UNKNOWN, RadioLabels.signalDbm(Int.MAX_VALUE))
        assertEquals(RadioLabels.SIGNAL_UNKNOWN, RadioLabels.signalDbm(null))
    }

    @Test
    fun `gsm asu converts to dbm and 99 is unknown`() {
        assertEquals(-113, RadioLabels.gsmAsuToDbm(0))
        assertEquals(-51, RadioLabels.gsmAsuToDbm(31))
        assertEquals(RadioLabels.SIGNAL_UNKNOWN, RadioLabels.gsmAsuToDbm(99))
    }

    @Test
    fun `carrier is the first non-blank name`() {
        assertEquals("Smart", RadioLabels.carrierName(null, " ", " Smart ", "Globe"))
        assertEquals(RadioLabels.CARRIER_UNKNOWN, RadioLabels.carrierName(null, ""))
    }
}
