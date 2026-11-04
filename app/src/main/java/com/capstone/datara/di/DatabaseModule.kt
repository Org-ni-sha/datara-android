package com.capstone.datara.di

import android.content.Context
import com.capstone.datara.data.local.DataraDatabase
import com.capstone.datara.data.local.dao.DataUsageDao
import com.capstone.datara.data.local.dao.DeviceDao
import com.capstone.datara.data.local.dao.ForecastDao
import com.capstone.datara.data.local.dao.NotificationDao
import com.capstone.datara.data.local.dao.TelemetryDao
import com.capstone.datara.data.local.dao.TrackingCycleDao
import com.capstone.datara.data.local.dao.UserDao
import com.capstone.datara.data.local.device.DeviceIdStore
import com.capstone.datara.data.local.device.HardwareInfo
import com.capstone.datara.data.local.device.BuildHardwareInfo
import com.capstone.datara.data.local.device.PreferencesDeviceIdStore
import com.capstone.datara.data.session.CurrentUserSource
import com.capstone.datara.data.session.SupabaseCurrentUserSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DataraDatabase = DataraDatabase.build(context)

    @Provides fun provideTelemetryDao(db: DataraDatabase): TelemetryDao = db.telemetryDao()
    @Provides fun provideUserDao(db: DataraDatabase): UserDao = db.userDao()
    @Provides fun provideDeviceDao(db: DataraDatabase): DeviceDao = db.deviceDao()
    @Provides fun provideTrackingCycleDao(db: DataraDatabase): TrackingCycleDao = db.trackingCycleDao()
    @Provides fun provideDataUsageDao(db: DataraDatabase): DataUsageDao = db.dataUsageDao()
    @Provides fun provideForecastDao(db: DataraDatabase): ForecastDao = db.forecastDao()
    @Provides fun provideNotificationDao(db: DataraDatabase): NotificationDao = db.notificationDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceModule {

    @Binds
    abstract fun bindDeviceIdStore(impl: PreferencesDeviceIdStore): DeviceIdStore

    @Binds
    abstract fun bindHardwareInfo(impl: BuildHardwareInfo): HardwareInfo

    @Binds
    abstract fun bindCurrentUserSource(impl: SupabaseCurrentUserSource): CurrentUserSource
}
