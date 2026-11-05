package com.capstone.datara

import android.app.Application
import com.capstone.datara.domain.cycle.QuotaCycleManager
import com.capstone.datara.forecast.ForecastEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DataraApplication : Application() {

    /**
     * Injected so the cycle manager starts with the process (including a service-only restart)
     * and records a reached quota even when no screen is open.
     */
    @Inject
    lateinit var quotaCycles: QuotaCycleManager

    /** Forecasts (D6) follow the active cycle from process start, so a new cycle gets its PET at once. */
    @Inject
    lateinit var forecasts: ForecastEngine

    override fun onCreate() {
        super.onCreate()
        forecasts.start()
    }
}
