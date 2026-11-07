package com.capstone.datara.alerts

import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.data.cycle.TrackingCycle
import com.capstone.datara.data.local.entity.ActiveModel
import com.capstone.datara.data.local.entity.Notification
import com.capstone.datara.data.local.entity.NotificationType
import com.capstone.datara.domain.cycle.CycleProgress
import com.capstone.datara.domain.cycle.CycleState
import com.capstone.datara.domain.cycle.CycleUsage
import com.capstone.datara.forecast.CycleStateSource
import com.capstone.datara.forecast.ForecastStatus
import com.capstone.datara.ui.notifications.AlertCategory
import com.capstone.datara.ui.notifications.NotificationCenterViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

/** The D8 engine against in-memory stores: once per cycle, preferences, delivery and wording. */
class QuotaAlertEngineTest {

    private val hour = AlertRules.HOUR_MS
    private val start = 1_794_153_600_000L // 2026-11-09 00:00 Asia/Manila
    private var now = start + 10 * hour
    private val quota = 1_000_000_000L

    /** Mirrors Room: the unique (cycle_id, notification_type) index makes a second insert a no-op. */
    private class FakeStore : AlertStore {
        val rows = MutableStateFlow<List<Notification>>(emptyList())
        override suspend fun sentTypes(cycleId: String) = rows.value.filter { it.cycleId == cycleId }.map { it.notificationType }.toSet()
        override suspend fun insert(notification: Notification): Boolean {
            if (rows.value.any { it.cycleId == notification.cycleId && it.notificationType == notification.notificationType }) return false
            rows.value = rows.value + notification
            return true
        }
        override fun observeInbox(userId: String): Flow<List<Notification>> =
            rows.map { list -> list.filter { it.userId == userId }.sortedByDescending { it.sentAt } }
        override suspend fun markRead(notificationId: String, readAt: Long) {
            rows.value = rows.value.map { if (it.notificationId == notificationId && it.readAt == null) it.copy(readAt = readAt) else it }
        }
        override suspend fun markAllRead(userId: String, readAt: Long) {
            rows.value = rows.value.map { if (it.userId == userId && it.readAt == null) it.copy(readAt = readAt) else it }
        }
    }

    private class FakePreferences : NotificationPreferencesStore {
        val values = MutableStateFlow(AlertPreferences())
        override fun observe(userId: String): Flow<AlertPreferences> = values
        override suspend fun update(userId: String, now: Long, change: (AlertPreferences) -> AlertPreferences) {
            values.value = change(values.value)
        }
    }

    private val store = FakeStore()
    private val prefs = FakePreferences()
    private val posted = mutableListOf<Notification>()
    private val cycleState = MutableStateFlow<CycleState>(CycleState.NoCycle)
    private val forecast = MutableStateFlow<ForecastStatus>(ForecastStatus.Pending)

    private val engine = QuotaAlertEngine(
        cycles = object : CycleStateSource { override val state = cycleState },
        forecasts = object : ForecastStatusSource { override val status = forecast },
        preferences = prefs,
        store = store,
        poster = AlertPoster { posted += it },
        clock = { now },
        zone = { ZoneId.of("Asia/Manila") },
        scope = CoroutineScope(Dispatchers.Unconfined),
        locale = { Locale.US }
    )

    private fun cycle(used: Long, id: String = "c1", status: CycleStatus = CycleStatus.ACTIVE, endedAt: Long? = null) {
        val reachedAt = if (status == CycleStatus.REACHED) now else null
        val c = TrackingCycle(id, "u1", "d1", "Everyday", quota, start, reachedAt, endedAt, status, start, start)
        cycleState.value = CycleState.Tracked(CycleProgress(c, CycleUsage(used, used, reachedAt, now, 0, 0)))
    }

    private fun forecastIn(reachIn: Long?, cycleId: String = "c1") {
        forecast.value = ForecastStatus.Ready(cycleId, now - 5 * 60_000L, reachIn?.let { now + it }, 1, 1, ActiveModel.GLOBAL, "v0")
    }

    private suspend fun evaluate() = engine.evaluate(cycleState.value, forecast.value, prefs.values.value)

    @Test
    fun `each alert is recorded and posted once per cycle`() = runBlocking {
        cycle(used = 800_000_000)
        assertEquals(listOf(NotificationType.MILESTONE_75), evaluate().map { it.notificationType })
        assertTrue(evaluate().isEmpty())
        now += hour
        assertTrue(evaluate().isEmpty())
        assertEquals(1, posted.size)
        assertEquals(1, store.rows.value.size)
        val row = store.rows.value.single()
        assertEquals(QuotaAlertEngine.notificationId("c1", NotificationType.MILESTONE_75), row.notificationId)
        assertEquals("u1", row.userId)
        assertNull(row.readAt)
        assertNull(row.syncedAt) // queued for upload (C3)
    }

