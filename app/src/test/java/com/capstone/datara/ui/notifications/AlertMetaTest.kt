package com.capstone.datara.ui.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class AlertMetaTest {
    private fun item(category: AlertCategory, read: Boolean) =
        AlertItem("a", "Quota reached", "msg", category, 0L, read)

    @Test fun reachedAlertTagDoesNotRepeatItsTitle() {
        assertNotEquals("Quota reached", AlertCategory.REACHED.label)
    }

    @Test fun metaNamesKindAndTimeButNotReadState() {
        val meta = alertMeta(item(AlertCategory.FORECAST, read = false), ZoneOffset.UTC)
        assertTrue(meta.startsWith("Forecast alert · "))
        assertFalse(meta.contains("Unread"))
        assertFalse(alertMeta(item(AlertCategory.QUOTA, read = true), ZoneOffset.UTC).contains("Read"))
    }
}
