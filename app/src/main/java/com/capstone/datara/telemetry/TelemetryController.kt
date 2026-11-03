package com.capstone.datara.telemetry

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Starts and stops usage tracking. The user starts it from the tracking setup screen; signing out
 * stops it. "Should collect" is remembered so the watchdog and the boot receiver restart tracking
 * only when the user left it on.
 *
 * Holds no state of its own, so the watchdog and the boot receiver can construct one directly.
 */
class TelemetryController @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val prefs get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True while the service runs its loops in this process. */
    val isRunning: StateFlow<Boolean> get() = TelemetryService.running

    /** The user turned tracking on and has not turned it off or signed out since. */
    val shouldCollect: Boolean get() = prefs.getBoolean(KEY_SHOULD_COLLECT, false)

    fun start() {
        prefs.edit { putBoolean(KEY_SHOULD_COLLECT, true) }
        ContextCompat.startForegroundService(context, Intent(context, TelemetryService::class.java))
        scheduleWatchdog()
    }

    fun stop() {
        prefs.edit { putBoolean(KEY_SHOULD_COLLECT, false) }
        context.stopService(Intent(context, TelemetryService::class.java))
        WorkManager.getInstance(context).cancelUniqueWork(TelemetryWatchdogWorker.WORK_NAME)
    }

    /**
     * Restarts tracking from the background (watchdog, boot) when the user left it on and it is
     * not running. Android 12+ refuses unless the app is exempt from battery optimization; the
     * user is then asked to open the app.
     */
    fun restartIfWanted(reason: String) {
        if (!shouldCollect || TelemetryService.running.value) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, TelemetryService::class.java))
            Log.i(TAG, "$reason: tracking was not running, restarted")
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+) is an IllegalStateException
            Log.w(TAG, "$reason: Android refused a background restart", e)
            TelemetryNotifications.notifyPaused(context)
        }
    }

    /** 15 minutes is WorkManager's minimum period (ROADMAP Decision 1). Idempotent. */
    fun scheduleWatchdog() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TelemetryWatchdogWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TelemetryWatchdogWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    private companion object {
        const val TAG = "TelemetryController"
        const val PREFS_NAME = "telemetry"
        const val KEY_SHOULD_COLLECT = "should_collect"
    }
}