    @Test
    fun `usage climbing through the levels sends each one once, in order`() = runBlocking {
        listOf(500_000_000L, 760_000_000L, 800_000_000L, 910_000_000L, 990_000_000L, quota, quota).forEach {
            cycle(used = it)
            evaluate()
            now += 10 * 60_000L
        }
        assertEquals(listOf(NotificationType.MILESTONE_75, NotificationType.MILESTONE_90, NotificationType.QUOTA_REACHED),
            posted.map { it.notificationType })
    }

    @Test
    fun `a new cycle gets its own alerts`() = runBlocking {
        cycle(used = quota, status = CycleStatus.REACHED)
        evaluate()
        cycle(used = 950_000_000, id = "c2")
        evaluate()
        assertEquals(listOf("c1" to NotificationType.QUOTA_REACHED, "c2" to NotificationType.MILESTONE_90),
            posted.map { it.cycleId to it.notificationType })
        assertNotEquals(posted[0].notificationId, QuotaAlertEngine.notificationId("c2", NotificationType.QUOTA_REACHED))
    }

    @Test
    fun `nothing is recorded while a preference is off`() = runBlocking {
        prefs.values.value = AlertPreferences(milestones = false, forecasts = false)
        cycle(used = 950_000_000)
        forecastIn(2 * hour)
        assertTrue(evaluate().isEmpty())
        assertTrue(store.rows.value.isEmpty())
        // Turned back on while still true: sent then, once
        prefs.values.value = AlertPreferences(milestones = true, forecasts = false)
        assertEquals(listOf(NotificationType.MILESTONE_90), evaluate().map { it.notificationType })
    }

    @Test
    fun `forecast alerts follow the latest forecast and never re-fire`() = runBlocking {
        cycle(used = 100_000_000)
        forecastIn(reachIn = null) // beyond 30 days
        assertTrue(evaluate().isEmpty())
        forecast.value = ForecastStatus.Collecting(4, 6)
        assertTrue(evaluate().isEmpty())
        forecastIn(20 * hour)
        assertEquals(listOf(NotificationType.FORECAST_24H), evaluate().map { it.notificationType })
        forecastIn(40 * hour) // moved away
        assertTrue(evaluate().isEmpty())
        forecastIn(20 * hour) // and back: not re-sent
        assertTrue(evaluate().isEmpty())
        forecastIn(2 * hour)
        assertEquals(listOf(NotificationType.FORECAST_3H), evaluate().map { it.notificationType })
        assertEquals(2, posted.size)
    }

    @Test
    fun `an ended cycle sends nothing`() = runBlocking {
        cycle(used = quota, status = CycleStatus.ENDED, endedAt = now)
        forecastIn(hour)
        assertTrue(evaluate().isEmpty())
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `start follows the cycle, forecast and preference flows`() = runBlocking {
        engine.start()
        cycle(used = 760_000_000)
        assertEquals(listOf(NotificationType.MILESTONE_75), posted.map { it.notificationType })
        forecastIn(2 * hour)
        assertEquals(NotificationType.FORECAST_3H, posted.last().notificationType)
        cycle(used = 770_000_000) // another reading: nothing new
        assertEquals(2, posted.size)
    }

    @Test
    fun `wording says nothing is blocked and gives the figures`() = runBlocking {
        cycle(used = 900_000_000)
        val ninety = evaluate().single()
        assertEquals("90% of your quota used", ninety.title)
        assertEquals("900 MB of 1 GB used on “Everyday”. Only 100 MB left. Nothing is blocked.", ninety.message)

        cycle(used = quota, status = CycleStatus.REACHED)
        val reached = evaluate().single()
        assertEquals("Quota reached", reached.title)
        assertTrue(reached.message, reached.message.contains("never blocks data, so your connection stays on"))

        now = start + 10 * hour // 10:00 in Manila
        cycle(used = 100_000_000, id = "c3")
        forecastIn(2 * hour + 30 * 60_000L, cycleId = "c3")
        val soon = evaluate().single()
        assertEquals("Quota likely reached within 3 hours", soon.title)
        assertEquals("At your recent pace, “Everyday” may reach 1 GB around 12:30 PM. 900 MB left. This is an estimate.", soon.message)

        cycle(used = 100_000_000, id = "c4")
        forecastIn(20 * hour, cycleId = "c4")
        assertTrue(evaluate().single().message.contains("around Tue 6:00 AM"))
    }

    @Test
    fun `inbox rows map to center items with read state and category`() = runBlocking {
        cycle(used = quota, status = CycleStatus.REACHED)
        evaluate()
        val item = NotificationCenterViewModel.toItem(store.rows.value.single())
        assertEquals(AlertCategory.REACHED, item.category)
        assertFalse(item.isRead)
        assertEquals("c1", item.cycleId)
        store.markAllRead("u1", now)
        assertTrue(NotificationCenterViewModel.toItem(store.rows.value.single()).isRead)
    }
}
