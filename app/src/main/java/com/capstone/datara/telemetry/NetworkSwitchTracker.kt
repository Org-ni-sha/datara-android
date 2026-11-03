package com.capstone.datara.telemetry

/**
 * Default-network bookkeeping of the foreground service, mirrored from data-harvester's
 * `DataCollectionService.onDefaultNetworkChanged`.
 *
 * On a Wi-Fi ↔ mobile switch the open 30-second interval is closed at once under the OLD network
 * (so mobile bytes before joining Wi-Fi are never labelled WIFI), and the 10-minute read runs
 * immediately. Switches closer together than [debounceMs] are not closed separately; the open
 * interval then only remembers that a mobile network was the default during it.
 *
 * Network types are "WIFI", "MOBILE", "OTHER" or "NONE". Thread-safe: the connectivity callback
 * and the sampling loop call it from different threads.
 */
class NetworkSwitchTracker(private val debounceMs: Long = MIN_SWITCH_INTERVAL_MS) {

    /** A switch that closes the open interval now. */
    data class Switch(
        /** The network the closing interval ran on. */
        val from: String,
        /** Also run the 10-minute read; skipped when coming back from no network. */
        val runDurableRead: Boolean
    )

    private var currentType: String? = null
    private var lastSwitchAt = Long.MIN_VALUE
    private var cellularSeen = false

    /** True once a non-Wi-Fi network was the default during the open interval. */
    val cellularSeenThisInterval: Boolean
        @Synchronized get() = cellularSeen

    /** Sets the network at registration, so the callback's first report is not a false switch. */
    @Synchronized
    fun initialize(type: String) {
        currentType = type
        cellularSeen = isNonWifiData(type)
    }

    /** @param now a monotonic clock (elapsedRealtime) */
    @Synchronized
    fun onDefaultNetworkChanged(newType: String, now: Long): Switch? {
        val previous = currentType
        currentType = newType
        if (previous == null || previous == newType) return null

        if (lastSwitchAt == Long.MIN_VALUE || now - lastSwitchAt > debounceMs) {
            lastSwitchAt = now
            return Switch(from = previous, runDurableRead = previous != NONE)
        }
        // Not closed by a tick: the open interval now mixes networks
        if (isNonWifiData(newType)) cellularSeen = true
        return null
    }

    /** The next interval starts on the current network. */
    @Synchronized
    fun onIntervalClosed() {
        cellularSeen = isNonWifiData(currentType)
    }

    companion object {
        /** Minimum time between switch-triggered collections. */
        const val MIN_SWITCH_INTERVAL_MS = 5_000L

        const val WIFI = "WIFI"
        const val MOBILE = "MOBILE"
        const val OTHER = "OTHER"
        const val NONE = "NONE"

        /** A default network other than Wi-Fi that can carry traffic (no network moves no counters). */
        fun isNonWifiData(type: String?) = type != null && type != WIFI && type != NONE

        /**
         * The network label of a closing interval: the one it ran on when a switch closed it,
         * otherwise the network active now.
         */
        fun intervalNetwork(switchedFrom: String?, activeNetwork: String, cellularRadio: () -> String): String =
            when (switchedFrom) {
                null -> activeNetwork
                WIFI -> WIFI
                MOBILE -> cellularRadio()
                NONE -> NONE
                else -> activeNetwork
            }
    }
}
