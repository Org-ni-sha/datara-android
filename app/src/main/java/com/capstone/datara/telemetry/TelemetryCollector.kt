package com.capstone.datara.telemetry

import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.data.telemetry.TelemetryRepository
import com.capstone.datara.data.telemetry.UsageSample
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One tick of each loop, from checkpoint to committed sample, outside the Service so it can be
 * tested with fakes (data-harvester's `PrimaryCollector`).
 *
 * Each loop holds its own lock from loading the checkpoint to committing the new one, and reads
 * the counters and clocks inside it: overlapping triggers (timer + network switch) run one after
 * the other and never count the same bytes twice.
 */
@Singleton
class TelemetryCollector @Inject constructor(
    private val repository: TelemetryRepository,
    private val counters: MobileCounterSource,
    private val durable: DurableUsageSource,
    private val radio: RadioInfoSource,
    private val clock: TelemetryClock
) {
    private val primaryLock = Mutex()
    private val durableLock = Mutex()

    /**
     * Closes the open 30-second interval.
     * @param switchedFrom the network the interval ran on when a switch closes it, else null
     * @param cellularSeen whether a mobile network was the default during the interval
     */
    suspend fun collectPrimary(switchedFrom: String?, cellularSeen: Boolean): UsageSample {
        // Device state first; none of it depends on the checkpoint
        val activeNetwork = radio.activeNetworkLabel()
        val signal = radio.signalDbm()
        val carrier = radio.carrierName()
        val intervalNetwork = NetworkSwitchTracker.intervalNetwork(switchedFrom, activeNetwork) { radio.cellularRadioLabel() }

        return primaryLock.withLock {
            val checkpoint = repository.primaryCheckpoint() ?: TelemetryCheckpoint()
            val nowElapsed = clock.elapsedRealtime()
            val nowWall = clock.currentTimeMillis()
            // Interface state just before the counters, so a counter that came back with it is seen
            val mobileUp = radio.isMobileDataUp()
            val tick = PrimaryTick.compute(checkpoint, counters.read(), nowElapsed, nowWall, mobileUp)
            val sample = UsageSample(
                intervalStart = tick.intervalStart,
                intervalEnd = nowWall,
                elapsedMs = tick.elapsedMs,
                bytesRx = tick.bytesRx,
                bytesTx = tick.bytesTx,
                status = tick.status,
                // Cleaning drops WIFI rows, so WIFI must hold no mobile bytes
                networkType = MeasurementRules.primaryNetworkLabel(intervalNetwork, cellularSeen, tick.bytesTotal) {
                    radio.cellularRadioLabel()
                },
                activeNetworkAtSample = activeNetwork,
                signalDbm = signal,
                carrier = carrier,
                utcOffsetMinutes = clock.utcOffsetMinutes(nowWall),
                schemaVersion = MeasurementRules.SCHEMA_VERSION
            )
            repository.commitPrimary(sample, tick.checkpoint)
            sample
        }
    }

    /**
     * Reads durable mobile totals and stores the usage since the previous read.
     * @return the stored readings (empty on the first read), or null when Usage Access is missing
     *   or a query failed; the previous checkpoint is then kept for a retry
     */
    suspend fun collectDurable(): List<DurableUsageReading>? = durableLock.withLock {
        if (!durable.hasAccess()) return@withLock null
        val result = DurableTick.compute(repository.durableCheckpoint(), clock.currentTimeMillis()) { anchor, until ->
            durable.queryMobileCumulative(anchor, until)
        } ?: return@withLock null
        val readings = result.spans.map {
            DurableUsageReading(
                since = it.since,
                until = it.until,
                bytesRx = it.bytesRx,
                bytesTx = it.bytesTx,
                status = it.status,
                schemaVersion = MeasurementRules.SCHEMA_VERSION
            )
        }
        repository.commitDurable(readings, result.checkpoint)
        readings
    }
}
