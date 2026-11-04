package com.capstone.datara.di

import com.capstone.datara.data.cycle.CycleOwnerProvider
import com.capstone.datara.data.cycle.DeviceIdProvider
import com.capstone.datara.data.cycle.InMemoryTrackingCycleRepository
import com.capstone.datara.data.cycle.PreferencesDeviceIdProvider
import com.capstone.datara.data.cycle.SessionCycleOwnerProvider
import com.capstone.datara.data.cycle.TelemetryUsageReadingRepository
import com.capstone.datara.data.cycle.TrackingCycleRepository
import com.capstone.datara.data.cycle.UsageReadingRepository
import com.capstone.datara.domain.cycle.CycleClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** A scope that lives as long as the app process, for work no screen owns. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class CycleModule {

    /** In memory until D2: replace this binding with the Room-backed repository. */
    @Binds
    abstract fun bindTrackingCycleRepository(impl: InMemoryTrackingCycleRepository): TrackingCycleRepository

    /** Reads D4's durable readings through TelemetryRepository, so it follows that binding. */
    @Binds
    abstract fun bindUsageReadingRepository(impl: TelemetryUsageReadingRepository): UsageReadingRepository

    @Binds
    abstract fun bindCycleOwnerProvider(impl: SessionCycleOwnerProvider): CycleOwnerProvider

    @Binds
    abstract fun bindDeviceIdProvider(impl: PreferencesDeviceIdProvider): DeviceIdProvider

    companion object {
        @Provides
        fun provideCycleClock(): CycleClock = CycleClock { System.currentTimeMillis() }

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
