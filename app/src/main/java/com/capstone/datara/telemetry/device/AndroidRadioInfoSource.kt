package com.capstone.datara.telemetry.device

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.capstone.datara.telemetry.RadioInfoSource
import com.capstone.datara.telemetry.RadioLabels
import com.capstone.datara.telemetry.TelemetryAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Network type, signal and carrier through ConnectivityManager and TelephonyManager, matching
 * data-harvester's `DeviceInfoHelper`. Never reads SMS; the carrier is detected, not entered.
 */
class AndroidRadioInfoSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val access: TelemetryAccess,
    private val signalMonitor: SignalStrengthMonitor
) : RadioInfoSource {

    private val telephony get() = context.getSystemService(TelephonyManager::class.java)

    override fun activeNetworkLabel(): String = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        when {
            network == null -> "NONE"
            caps == null -> "UNKNOWN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> cellularRadioLabel()
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "UNKNOWN"
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read the active network", e)
        "UNKNOWN"
    }

    /** dataNetworkType needs READ_PHONE_STATE; without it the label is CELLULAR, as in the harvester. */
    @SuppressLint("MissingPermission") // checked through TelemetryAccess just above the call
    override fun cellularRadioLabel(): String {
        if (!access.hasPhoneState()) return "CELLULAR"
        return try {
            RadioLabels.cellularRadioLabel(telephony?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN)
        } catch (e: SecurityException) {
            "CELLULAR"
        }
    }

    override fun signalDbm(): Int = signalMonitor.currentDbm()

    @Suppress("DEPRECATION") // allNetworks: the only synchronous view of non-default networks
    override fun isMobileDataUp(): Boolean = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm?.allNetworks.orEmpty().any { network ->
            cm?.getNetworkCapabilities(network)?.let {
                it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } == true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read mobile data state", e)
        true // unknown: keep the harvester's rules
    }

    /** The default data SIM's operator first (dual-SIM), then the device's. Needs no permission. */
    override fun carrierName(): String = try {
        val tm = telephony
        val subId = SubscriptionManager.getDefaultDataSubscriptionId()
        val dataSim = if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) tm?.createForSubscriptionId(subId) else null
        RadioLabels.carrierName(
            dataSim?.simOperatorName, dataSim?.networkOperatorName,
            tm?.simOperatorName, tm?.networkOperatorName
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read the carrier", e)
        RadioLabels.CARRIER_UNKNOWN
    }

    private companion object {
        const val TAG = "RadioInfoSource"
    }
}
