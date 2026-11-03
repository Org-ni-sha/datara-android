package com.capstone.datara.telemetry

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Every 15 minutes: restarts the tracking service if the user left tracking on but the system
 * (or a vendor battery manager) killed it. It takes no measurements; time without tracking shows
 * up in the data as a GAP interval.
 */
class TelemetryWatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        TelemetryController(applicationContext).restartIfWanted("Watchdog")
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "TelemetryWatchdog"
    }
}
