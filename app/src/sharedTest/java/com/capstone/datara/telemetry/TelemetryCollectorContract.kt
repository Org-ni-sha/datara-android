package com.capstone.datara.telemetry

import com.capstone.datara.data.telemetry.TelemetryRepository
import com.capstone.datara.telemetry.DurableTick.DAY_MS
import com.capstone.datara.telemetry.PrimaryTick.Counters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The collector against a [TelemetryRepository]: the contract every repository must satisfy. Run
 * on the JVM against the in-memory repository (TelemetryCollectorTest) and on a device against
 * Room (RoomTelemetryCollectorTest). Lives in src/sharedTest, which both test source sets include;
 * test names are camelCase because names with spaces cannot be dexed at minSdk 26.
 */
abstract class TelemetryCollectorContract {

    /** A new, empty repository for each test. */
    protected abstract fun createRepository(): TelemetryRepository

    private val boot = 1_793_000_000_000L

    private class FakeClock(var elapsed: Long, val boot: Long) : TelemetryClock {
        override fun elapsedRealtime() = elapsed
        override fun currentTimeMillis() = boot + elapsed
        override fun utcOffsetMinutes(wall: Long) = 480
    }

    private class FakeRadio(var active: String = "LTE", var mobileUp: Boolean = true) : RadioInfoSource {
        override fun activeNetworkLabel() = active
        override fun cellularRadioLabel() = "LTE"
        override fun signalDbm() = -95
        override fun carrierName() = "Smart"
        override fun isMobileDataUp() = mobileUp
    }

    private class FakeDurable(var access: Boolean = true, var totals: Counters? = Counters(0, 0)) : DurableUsageSource {
        override fun hasAccess() = access
        override fun queryMobileCumulative(anchor: Long, until: Long) = totals
    }

    protected val repository = createRepository()
    private val clock = FakeClock(100_000, boot)
    private var counters = Counters(1_000, 100)
    private val radio = FakeRadio()
    private val durable = FakeDurable()
    private val collector = TelemetryCollector(repository, { counters }, durable, radio, clock)

    @Test
    fun samplesCommitWithTheirCheckpointAndCarryNetworkInputs() = runBlocking {
        val first = collector.collectPrimary(switchedFrom = null, cellularSeen = true)
        assertEquals("BASELINE", first.status)

        clock.elapsed += 30_000
        counters = Counters(1_600, 150)
        val second = collector.collectPrimary(switchedFrom = null, cellularSeen = true)
        assertEquals("OK", second.status)
        assertEquals(650L, second.bytesTotal)
        assertEquals("LTE", second.networkType)
        assertEquals(-95, second.signalDbm)
        assertEquals("Smart", second.carrier)
        assertEquals(480, second.utcOffsetMinutes)
        assertEquals(MeasurementRules.SCHEMA_VERSION, second.schemaVersion)
        assertEquals(650 * 1_000.0 / 30_000, second.bytesPerSecond!!, 1e-9)

        assertEquals(listOf(first, second), repository.observeSamples().first())
        assertEquals(1_600L, repository.primaryCheckpoint()!!.mobileRx)
    }

    @Test
    fun intervalClosedByASwitchFromWifiKeepsTheWifiLabelWhenNoMobileBytesMoved() = runBlocking {
        radio.active = "WIFI"
        collector.collectPrimary(switchedFrom = null, cellularSeen = false)
        clock.elapsed += 10_000
        val closed = collector.collectPrimary(switchedFrom = "WIFI", cellularSeen = false)
        assertEquals("WIFI", closed.networkType)
        assertEquals("WIFI", closed.activeNetworkAtSample)
    }

    @Test
    fun mobileBytesUnderWifiAreLabelledMobile() = runBlocking {
        radio.active = "WIFI"
        collector.collectPrimary(switchedFrom = null, cellularSeen = false)
        clock.elapsed += 30_000
        counters = Counters(1_001, 100)
        assertEquals("LTE", collector.collectPrimary(switchedFrom = null, cellularSeen = false).networkType)
    }

    @Test
    fun mobileDataReturningAfterABaselineReadOnWifiIsNotCountedAsUsage() = runBlocking {
        // The phone test: baseline on Wi-Fi with mobile data torn down, then LTE comes back
        radio.active = "WIFI"
        radio.mobileUp = false
        counters = Counters(10_000, 1_000)
        collector.collectPrimary(switchedFrom = null, cellularSeen = false)

        clock.elapsed += 29_000
        radio.active = "LTE"
        radio.mobileUp = true
        counters = Counters(1_204_000_000, 800_000)
        val returned = collector.collectPrimary(switchedFrom = null, cellularSeen = true)
        assertEquals("COUNTER_RESET", returned.status)
        assertEquals(0L, returned.bytesTotal)

        clock.elapsed += 30_000
        counters = Counters(1_204_090_000, 807_114)
        assertEquals(97_114L, collector.collectPrimary(switchedFrom = null, cellularSeen = true).bytesTotal)
    }

    @Test
    fun durableReadWithoutUsageAccessIsSkippedAndStoresNothing() = runBlocking {
        durable.access = false
        assertNull(collector.collectDurable())
        assertNull(repository.durableCheckpoint())
    }

    @Test
    fun durableReadsStoreTheDifferenceFromThePreviousRead() = runBlocking {
        durable.totals = Counters(5_000, 500)
        assertTrue(collector.collectDurable()!!.isEmpty())

        clock.elapsed += MeasurementRules.APP_COLLECTION_INTERVAL_MS
        durable.totals = Counters(7_000, 600)
        val reading = collector.collectDurable()!!.single()
        assertEquals(2_100L, reading.bytesTotal)
        assertEquals("OK", reading.status)
        assertEquals(listOf(reading), repository.observeDurableReadings().first())
        assertEquals(DurableTick.utcMidnight(boot + clock.elapsed), repository.durableCheckpoint()!!.anchor)
        assertTrue(repository.durableCheckpoint()!!.anchor % DAY_MS == 0L)
    }

    @Test
    fun failedDurableQueryKeepsTheCheckpoint() = runBlocking {
        durable.totals = Counters(5_000, 500)
        collector.collectDurable()
        val kept = repository.durableCheckpoint()
        clock.elapsed += MeasurementRules.APP_COLLECTION_INTERVAL_MS
        durable.totals = null
        assertNull(collector.collectDurable())
        assertEquals(kept, repository.durableCheckpoint())
    }
}
