package com.capstone.datara.telemetry.device

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.capstone.datara.telemetry.RadioLabels
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the latest cell signal strength while the tracking service runs. Listening for signal
 * strength needs no permission.
 *
 * Android 12+ (API 31) uses [TelephonyCallback]; older versions use the deprecated
 * [PhoneStateListener]. minSdk is 26, so both paths are needed.
 */
@Singleton
class SignalStrengthMonitor @Inject constructor(@param:ApplicationContext private val context: Context) {

    @Volatile
    private var latestDbm = RadioLabels.SIGNAL_UNKNOWN
    private var registration: Any? = null

    private val telephony get() = context.getSystemService(TelephonyManager::class.java)

    /** Starts listening. Call from the main thread (PhoneStateListener binds to its Looper). */
    @Synchronized
    fun start() {
        if (registration != null) return
        val tm = telephony ?: return
        registration = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Api31Listener { latestDbm = dbmOf(it) }.also { tm.registerTelephonyCallback(context.mainExecutor, it) }
            } else {
                legacyListener().also {
                    @Suppress("DEPRECATION")
                    tm.listen(it, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Signal strength listener unavailable", e)
            null
        }
    }

    @Suppress("DEPRECATION") // PhoneStateListener is the only option below API 31
    @Synchronized
    fun stop() {
        val tm = telephony
        when (val r = registration) {
            null -> return
            is PhoneStateListener -> tm?.listen(r, PhoneStateListener.LISTEN_NONE)
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) tm?.unregisterTelephonyCallback(r as TelephonyCallback)
        }
        registration = null
        latestDbm = RadioLabels.SIGNAL_UNKNOWN
    }

    /**
     * The last reported dBm. Before the first callback (or when not listening), Android 10+ is
     * asked directly, as the harvester did on every tick.
     */
    fun currentDbm(): Int {
        val cached = latestDbm
        if (cached != RadioLabels.SIGNAL_UNKNOWN) return cached
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return cached
        return try {
            telephony?.signalStrength?.let(::dbmOf) ?: RadioLabels.SIGNAL_UNKNOWN
        } catch (e: Exception) {
            RadioLabels.SIGNAL_UNKNOWN
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyListener() = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            latestDbm = signalStrength?.let(::dbmOf) ?: RadioLabels.SIGNAL_UNKNOWN
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private class Api31Listener(private val onChange: (SignalStrength) -> Unit) :
        TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = onChange(signalStrength)
    }

    private companion object {
        const val TAG = "SignalStrengthMonitor"

        /**
         * dBm of the first reported cell (the harvester's choice). Android 8–9 has no per-cell
         * dBm; the GSM ASU is converted when present, otherwise the value stays unknown.
         */
        fun dbmOf(signal: SignalStrength): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RadioLabels.signalDbm(signal.cellSignalStrengths.firstOrNull()?.dbm)
            } else {
                @Suppress("DEPRECATION")
                if (signal.isGsm) RadioLabels.gsmAsuToDbm(signal.gsmSignalStrength) else RadioLabels.SIGNAL_UNKNOWN
            }
    }
}
