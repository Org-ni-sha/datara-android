package com.capstone.datara.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.capstone.datara.data.local.device.DeviceIdStore
import com.capstone.datara.data.local.device.DeviceRepository
import com.capstone.datara.data.local.device.HardwareInfo
import com.capstone.datara.data.local.entity.UsageSampleEntity
import com.capstone.datara.data.telemetry.DataUsageRollup.WINDOW_MS
import com.capstone.datara.data.telemetry.UsageRollup
import com.capstone.datara.data.telemetry.UsageSample
import com.capstone.datara.telemetry.TelemetryClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Device registration and the 10-minute rollup against Room. */
@RunWith(AndroidJUnit4::class)
class UsageRollupTest {

    private val db = DataraDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)

    private class MapIdStore : DeviceIdStore {
        val ids = mutableMapOf<String, String>()
        override fun get(userId: String) = ids[userId]
        override fun put(userId: String, deviceId: String) {
            ids[userId] = deviceId
        }
    }

    private class FakeHardware(override var model: String = "M2012K11AG", override var sdkInt: Int = 34) : HardwareInfo

    private class FakeClock(var now: Long) : TelemetryClock {
        override fun elapsedRealtime() = now
        override fun currentTimeMillis() = now
        override fun utcOffsetMinutes(wall: Long) = 480
    }

    /** 2026-11-04 08:00 UTC, a window boundary. */
    private val t0 = 1_793_779_200_000L
    private val ids = MapIdStore()
    private val hardware = FakeHardware()
    private val devices = DeviceRepository(db.deviceDao(), ids, hardware)
    private val clock = FakeClock(t0)
    private var user: String? = "u1"
    private val rollup = UsageRollup(db.telemetryDao(), db.dataUsageDao(), devices, { user }, clock)

    @After
    fun close() = db.close()

    private suspend fun sampleEnding(end: Long, rx: Long) = db.telemetryDao().insertSample(
        UsageSampleEntity.from(
            UsageSample(
                intervalStart = end - 30_000, intervalEnd = end, elapsedMs = 30_000, bytesRx = rx, bytesTx = 0,
                status = "OK", networkType = "LTE", activeNetworkAtSample = "LTE", signalDbm = -90,
                carrier = "GLOBE", utcOffsetMinutes = 480, schemaVersion = 3
            )
        )
    )

    @Test
    fun deviceIsRegisteredOncePerInstallAndAccount() = runBlocking {
        val first = devices.ensureRegistered("u1", now = 1)
        assertEquals(first, devices.ensureRegistered("u1", now = 2))
        assertEquals("M2012K11AG", first.deviceModel)
        assertEquals(34, first.androidSdkInt)
        assertNull(first.deviceName)

        // A second account on the same phone needs its own device_id (the Postgres primary key)
        assertNotEquals(first.deviceId, devices.ensureRegistered("u2").deviceId)

        // An OS update refreshes the row and queues it for upload
        db.deviceDao().markSynced(first.deviceId, syncedAt = 5)
        hardware.sdkInt = 35
        val updated = devices.ensureRegistered("u1")
        assertEquals(first.deviceId, updated.deviceId)
        assertEquals(35, db.deviceDao().get(first.deviceId)!!.androidSdkInt)
        assertNull(db.deviceDao().get(first.deviceId)!!.syncedAt)
    }

    @Test
    fun closedWindowsAreRolledUpOnce() = runBlocking {
        sampleEnding(t0 + 30_000, rx = 100)
        sampleEnding(t0 + 60_000, rx = 50)
        sampleEnding(t0 + WINDOW_MS + 30_000, rx = 7)

        // The first window is still open
        clock.now = t0 + 90_000
        assertEquals(0, rollup.run())

        clock.now = t0 + WINDOW_MS + 40_000
        assertEquals(1, rollup.run())
        val deviceId = devices.deviceId("u1")!!
        val row = db.dataUsageDao().between("u1", t0, t0 + WINDOW_MS).single()
        assertEquals(150L, row.bytesTotal)
        assertEquals(deviceId, row.deviceId)
        assertEquals(-90, row.signalDbm)

        // Nothing new is closed, so nothing is written twice
        assertEquals(0, rollup.run())

        clock.now = t0 + 2 * WINDOW_MS
        assertEquals(1, rollup.run())
        assertEquals(7L, db.dataUsageDao().latestIntervalStart(deviceId)!!.let {
            db.dataUsageDao().between("u1", it, it + WINDOW_MS).single().bytesTotal
        })
    }

    @Test
    fun signedOutRollsUpNothingAndKeepsSamples() = runBlocking {
        sampleEnding(t0 + 30_000, rx = 100)
        user = null
        clock.now = t0 + 30 * 24 * 3_600_000L
        assertEquals(0, rollup.run())
        assertEquals(t0 + 30_000, db.telemetryDao().earliestSampleEnd())
    }

    @Test
    fun oldSamplesArePrunedAfterRollup() = runBlocking {
        // Registered before the samples, as when D3 or the first rollup runs at sign-in
        devices.ensureRegistered("u1", now = t0)
        sampleEnding(t0 + 30_000, rx = 100)
        clock.now = t0 + UsageRollup.SAMPLE_RETENTION_MS + 2 * WINDOW_MS
        assertEquals(1, rollup.run())
        assertNull(db.telemetryDao().earliestSampleEnd())
    }
}
