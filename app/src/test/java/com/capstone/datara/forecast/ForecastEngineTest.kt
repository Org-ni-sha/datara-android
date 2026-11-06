package com.capstone.datara.forecast

import com.capstone.datara.data.cycle.CycleOwner
import com.capstone.datara.data.cycle.CycleOwnerProvider
import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.data.cycle.TrackingCycle
import com.capstone.datara.data.local.entity.ActiveModel
import com.capstone.datara.data.local.entity.Forecast
import com.capstone.datara.data.local.entity.UserModelState
import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.data.telemetry.UsageSample
import com.capstone.datara.domain.cycle.CycleProgress
import com.capstone.datara.domain.cycle.CycleState
import com.capstone.datara.domain.cycle.CycleUsage
import com.capstone.datara.forecast.FeatureContract.HOUR_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** The hourly D6 job end to end against in-memory storage and a constant model. */
class ForecastEngineTest {

    private val start = 1_794_153_600_000L // 2026-11-09 00:00 Asia/Manila
    private val oneMb = 1_000_000L

    private class FakeStore : ForecastStore {
        val samples = mutableListOf<UsageSample>()
        val states = mutableMapOf<String, UserModelState>()
        val forecasts = mutableMapOf<Pair<String, Long>, Forecast>()
        var stateWrites = 0
        val readings = mutableListOf<DurableUsageReading>()
        override suspend fun samplesEndingIn(from: Long, until: Long) = samples.filter { it.intervalEnd in from until until }
        override suspend fun durableReadingsOverlapping(from: Long, until: Long) =
            readings.filter { it.until > from && it.since < until }
        override suspend fun modelState(userId: String) = states[userId]
        override suspend fun saveModelState(state: UserModelState) { stateWrites++; states[state.userId] = state }
        override suspend fun forecastAt(cycleId: String, predictedAt: Long) = forecasts[cycleId to predictedAt]
        override suspend fun insertForecast(forecast: Forecast): Boolean =
            forecasts.putIfAbsent(forecast.cycleId to forecast.predictedAt, forecast) == null
    }

    private class ConstantModel(val bytes: Double) : NextHourModel {
        var calls = 0
        override val version = "dev-test"
        override fun predictBytes(features: FloatArray): Double { calls++; return bytes }
    }

    private val store = FakeStore()
    private val cycleState = MutableStateFlow<CycleState>(CycleState.NoCycle)
    private var now = start
    private var model: NextHourModel? = ConstantModel(oneMb.toDouble())
    private val engine = ForecastEngine(
        store = store,
        owners = object : CycleOwnerProvider { override val owner = MutableStateFlow<CycleOwner?>(CycleOwner("u1", "d1")) },
        cycles = object : CycleStateSource { override val state = cycleState },
        models = NextHourModelProvider { model },
        clock = { now },
        zone = { ZoneId.of("Asia/Manila") },
        scope = CoroutineScope(Dispatchers.Unconfined),
        compute = Dispatchers.Unconfined
    )

    /** The hour as the phone records it: one NSM reading covering it and a 30-second sample. */
    private fun usage(hour: Long, bytes: Long) {
        store.readings += DurableUsageReading(since = hour, until = hour + HOUR_MS, bytesRx = bytes, bytesTx = 0,
            status = "OK", schemaVersion = 4)
        store.samples += UsageSample(intervalStart = hour + 1_000, intervalEnd = hour + 31_000, elapsedMs = 30_000,
            bytesRx = bytes, bytesTx = 0, status = "OK", networkType = "LTE", activeNetworkAtSample = "LTE",
            signalDbm = -90, carrier = "Globe", utcOffsetMinutes = 480, schemaVersion = 4)
    }

    private fun activeCycle(quota: Long, used: Long) {
        val cycle = TrackingCycle("c1", "u1", "d1", "Everyday", quota, start - 48 * HOUR_MS, null, null,
            CycleStatus.ACTIVE, start - 48 * HOUR_MS, start - 48 * HOUR_MS)
        cycleState.value = CycleState.Tracked(CycleProgress(cycle, CycleUsage(used, used, null, null, 0, 0)))
    }

    @Test
    fun `too little history is collecting, with no forecast logged`() = runBlocking {
        activeCycle(quota = 100 * oneMb, used = 0)
        (1..5).forEach { usage(start - it * HOUR_MS, oneMb) }
        val status = engine.run()
        assertEquals(ForecastStatus.Collecting(5, FeatureContract.MIN_OBSERVED_LAST_24H), status)
        assertTrue(store.forecasts.isEmpty())
    }

