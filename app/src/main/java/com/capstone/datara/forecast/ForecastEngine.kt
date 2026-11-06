package com.capstone.datara.forecast

import android.util.Log
import com.capstone.datara.data.cycle.CycleOwnerProvider
import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.data.local.entity.ActiveModel
import com.capstone.datara.data.local.entity.Forecast
import com.capstone.datara.data.local.entity.UserModelState
import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.data.telemetry.UsageSample
import com.capstone.datara.di.ApplicationScope
import com.capstone.datara.domain.cycle.CycleClock
import com.capstone.datara.domain.cycle.CycleState
import com.capstone.datara.forecast.FeatureContract.HOUR_MS
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What Home and Quota Details show about the PET. */
sealed interface ForecastStatus {
    /** Not computed yet in this process. */
    data object Pending : ForecastStatus

    /** Signed out, or no active cycle on this device (none, reached or ended). */
    data object NoActiveCycle : ForecastStatus

    /** Too little usage history for an honest forecast. */
    data class Collecting(val observedHours: Int, val neededHours: Int) : ForecastStatus

    /** The model could not run (missing or incompatible model file). */
    data class Unavailable(val reason: String) : ForecastStatus

    /**
     * The forecast for [cycleId] made at [predictedAt]. [reachAt] is the PET of the model the user
     * is shown ([activeModel]); null means beyond 30 days.
     */
    data class Ready(
        val cycleId: String,
        val predictedAt: Long,
        val reachAt: Long?,
        val nextHourBytes: Long,
        val remainingBytes: Long,
        val activeModel: String,
        val modelVersion: String
    ) : ForecastStatus {
        /** The synthetic placeholder model from datara-ml, not a trained one. */
        val isPlaceholderModel: Boolean get() = modelVersion.startsWith(OnnxNextHourModel.PLACEHOLDER_PREFIX)
    }
}

/** Storage the engine reads and writes (Room in the app, in memory in JVM tests). */
interface ForecastStore {
    /** 30-second samples whose interval ended in [from, until). */
    suspend fun samplesEndingIn(from: Long, until: Long): List<UsageSample>

    /** 10-minute NetworkStatsManager readings overlapping [from, until). */
    suspend fun durableReadingsOverlapping(from: Long, until: Long): List<DurableUsageReading>
    suspend fun modelState(userId: String): UserModelState?
    suspend fun saveModelState(state: UserModelState)
    suspend fun forecastAt(cycleId: String, predictedAt: Long): Forecast?

    /** False when that (cycle, hour) is already logged. */
    suspend fun insertForecast(forecast: Forecast): Boolean
}

/** The latest cycle and its measured usage (D3's [com.capstone.datara.domain.cycle.QuotaCycleManager]). */
interface CycleStateSource {
    val state: StateFlow<CycleState>
}

/** Loads the Global model once; null when it cannot be loaded. */
fun interface NextHourModelProvider {
    suspend fun model(): NextHourModel?
}

/** The phone's time zone for the time features. */
fun interface ForecastZone {
    fun zone(): ZoneId
}

/**
 * D6: once per hour, learns the local layer from the last hour's error, predicts the next hour
 * with the Global model (and the local correction), projects the active cycle's PET with both,
 * logs one `forecasts` row per active cycle per hour, and publishes [status].
 *
 * Cadence: [runIfDue] is called after each 10-minute durable read of the telemetry service, and
 * does work only once per hour (or when the active cycle changes), so no extra alarm or worker
 * wakes the phone. Inference runs on [compute] (Dispatchers.Default), never the main thread, and
 * never over the network.
 */
