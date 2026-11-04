package com.capstone.datara

import android.app.Application
import com.capstone.datara.domain.cycle.QuotaCycleManager
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
}
