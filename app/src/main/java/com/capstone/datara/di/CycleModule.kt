package com.capstone.datara.di

import com.capstone.datara.data.cycle.CycleOwnerProvider
import com.capstone.datara.data.cycle.SessionCycleOwnerProvider
import com.capstone.datara.data.cycle.RoomTrackingCycleRepository
import com.capstone.datara.data.cycle.RoomUsageReadingRepository
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

    /** Room (D2): cycles survive process death. The in-memory version is for JVM tests. */
    @Binds
    abstract fun bindTrackingCycleRepository(impl: RoomTrackingCycleRepository): TrackingCycleRepository

    /** Every Room `durable_readings` row since a cycle's start. */
    @Binds
    abstract fun bindUsageReadingRepository(impl: RoomUsageReadingRepository): UsageReadingRepository

    @Binds
    abstract fun bindCycleOwnerProvider(impl: SessionCycleOwnerProvider): CycleOwnerProvider

    companion object {
        @Provides
        fun provideCycleClock(): CycleClock = CycleClock { System.currentTimeMillis() }

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
