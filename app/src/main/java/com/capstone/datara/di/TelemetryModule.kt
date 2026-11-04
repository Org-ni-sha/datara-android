package com.capstone.datara.di

import com.capstone.datara.data.telemetry.RoomTelemetryRepository
import com.capstone.datara.data.telemetry.TelemetryRepository
import com.capstone.datara.telemetry.DurableUsageSource
import com.capstone.datara.telemetry.MobileCounterSource
import com.capstone.datara.telemetry.RadioInfoSource
import com.capstone.datara.telemetry.TelemetryClock
import com.capstone.datara.telemetry.device.AndroidRadioInfoSource
import com.capstone.datara.telemetry.device.NetworkStatsUsageSource
import com.capstone.datara.telemetry.device.SystemTelemetryClock
import com.capstone.datara.telemetry.device.TrafficStatsCounterSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {

    /** Room (D2): samples and checkpoints survive process death and reboots. */
    @Binds
    abstract fun bindTelemetryRepository(impl: RoomTelemetryRepository): TelemetryRepository

    @Binds
    abstract fun bindMobileCounterSource(impl: TrafficStatsCounterSource): MobileCounterSource

    @Binds
    abstract fun bindDurableUsageSource(impl: NetworkStatsUsageSource): DurableUsageSource

    @Binds
    abstract fun bindRadioInfoSource(impl: AndroidRadioInfoSource): RadioInfoSource

    @Binds
    abstract fun bindTelemetryClock(impl: SystemTelemetryClock): TelemetryClock
}
