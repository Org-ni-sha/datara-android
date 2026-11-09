package com.capstone.datara.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountUpPartsTest {
    @Test fun daysCountAsWholeNumbers() {
        val parts = CountUpParts.parse("20 days")!!
        assertEquals(20.0, parts.number, 0.0)
        assertEquals("7 days", parts.format(7.4))
        assertEquals("20 days", parts.format(20.0))
    }

    @Test fun decimalsAreKept() {
        val parts = CountUpParts.parse("28.21 GB")!!
        assertEquals(2, parts.decimals)
        assertEquals("3.50 GB", parts.format(3.5))
    }

    @Test fun groupedThousandsStayGrouped() {
        val parts = CountUpParts.parse("1,000 MB")!!
        assertEquals(1000.0, parts.number, 0.0)
        assertEquals("1,000 MB", parts.format(1000.0))
        assertEquals("999 MB", parts.format(999.0))
    }

    @Test fun suffixWithPlusIsKept() {
        assertEquals("30+ days", CountUpParts.parse("30+ days")!!.format(30.0))
    }

    @Test fun timesSignsAndPlaceholdersAreNotAnimated() {
        assertNull(CountUpParts.parse("14:00"))
        assertNull(CountUpParts.parse("+3 h"))
        assertNull(CountUpParts.parse("—"))
        assertNull(CountUpParts.parse("…"))
    }
}