@Singleton
class ForecastEngine(
    private val store: ForecastStore,
    private val owners: CycleOwnerProvider,
    private val cycles: CycleStateSource,
    private val models: NextHourModelProvider,
    private val clock: CycleClock,
    private val zone: ForecastZone,
    private val scope: CoroutineScope,
    private val compute: CoroutineDispatcher
) {
    @Inject
    constructor(
        store: ForecastStore,
        owners: CycleOwnerProvider,
        cycles: CycleStateSource,
        models: NextHourModelProvider,
        clock: CycleClock,
        zone: ForecastZone,
        @ApplicationScope scope: CoroutineScope
    ) : this(store, owners, cycles, models, clock, zone, scope, Dispatchers.Default)

    private val lock = Mutex()
    private val mutableStatus = MutableStateFlow<ForecastStatus>(ForecastStatus.Pending)
    val status: StateFlow<ForecastStatus> = mutableStatus.asStateFlow()

    /** (hour, active cycle id) of the last completed run, so repeated calls in an hour are free. */
    @Volatile private var lastRun: Pair<Long, String?>? = null

    /**
     * Starts following the signed-in user and the active cycle: signing in, or a new or ended
     * cycle, gets its forecast at once instead of at the next telemetry read.
     */
    fun start() {
        scope.launch {
            combine(owners.owner, cycles.state) { owner, state -> Triple(owner, activeCycleId(state), state !is CycleState.Loading) }
                .distinctUntilChanged()
                .collect { (_, _, loaded) -> if (loaded) runCatching { run() }.onFailure { Log.e(TAG, "Forecast failed", it) } }
        }
    }

    /** Runs when the hour or the active cycle changed since the last run. */
    suspend fun runIfDue(): ForecastStatus {
        val hour = FeatureContract.hourStart(clock.now())
        if (lastRun == hour to activeCycleId(cycles.state.value)) return status.value
        return run()
    }

    suspend fun run(): ForecastStatus = lock.withLock {
        withContext(compute) { compute() }.also { mutableStatus.value = it }
    }

    private suspend fun compute(): ForecastStatus {
        val now = clock.now()
        val hour = FeatureContract.hourStart(now)
        val cycleState = cycles.state.value
        val owner = owners.owner.first() ?: return ForecastStatus.NoActiveCycle.also { lastRun = hour to null }
        val zoneId = zone.zone()

        val from = hour - (FeatureContract.LOOKBACK_HOURS + 1) * HOUR_MS
        val readings = store.durableReadingsOverlapping(from, hour)
        // The last hour isn't fully read yet (a run between the hour and the next 10-minute
        // read): wait for that read instead of treating the hour as unmeasured
        val readUntil = readings.maxOfOrNull { it.until }
        if (readUntil != null && readUntil < hour && hour - readUntil <= READ_WAIT_MS) return status.value
        val history = HourlyUsage.aggregate(readings, store.samplesEndingIn(from, hour))

        // 1. Learn from the hour predicted last time, if it is over
        val stored = store.modelState(owner.userId)
        var params = LocalLayerParameters.fromJson(stored?.parameters)
        var scores = LayerScores(stored?.hoursSeen ?: 0, stored?.globalRecentMae, stored?.localRecentMae)
        val pending = params.pendingHour
        if (pending != null && pending < hour) {
            val (p, s) = LocalLayer.learn(params, scores, history[pending]?.mobileBytes)
            params = p
            scores = s
        }

        // 2. Predict the current hour, when there is enough history
        val active = (cycleState as? CycleState.Tracked)?.progress?.takeIf {
            it.cycle.status == CycleStatus.ACTIVE && it.cycle.endedAt == null && it.cycle.deviceId == owner.deviceId
        }
        val observed = FeatureExtractor.observedLast24h(history, hour)
        val model = if (observed >= FeatureContract.MIN_OBSERVED_LAST_24H) models.model() else null
        var activeModel = stored?.activeModel ?: ActiveModel.GLOBAL
        val result: ForecastStatus
        if (observed < FeatureContract.MIN_OBSERVED_LAST_24H || model == null) {
            result = when {
                active == null -> ForecastStatus.NoActiveCycle
                model == null && observed >= FeatureContract.MIN_OBSERVED_LAST_24H ->
                    ForecastStatus.Unavailable("The forecast model could not be loaded.")
                else -> ForecastStatus.Collecting(observed, FeatureContract.MIN_OBSERVED_LAST_24H)
            }
        } else {
            val x = FeatureExtractor.featureVector(history, hour, zoneId)
            val globalLog = model.predictLog(x).toDouble()
            val localReady = params.isReady
            val localLog = if (localReady) globalLog + params.correction(localHour(hour, zoneId)) else null
            if (params.pendingHour != hour) {
                params = params.copy(pendingHour = hour, pendingLocalHour = localHour(hour, zoneId),
                    pendingGlobalLog = globalLog, pendingLocalLog = localLog)
            }
            activeModel = ModelSwitch.decide(activeModel, scores, localReady)
            result = if (active == null) ForecastStatus.NoActiveCycle
            else forecastCycle(active.cycle.cycleId, owner.userId, owner.deviceId, hour, active.remainingBytes,
                history, x, model, params, activeModel, zoneId)
        }

        // 3. Keep the local layer (only when something changed, so the sync queue stays small)
        val updated = UserModelState(
            userId = owner.userId,
            activeModel = activeModel,
            parameters = params.toJson(),
            deviceId = owner.deviceId,
            hoursSeen = scores.hoursSeen,
            globalRecentMae = scores.globalRecentMae,
            localRecentMae = scores.localRecentMae,
            updatedAt = now
        )
        if (stored == null || stored.copy(updatedAt = now, syncedAt = null, deviceId = owner.deviceId) != updated) {
            store.saveModelState(updated)
        }
        lastRun = hour to active?.cycle?.cycleId
        return result
    }

    private suspend fun forecastCycle(
        cycleId: String, userId: String, deviceId: String, hour: Long, remainingBytes: Long,
        history: UsageHistory, features: FloatArray, model: NextHourModel, params: LocalLayerParameters,
        activeModel: String, zoneId: ZoneId
    ): ForecastStatus {
        val row = store.forecastAt(cycleId, hour) ?: run {
            val remaining = remainingBytes.toDouble()
            val global = PetProjector.project(history, hour, remaining, zoneId) { x, _ -> model.predictLog(x).toDouble() }
            val local = if (params.isReady) PetProjector.project(history, hour, remaining, zoneId) { x, t ->
                model.predictLog(x).toDouble() + params.correction(localHour(t, zoneId))
            } else null
            val forecast = Forecast(
                forecastId = forecastId(cycleId, hour),
                userId = userId,
                deviceId = deviceId,
                cycleId = cycleId,
                predictedAt = hour,
                remainingBytes = remainingBytes,
                activeModel = if (local == null) ActiveModel.GLOBAL else activeModel,
                globalNextHourBytes = global.nextHourBytes.toLong(),
                globalReachAt = global.reachAt,
                localNextHourBytes = local?.nextHourBytes?.toLong(),
                localReachAt = local?.reachAt,
                modelVersion = model.version,
                inputs = inputsJson(features, zoneId, hour)
            )
            store.insertForecast(forecast)
            store.forecastAt(cycleId, hour) ?: forecast
        }
        return ready(row)
    }

    private fun activeCycleId(state: CycleState): String? =
        (state as? CycleState.Tracked)?.progress?.cycle?.takeIf { it.status == CycleStatus.ACTIVE && it.endedAt == null }?.cycleId

    companion object {
        private const val TAG = "ForecastEngine"

        /** How long after the hour a run waits for the durable read that completes it. */
        const val READ_WAIT_MS = 20 * 60_000L

        /** The same id for the same cycle and hour, so sync retries upsert. */
        fun forecastId(cycleId: String, hour: Long): String =
            UUID.nameUUIDFromBytes("forecast:$cycleId:$hour".toByteArray()).toString()

        fun localHour(hour: Long, zone: ZoneId): Int = Instant.ofEpochMilli(hour).atZone(zone).hour

        fun ready(row: Forecast): ForecastStatus.Ready {
            val local = row.activeModel == ActiveModel.LOCAL && row.localNextHourBytes != null
            return ForecastStatus.Ready(
                cycleId = row.cycleId,
                predictedAt = row.predictedAt,
                reachAt = if (local) row.localReachAt else row.globalReachAt,
                nextHourBytes = if (local) row.localNextHourBytes!! else row.globalNextHourBytes,
                remainingBytes = row.remainingBytes,
                activeModel = if (local) ActiveModel.LOCAL else ActiveModel.GLOBAL,
                modelVersion = row.modelVersion
            )
        }

        /** `forecasts.inputs`: the feature values by name (null for missing) and the zone offset used. */
        fun inputsJson(features: FloatArray, zone: ZoneId, hour: Long): String {
            val values = FeatureContract.FEATURE_NAMES.mapIndexed { i, name ->
                name to (features[i].takeUnless { it.isNaN() }?.let { JsonPrimitive(it) } ?: JsonNull)
            }.toMap()
            return JsonObject(values + mapOf(
                "contract_version" to JsonPrimitive(FeatureContract.CONTRACT_VERSION),
                "utc_offset_minutes" to JsonPrimitive(zone.rules.getOffset(Instant.ofEpochMilli(hour)).totalSeconds / 60)
            )).toString()
        }
    }
}
