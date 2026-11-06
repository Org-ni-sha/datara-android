package com.capstone.datara.di

import com.capstone.datara.alerts.AlertPoster
import com.capstone.datara.alerts.AlertStore
import com.capstone.datara.alerts.AndroidAlertPoster
import com.capstone.datara.alerts.EngineForecastStatusSource
import com.capstone.datara.alerts.ForecastStatusSource
import com.capstone.datara.alerts.NotificationPreferencesStore
import com.capstone.datara.alerts.RoomAlertStore
import com.capstone.datara.alerts.RoomNotificationPreferencesStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** D8 quota alerts: Room inbox and preferences, Android delivery. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AlertModule {

    @Binds
    abstract fun bindAlertStore(impl: RoomAlertStore): AlertStore

    @Binds
    abstract fun bindNotificationPreferencesStore(impl: RoomNotificationPreferencesStore): NotificationPreferencesStore

    @Binds
    abstract fun bindAlertPoster(impl: AndroidAlertPoster): AlertPoster

    @Binds
    abstract fun bindForecastStatusSource(impl: EngineForecastStatusSource): ForecastStatusSource
}
