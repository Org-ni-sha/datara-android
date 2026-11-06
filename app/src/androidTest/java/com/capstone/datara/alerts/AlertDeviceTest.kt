package com.capstone.datara.alerts

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import com.capstone.datara.data.local.DataraDatabase
import com.capstone.datara.data.local.entity.Notification
import com.capstone.datara.data.local.entity.NotificationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * D8 on a phone: Room's once-per-cycle rule and the preferences row (in-memory database, the
 * app's own data is not touched), and posting to the real Android channels.
 *
 * Runs without wiping the app: install the test APKs and use
 * `adb shell am instrument -w -e class com.capstone.datara.alerts.AlertDeviceTest com.capstone.datara.test/androidx.test.runner.AndroidJUnitRunner`.
 */
class AlertDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = DataraDatabase.inMemory(context)
    private val alerts = RoomAlertStore(db.notificationDao())
    private val preferences = RoomNotificationPreferencesStore(db.userDao())

    private fun alert(type: String, cycleId: String = "test-cycle") = Notification(
        notificationId = QuotaAlertEngine.notificationId(cycleId, type), userId = "u1", cycleId = cycleId,
        notificationType = type, title = "75% of your quota used",
        message = "750 MB of 1 GB used on “Smoke test”. 250 MB left. Nothing is blocked.",
        sentAt = System.currentTimeMillis(), readAt = null
    )

    @After
    fun tearDown() {
        db.close()
        context.getSystemService(NotificationManager::class.java)
            .cancel(QuotaAlertEngine.notificationId("smoke-test-cycle", NotificationType.MILESTONE_75).hashCode())
    }

    @Test
    fun roomRecordsEachTypeOncePerCycle() = runBlocking {
        assertTrue(alerts.insert(alert(NotificationType.MILESTONE_75)))
        assertFalse(alerts.insert(alert(NotificationType.MILESTONE_75).copy(notificationId = "other-id")))
        assertTrue(alerts.insert(alert(NotificationType.MILESTONE_90)))
        assertTrue(alerts.insert(alert(NotificationType.MILESTONE_75, cycleId = "next-cycle")))
        assertEquals(setOf(NotificationType.MILESTONE_75, NotificationType.MILESTONE_90), alerts.sentTypes("test-cycle"))

        alerts.markRead(QuotaAlertEngine.notificationId("test-cycle", NotificationType.MILESTONE_75), 1_000)
        alerts.markAllRead("u1", 2_000)
        val inbox = alerts.observeInbox("u1").first().associateBy { it.notificationId }
        assertEquals(1_000L, inbox.getValue(QuotaAlertEngine.notificationId("test-cycle", NotificationType.MILESTONE_75)).readAt)
        assertEquals(2_000L, inbox.getValue(QuotaAlertEngine.notificationId("test-cycle", NotificationType.MILESTONE_90)).readAt)
    }

    @Test
    fun preferencesPersistAndDefaultToOn() = runBlocking {
        assertEquals(AlertPreferences(), preferences.observe("u1").first())
        preferences.update("u1", 5_000) { it.copy(forecasts = false) }
        assertEquals(AlertPreferences(milestones = true, forecasts = false), preferences.observe("u1").first())
        val row = db.userDao().preferences("u1")
        assertNotNull(row)
        assertEquals(5_000L, row!!.updatedAt)
        assertEquals(null, row.syncedAt)
    }

    @Test
    fun postsAMilestoneOnTheMilestonesChannel() {
        assumeTrue("Notifications are not allowed for the app on this device", QuotaAlertNotifications.canPost(context))
        val manager = context.getSystemService(NotificationManager::class.java)
        QuotaAlertNotifications.post(context, alert(NotificationType.MILESTONE_75, cycleId = "smoke-test-cycle"))
        assertNotNull(manager.getNotificationChannel(QuotaAlertNotifications.MILESTONES_CHANNEL_ID))
        assertNotNull(manager.getNotificationChannel(QuotaAlertNotifications.FORECASTS_CHANNEL_ID))
        val id = QuotaAlertEngine.notificationId("smoke-test-cycle", NotificationType.MILESTONE_75).hashCode()
        val shown = manager.activeNotifications.firstOrNull { it.id == id }
        assertNotNull("Milestone notification not shown", shown)
        assertEquals(QuotaAlertNotifications.MILESTONES_CHANNEL_ID, shown!!.notification.channelId)
        assertNotNull(shown.notification.contentIntent)
    }
}
