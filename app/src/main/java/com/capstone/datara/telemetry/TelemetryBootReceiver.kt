package com.capstone.datara.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts tracking after a reboot and after an app update (which stops the running service),
 * when the user left it on. The first sample after a reboot re-baselines (COUNTER_RESET).
 */
class TelemetryBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        TelemetryController(context.applicationContext).restartIfWanted(intent.action.orEmpty())
    }
}
