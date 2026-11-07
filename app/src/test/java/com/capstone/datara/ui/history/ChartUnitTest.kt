package com.capstone.datara.ui.history

import com.capstone.datara.ui.quota.QuotaUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartUnitTest {
    @Test fun staysInMegabytesBelowOneGigabyte() {
        assertEquals(QuotaUnit.MB, chartUnit(listOf(UsagePoint("Mon", 999_999_999), UsagePoint("Tue", null))))
    }

    @Test fun switchesToGigabytesAtOneGigabyte() {
        assertEquals(QuotaUnit.GB, chartUnit(listOf(UsagePoint("Mon", 20_000_000), UsagePoint("Tue", 1_000_000_000))))
    }

    @Test fun unmeasuredOrEmptyPeriodsUseMegabytes() {
        assertEquals(QuotaUnit.MB, chartUnit(emptyList()))
        assertEquals(QuotaUnit.MB, chartUnit(listOf(UsagePoint("Mon", null))))
    }
}
