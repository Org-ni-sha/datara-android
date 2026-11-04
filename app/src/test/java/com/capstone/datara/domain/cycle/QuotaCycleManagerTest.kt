package com.capstone.datara.domain.cycle

import com.capstone.datara.data.cycle.InMemoryTrackingCycleRepository
import com.capstone.datara.data.cycle.TelemetryUsageReadingRepository
import com.capstone.datara.data.telemetry.InMemoryTelemetryRepository

/** The cycle use-case contract against the in-memory repositories (JVM). */
class QuotaCycleManagerTest : QuotaCycleManagerContract() {
    override fun createStorage(): Storage {
        val telemetry = InMemoryTelemetryRepository()
        return Storage(InMemoryTrackingCycleRepository(), telemetry, TelemetryUsageReadingRepository(telemetry))
    }
}
