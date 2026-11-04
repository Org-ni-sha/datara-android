package com.capstone.datara.telemetry

import com.capstone.datara.data.telemetry.InMemoryTelemetryRepository

/** The collector contract against the in-memory repository (JVM). */
class TelemetryCollectorTest : TelemetryCollectorContract() {
    override fun createRepository() = InMemoryTelemetryRepository()
}