    @Test
    fun `logs one forecast per cycle per hour with the global PET`() = runBlocking {
        activeCycle(quota = 100 * oneMb, used = 90 * oneMb)
        (1..24).forEach { usage(start - it * HOUR_MS, oneMb) }
        now = start + 5 * 60_000 // run a few minutes into the hour

        val status = engine.run() as ForecastStatus.Ready
        assertEquals(start, status.predictedAt)
        assertEquals((start + 10 * HOUR_MS).toDouble(), status.reachAt!!.toDouble(), 1_000.0) // 10 MB left at 1 MB/h
        assertEquals(ActiveModel.GLOBAL, status.activeModel)
        assertTrue(status.isPlaceholderModel)

        val row = store.forecasts.getValue("c1" to start)
        assertEquals(ForecastEngine.forecastId("c1", start), row.forecastId)
        assertEquals(10 * oneMb, row.remainingBytes)
        assertEquals(oneMb.toDouble(), row.globalNextHourBytes.toDouble(), 2.0)
        assertNull(row.localNextHourBytes) // the local layer has not learned yet
        assertNull(row.localReachAt)
        assertEquals("dev-test", row.modelVersion)
        val inputs = Json.parseToJsonElement(row.inputs).jsonObject
        assertTrue(FeatureContract.FEATURE_NAMES.all { it in inputs })
        assertEquals("480", inputs["utc_offset_minutes"].toString())

        // Calls later in the same hour do nothing, and a forced re-run does not duplicate the row
        val calls = (model as ConstantModel).calls
        engine.runIfDue()
        assertEquals(calls, (model as ConstantModel).calls)
        engine.run()
        assertEquals(1, store.forecasts.size)
    }

    @Test
    fun `beyond 30 days has no reach time`() = runBlocking {
        model = ConstantModel(0.0)
        activeCycle(quota = 100 * oneMb, used = 0)
        (1..24).forEach { usage(start - it * HOUR_MS, 0) }
        val status = engine.run() as ForecastStatus.Ready
        assertNull(status.reachAt)
        assertNull(store.forecasts.getValue("c1" to start).globalReachAt)
    }

    @Test
    fun `learns from the previous hour once it is measured, even without a cycle`() = runBlocking {
        (1..24).forEach { usage(start - it * HOUR_MS, oneMb) }
        assertEquals(ForecastStatus.NoActiveCycle, engine.run())
        val pending = LocalLayerParameters.fromJson(store.states.getValue("u1").parameters)
        assertEquals(start, pending.pendingHour)

        usage(start, 3 * oneMb) // what the predicted hour actually used
        now = start + HOUR_MS + 60_000
        engine.run()
        val state = store.states.getValue("u1")
        val params = LocalLayerParameters.fromJson(state.parameters)
        assertEquals(1, state.hoursSeen)
        assertEquals(2.0 * oneMb, state.globalRecentMae!!, 2.0)
        assertEquals(3.0, params.overallActual / params.overallPredicted, 1e-6)
        assertEquals(start + HOUR_MS, params.pendingHour)
        assertEquals(ActiveModel.GLOBAL, state.activeModel)
    }

    @Test
    fun `local layer predicts in shadow after a day and takes over after a week when better`() = runBlocking {
        activeCycle(quota = 10_000 * oneMb, used = 0)
        // Global always says 1 MB, the user really uses 2 MB every hour
        (1..24).forEach { usage(start - it * HOUR_MS, 2 * oneMb) }
        var lastRow: Forecast? = null
        for (k in 0 until ModelSwitch.MIN_HOURS_FOR_LOCAL + 2) {
            val hour = start + k * HOUR_MS
            now = hour + 60_000
            engine.run()
            lastRow = store.forecasts.getValue("c1" to hour)
            usage(hour, 2 * oneMb)
            if (k == LocalLayerParameters.MIN_HOURS_TO_PREDICT + 1) {
                assertNotNull("local predicts in shadow", lastRow.localNextHourBytes)
                assertEquals(ActiveModel.GLOBAL, lastRow.activeModel)
            }
        }
        val state = store.states.getValue("u1")
        assertTrue(state.localRecentMae!! < state.globalRecentMae!!)
        assertEquals(ActiveModel.LOCAL, state.activeModel)
        assertEquals(ActiveModel.LOCAL, lastRow!!.activeModel)
        assertEquals(2.0 * oneMb, lastRow.localNextHourBytes!!.toDouble(), 0.05 * oneMb)
    }

    @Test
    fun `waits for the read that completes the last hour`() = runBlocking {
        activeCycle(quota = 100 * oneMb, used = 0)
        (2..24).forEach { usage(start - it * HOUR_MS, oneMb) }
        store.readings += DurableUsageReading(since = start - HOUR_MS, until = start - 5 * 60_000, bytesRx = oneMb,
            bytesTx = 0, status = "OK", schemaVersion = 4)
        now = start + 60_000
        assertEquals(ForecastStatus.Pending, engine.run())
        assertTrue(store.forecasts.isEmpty())

        store.readings += DurableUsageReading(since = start - 5 * 60_000, until = start + 5 * 60_000, bytesRx = 0,
            bytesTx = 0, status = "OK", schemaVersion = 4)
        assertTrue(engine.run() is ForecastStatus.Ready)
    }

    @Test
    fun `missing model is unavailable, not a made-up forecast`() = runBlocking {
        model = null
        activeCycle(quota = 100 * oneMb, used = 0)
        (1..24).forEach { usage(start - it * HOUR_MS, oneMb) }
        assertTrue(engine.run() is ForecastStatus.Unavailable)
        assertTrue(store.forecasts.isEmpty())
    }

    @Test
    fun `model state is written only when it changes`() = runBlocking {
        (1..24).forEach { usage(start - it * HOUR_MS, oneMb) }
        engine.run()
        val writes = store.stateWrites
        engine.run()
        assertEquals(writes, store.stateWrites)
    }
}
