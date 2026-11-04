package com.capstone.datara.telemetry

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import com.capstone.datara.data.telemetry.UsageRollup
import com.capstone.datara.telemetry.device.SignalStrengthMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Foreground service that measures mobile usage on data-harvester's schedule (ROADMAP
 * Decision 1):
 *
 * 1. Every 30 s, and on every Wi-Fi ↔ mobile switch: a TrafficStats sample with the network
 *    type, signal strength and carrier ([TelemetryCollector.collectPrimary]).
 * 2. Every 10 min, and on a switch: durable mobile totals from NetworkStatsManager
 *    ([TelemetryCollector.collectDurable]).
 * 3. The moment a cellular data network comes up: a 30-second-loop tick, so the counter jump of
 *    a returning mobile interface is re-baselined at once (DATAra deviation, see PrimaryTick).
 *
 * Typed `specialUse`: continuous measurement has no runtime cap and may restart from
 * BOOT_COMPLETED, unlike `dataSync` (6 h/day on Android 15+). [TelemetryWatchdogWorker] restarts
 * it if the system kills it. Samples go to the TelemetryRepository (Room); after each durable read,
 * [UsageRollup] rolls closed 10-minute windows up into `data_usage`.
 */
@AndroidEntryPoint
class TelemetryService : Service() {

    @Inject lateinit var collector: TelemetryCollector
    @Inject lateinit var signalMonitor: SignalStrengthMonitor
    @Inject lateinit var controller: TelemetryController
    @Inject lateinit var rollup: UsageRollup

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loops: Job? = null
    private val switches = NetworkSwitchTracker()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var cellularCallback: ConnectivityManager.NetworkCallback? = null

    /** Bytes counted since this service started, for the notification. */
    private val measuredBytes = AtomicLong()

    /** Set when the service stops without the user asking, so tracking resumes on restart. */
    private var keepCollecting = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must run first on every start, or Android kills the app
        TelemetryNotifications.ensureChannels(this)
        ServiceCompat.startForeground(
            this,
            TelemetryNotifications.TRACKING_NOTIFICATION_ID,
            TelemetryNotifications.tracking(this, measuredBytes.get()),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )

        // A sticky restart after the user turned tracking off (or signed out)
        if (!controller.shouldCollect) {
            Log.i(TAG, "Tracking is off; stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        if (loops == null) startLoops()
        return START_STICKY
    }

    private fun startLoops() {
        _running.value = true
        signalMonitor.start()
        registerNetworkCallback()
        controller.scheduleWatchdog()

        loops = scope.launch {
            // Primary loop: a tick every 30 s, each in its own coroutine like the harvester's
            // Handler, so a slow tick never shifts the schedule
            launch {
                while (isActive) {
                    launch { collectPrimary(switchedFrom = null) }
                    delay(MeasurementRules.COLLECTION_INTERVAL_MS)
                }
            }
            // Durable loop: first read 30 s after start to avoid a startup spike
            launch {
                delay(FIRST_DURABLE_DELAY_MS)
                while (isActive) {
                    launch { collectDurable() }
                    delay(MeasurementRules.APP_COLLECTION_INTERVAL_MS)
                }
            }
        }
        Log.i(TAG, "Tracking started (30 s samples, 10 min durable reads, network-switch closing)")
    }

    private suspend fun collectPrimary(switchedFrom: String?) {
        try {
            val sample = collector.collectPrimary(switchedFrom, switches.cellularSeenThisInterval)
            switches.onIntervalClosed()
            measuredBytes.addAndGet(sample.bytesTotal)
            TelemetryNotifications.notifyTracking(this, measuredBytes.get())
            Log.d(TAG, "Sample [${sample.status}${switchedFrom?.let { ", switch from $it" } ?: ""}] " +
                "${sample.bytesTotal} B in ${sample.elapsedMs} ms on ${sample.networkType}, " +
                "${sample.signalDbm} dBm, ${sample.carrier}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission error; stopping tracking", e)
            stopSelf()
        } catch (e: Exception) {
            // The checkpoint was not committed, so the bytes carry over to the next tick
            Log.e(TAG, "Sample failed", e)
        }
    }

    private suspend fun collectDurable() {
        try {
            val readings = collector.collectDurable()
            if (readings == null) Log.w(TAG, "Durable read skipped (no Usage Access or query failed)")
            else Log.d(TAG, "Durable read: ${readings.sumOf { it.bytesTotal }} B in ${readings.size} span(s)")
        } catch (e: Exception) {
            Log.e(TAG, "Durable read failed; checkpoint kept for retry", e)
        }
        // Independent of the durable read: the rollup only needs the 30-second samples
        try {
            val rows = rollup.run()
            if (rows > 0) Log.d(TAG, "Rolled up $rows data_usage window(s)")
        } catch (e: Exception) {
            Log.e(TAG, "Usage rollup failed; it resumes on the next read", e)
        }
    }

    // Network switches

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        // Set before registering, so the callback's first report is not a false switch
        switches.initialize(networkType(cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onNetwork(networkType(caps))
            override fun onLost(network: Network) = onNetwork(NetworkSwitchTracker.NONE)
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "Network switch detection unavailable", e)
        }

        // Mobile data coming up, default or not: re-baseline the returned counter right away.
        // Listening only; it never brings a cellular network up
        val cellular = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Mobile data up: closing the interval")
                scope.launch { collectPrimary(switchedFrom = null) }
            }
        }
        try {
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                cellular
            )
            cellularCallback = cellular
        } catch (e: Exception) {
            Log.w(TAG, "Mobile data detection unavailable", e)
        }
    }

    private fun onNetwork(type: String) {
        val switch = switches.onDefaultNetworkChanged(type, SystemClock.elapsedRealtime()) ?: return
        Log.i(TAG, "Network switch from ${switch.from} to $type: closing the interval")
        // The open interval ran on the old network; close it under that label
        scope.launch { collectPrimary(switchedFrom = switch.from) }
        if (switch.runDurableRead) scope.launch { collectDurable() }
    }

    private fun networkType(caps: NetworkCapabilities?): String = when {
        caps == null -> NetworkSwitchTracker.NONE
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkSwitchTracker.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkSwitchTracker.MOBILE
        else -> NetworkSwitchTracker.OTHER
    }

    private fun unregisterNetworkCallback() {
        listOfNotNull(networkCallback, cellularCallback).forEach {
            try {
                getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister a network callback", e)
            }
        }
        networkCallback = null
        cellularCallback = null
    }

    /**
     * Android 15+ calls this when a time-limited foreground-service type runs out. specialUse is
     * not time-limited, so this is a safety net: stop cleanly and let the watchdog restart it.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service timeout (type $fgsType); stopping cleanly")
        keepCollecting = true
        stopSelf()
    }

    override fun onDestroy() {
        _running.value = false
        loops = null
        scope.cancel()
        unregisterNetworkCallback()
        signalMonitor.stop()
        // Stopped by anything other than a timeout: tracking stays off until the user starts it
        if (!keepCollecting && controller.shouldCollect) controller.stop()
        Log.i(TAG, "Tracking stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "TelemetryService"
        private const val FIRST_DURABLE_DELAY_MS = 30_000L

        private val _running = MutableStateFlow(false)

        /** True while this process runs the measurement loops; read by the watchdog and the UI. */
        val running: StateFlow<Boolean> = _running.asStateFlow()
    }
}
