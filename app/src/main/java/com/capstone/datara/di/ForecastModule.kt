package com.capstone.datara.di

import com.capstone.datara.forecast.AssetNextHourModelProvider
import com.capstone.datara.forecast.CycleStateSource
import com.capstone.datara.forecast.ForecastStore
import com.capstone.datara.forecast.ForecastZone
import com.capstone.datara.forecast.NextHourModelProvider
import com.capstone.datara.forecast.QuotaCycleStateSource
import com.capstone.datara.forecast.RoomForecastStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId

@Module
@InstallIn(SingletonComponent::class)
abstract class ForecastModule {

    @Binds
    abstract fun bindForecastStore(impl: RoomForecastStore): ForecastStore

    @Binds
    abstract fun bindCycleStateSource(impl: QuotaCycleStateSource): CycleStateSource

    /** The bundled ONNX model; on-device inference only, never a /predict call. */
    @Binds
    abstract fun bindNextHourModelProvider(impl: AssetNextHourModelProvider): NextHourModelProvider

    companion object {
        /** The phone's own zone, read at each run (training used Asia/Manila, UTC+8). */
        @Provides
        fun provideForecastZone(): ForecastZone = ForecastZone { ZoneId.systemDefault() }
    }
}
