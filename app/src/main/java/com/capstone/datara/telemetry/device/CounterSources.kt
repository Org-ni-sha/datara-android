package com.capstone.datara.telemetry.device

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.SystemClock
import android.util.Log
import com.capstone.datara.telemetry.DurableUsageSource
import com.capstone.datara.telemetry.MobileCounterSource
import com.capstone.datara.telemetry.PrimaryTick
import com.capstone.datara.telemetry.TelemetryAccess
import com.capstone.datara.telemetry.TelemetryClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.TimeZone
import javax.inject.Inject

/** Device-wide cumulative mobile counters from TrafficStats. Needs no permission. */
class TrafficStatsCounterSource @Inject constructor() : MobileCounterSource {
    override fun read(): PrimaryTick.Counters {
        val rx = TrafficStats.getMobileRxBytes()
        val tx = TrafficStats.getMobileTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            Log.w(TAG, "TrafficStats mobile counters are not supported on this device")
            return PrimaryTick.Counters(-1L, -1L)
        }
        return PrimaryTick.Counters(rx, tx)
    }

    private companion object {
        const val TAG = "TrafficStatsSource"
    }
}

/**
 * Device-wide mobile totals from NetworkStatsManager. Needs Usage Access (PACKAGE_USAGE_STATS),
 * which the user grants in Android Settings; see [TelemetryAccess].
 */
class NetworkStatsUsageSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val access: TelemetryAccess
) : DurableUsageSource {

    override fun hasAccess(): Boolean = access.hasUsageAccess()

    @Suppress("DEPRECATION") // querySummaryForDevice still takes the legacy ConnectivityManager types
    override fun queryMobileCumulative(anchor: Long, until: Long): PrimaryTick.Counters? {
        val manager = context.getSystemService(NetworkStatsManager::class.java) ?: return null
        return try {
            // A null subscriber id covers every mobile subscription
            val bucket = manager.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, anchor, until)
            PrimaryTick.Counters(bucket.rxBytes, bucket.txBytes)
        } catch (e: SecurityException) {
            Log.w(TAG, "Usage Access revoked; durable read skipped", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "NetworkStatsManager query failed", e)
            null
        }
    }

    private companion object {
        const val TAG = "NetworkStatsSource"
    }
}

class SystemTelemetryClock @Inject constructor() : TelemetryClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun utcOffsetMinutes(wall: Long): Int = TimeZone.getDefault().getOffset(wall) / 60_000
}
