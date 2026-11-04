package com.capstone.datara.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.capstone.datara.data.local.entity.ActiveModel
import com.capstone.datara.data.local.entity.CycleStatus
import com.capstone.datara.data.local.entity.DataUsage
import com.capstone.datara.data.local.entity.DurableReadingEntity
import com.capstone.datara.data.local.entity.Forecast
import com.capstone.datara.data.local.entity.Notification
import com.capstone.datara.data.local.entity.NotificationPreferences
import com.capstone.datara.data.local.entity.NotificationType
import com.capstone.datara.data.local.entity.TrackingCycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** The DAO queries D3, D6, D7, D8 and C3 rely on, and the keys mirrored from Postgres. */
@RunWith(AndroidJUnit4::class)
class DataraDatabaseTest {

    private val db = DataraDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val cycles = db.trackingCycleDao()

    @After
    fun close() = db.close()

    private fun cycle(id: String, device: String = "d1", status: String = CycleStatus.ACTIVE, start: Long = 1_000) =
        TrackingCycle(
            cycleId = id, userId = "u1", deviceId = device, name = null, quotaBytes = 1_000_000_000,
            startAt = start, reachedAt = if (status == CycleStatus.REACHED) start + 1 else null, endedAt = null,
            status = status, createdAt = start, updatedAt = start
        )

    private fun usage(start: Long, device: String = "d1") = DataUsage(
        dataUsageId = "$device-$start", userId = "u1", deviceId = device, intervalStartAt = start,
        intervalEndAt = start + 600_000, bytesRx = 1, bytesTx = 1, measurementStatus = "OK",
        networkType = "LTE", signalDbm = null, carrier = "GLOBE", schemaVersion = 3
    )

    private fun assertConstraintViolation(block: suspend () -> Unit) = runBlocking {
        try {
            block()
            fail("expected a constraint violation")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test
    fun oneActiveCyclePerDevice() = runBlocking {
        cycles.insert(cycle("c1"))
        assertConstraintViolation { cycles.insert(cycle("c2")) }
        // Another device, or a finished cycle, is fine
        cycles.insert(cycle("c3", device = "d2"))
        cycles.insert(cycle("c4", status = CycleStatus.ENDED))
        // Reactivating a finished cycle while one is active is rejected too
        assertConstraintViolation { cycles.update(cycle("c4")) }
        // Ending the active cycle, then starting a new one, works
        cycles.update(cycle("c1").copy(status = CycleStatus.ENDED, endedAt = 2_000, updatedAt = 2_000))
        cycles.insert(cycle("c5", start = 3_000))
        assertEquals("c5", cycles.active("d1")!!.cycleId)
    }

    @Test
    fun activeCycleFlowFollowsChanges() = runBlocking {
        assertNull(cycles.observeActive("d1").first())
        cycles.insert(cycle("c1"))
        assertEquals("c1", cycles.observeActive("d1").first()!!.cycleId)
        cycles.update(cycle("c1").copy(status = CycleStatus.REACHED, reachedAt = 5_000))
        assertNull(cycles.observeActive("d1").first())
        assertEquals(listOf("c1"), cycles.observeAll("u1").first().map { it.cycleId })
    }

    @Test
    fun cycleSyncedOnlyAtTheUploadedVersion() = runBlocking {
        cycles.insert(cycle("c1"))
        assertEquals(1, cycles.unsynced().size)
        // Edited after the upload started: the old version's ack must not clear the queue
        cycles.update(cycle("c1").copy(name = "Renamed", updatedAt = 1_500))
        cycles.markSynced("c1", updatedAt = 1_000, syncedAt = 9_000)
        assertEquals(1, cycles.unsynced().size)
        cycles.markSynced("c1", updatedAt = 1_500, syncedAt = 9_000)
        assertTrue(cycles.unsynced().isEmpty())
    }

    @Test
    fun dataUsageWindowQueriesAndQueue() = runBlocking {
        val dao = db.dataUsageDao()
        dao.upsertAll(listOf(usage(0), usage(600_000), usage(1_200_000), usage(0, device = "d2")))
        assertConstraintViolation { dao.upsertAll(listOf(usage(0).copy(dataUsageId = "other-id"))) }

        assertEquals(listOf(600_000L, 1_200_000L), dao.between("u1", 600_000, 1_800_000).map { it.intervalStartAt })
        assertEquals(1_200_000L, dao.latestIntervalStart("d1"))
        assertEquals(4, dao.unsynced(limit = 10).size)
        dao.markSynced(listOf("d1-0", "d1-600000"), syncedAt = 1)
        assertEquals(listOf("d1-1200000", "d2-0"), dao.unsynced(limit = 10).map { it.dataUsageId }.sorted())
    }

    @Test
    fun durableReadingsOverlappingAWindow() = runBlocking {
        val dao = db.telemetryDao()
        dao.insertReadings(listOf(
            DurableReadingEntity("r1", since = 0, until = 600_000, bytesRx = 1, bytesTx = 0, status = "OK", schemaVersion = 3),
            DurableReadingEntity("r2", since = 600_000, until = 1_200_000, bytesRx = 2, bytesTx = 0, status = "OK", schemaVersion = 3),
            DurableReadingEntity("r3", since = 1_200_000, until = 1_800_000, bytesRx = 3, bytesTx = 0, status = "OK", schemaVersion = 3)
        ))
        // A cycle starting mid-r1 sees r1 (to be split by time) and everything after
        assertEquals(listOf("r1", "r2"), dao.readingsOverlapping(300_000, 1_200_000).map { it.id })
        assertEquals(listOf("r2", "r3"), dao.observeReadingsSince(600_000).first().map { it.id })
    }

    @Test
    fun forecastAndNotificationAreLoggedOncePerKey() = runBlocking {
        val forecasts = db.forecastDao()
        val forecast = Forecast(
            forecastId = "f1", userId = "u1", deviceId = "d1", cycleId = "c1", predictedAt = 3_600_000,
            remainingBytes = 500, activeModel = ActiveModel.GLOBAL, globalNextHourBytes = 10, globalReachAt = null,
            localNextHourBytes = null, localReachAt = null, modelVersion = "global-v1", inputs = "{}"
        )
        assertTrue(forecasts.insert(forecast) > 0)
        assertEquals(-1L, forecasts.insert(forecast.copy(forecastId = "f2")))
        assertEquals("f1", forecasts.observeLatest("c1").first()!!.forecastId)

        val notifications = db.notificationDao()
        val alert = Notification(
            notificationId = "n1", userId = "u1", cycleId = "c1", notificationType = NotificationType.MILESTONE_75,
            title = "75%", message = "m", sentAt = 1, readAt = null
        )
        assertTrue(notifications.insert(alert) > 0)
        assertEquals(-1L, notifications.insert(alert.copy(notificationId = "n2")))
        assertEquals(1, notifications.observeUnreadCount("u1").first())

        notifications.markSynced("n1", readAt = null, syncedAt = 5)
        notifications.markRead("n1", readAt = 7)
        assertEquals(0, notifications.observeUnreadCount("u1").first())
        // Reading it queues the row for upload again
        assertEquals(7L, notifications.unsynced().single().readAt)
    }

    @Test
    fun preferencesRoundTrip() = runBlocking {
        val users = db.userDao()
        users.upsertPreferences(NotificationPreferences(userId = "u1", forecastAlertsEnabled = false, updatedAt = 1))
        val stored = users.observePreferences("u1").first()!!
        assertTrue(stored.milestonesEnabled)
        assertEquals(false, stored.forecastAlertsEnabled)
    }
}
