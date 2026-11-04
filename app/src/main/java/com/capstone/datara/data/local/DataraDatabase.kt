package com.capstone.datara.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.capstone.datara.data.local.dao.DataUsageDao
import com.capstone.datara.data.local.dao.DeviceDao
import com.capstone.datara.data.local.dao.ForecastDao
import com.capstone.datara.data.local.dao.NotificationDao
import com.capstone.datara.data.local.dao.TelemetryDao
import com.capstone.datara.data.local.dao.TrackingCycleDao
import com.capstone.datara.data.local.dao.UserDao
import com.capstone.datara.data.local.entity.DataUsage
import com.capstone.datara.data.local.entity.Device
import com.capstone.datara.data.local.entity.DurableCheckpointEntity
import com.capstone.datara.data.local.entity.DurableReadingEntity
import com.capstone.datara.data.local.entity.Forecast
import com.capstone.datara.data.local.entity.Notification
import com.capstone.datara.data.local.entity.NotificationPreferences
import com.capstone.datara.data.local.entity.TelemetryCheckpointEntity
import com.capstone.datara.data.local.entity.TrackingCycle
import com.capstone.datara.data.local.entity.UsageSampleEntity
import com.capstone.datara.data.local.entity.User
import com.capstone.datara.data.local.entity.UserModelState

/**
 * The phone's database (D2): the eight tables synced with Supabase (migration
 * `20261104000001`) plus the four phone-only D4 telemetry tables.
 *
 * Schemas are exported to `app/schemas/` (see the `room` block in `app/build.gradle.kts`). Every
 * schema change bumps [version] and ships a `Migration`; never rely on destructive migration,
 * which would delete unsynced usage. A migration that rebuilds `tracking_cycles` must recreate
 * [ONE_ACTIVE_CYCLE_TRIGGERS], which Room does not track.
 *
 * No foreign keys: Postgres checks ownership on upload, and local foreign keys would force C3 to
 * download parents before children. No type converters: every column is a SQLite primitive.
 */
@Database(
    entities = [
        User::class,
        Device::class,
        TrackingCycle::class,
        DataUsage::class,
        Forecast::class,
        Notification::class,
        NotificationPreferences::class,
        UserModelState::class,
        UsageSampleEntity::class,
        DurableReadingEntity::class,
        TelemetryCheckpointEntity::class,
        DurableCheckpointEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class DataraDatabase : RoomDatabase() {

    abstract fun telemetryDao(): TelemetryDao
    abstract fun userDao(): UserDao
    abstract fun deviceDao(): DeviceDao
    abstract fun trackingCycleDao(): TrackingCycleDao
    abstract fun dataUsageDao(): DataUsageDao
    abstract fun forecastDao(): ForecastDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        /** Excluded from Auto Backup and device transfer (res/xml backup rules). */
        const val NAME = "datara.db"

        /**
         * Postgres keeps one active cycle per device with the partial unique index
         * `tracking_cycles_one_active_per_device`. Room cannot declare a partial index (and would
         * reject an undeclared one when validating the schema), so these triggers enforce the
         * same rule. A violation aborts the statement with SQLiteConstraintException.
         */
        val ONE_ACTIVE_CYCLE_TRIGGERS = listOf("INSERT", "UPDATE").map { event ->
            """
            CREATE TRIGGER IF NOT EXISTS tracking_cycles_one_active_per_device_${event.lowercase()}
            BEFORE $event ON tracking_cycles
            WHEN NEW.status = 'active' AND EXISTS (
                SELECT 1 FROM tracking_cycles
                WHERE device_id = NEW.device_id AND status = 'active' AND cycle_id <> NEW.cycle_id
            )
            BEGIN
                SELECT RAISE(ABORT, 'tracking_cycles_one_active_per_device');
            END
            """.trimIndent()
        }

        /** Creates the triggers Room does not manage, when the database file is first created. */
        val CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                ONE_ACTIVE_CYCLE_TRIGGERS.forEach(db::execSQL)
            }
        }

        fun build(context: Context): DataraDatabase =
            Room.databaseBuilder(context.applicationContext, DataraDatabase::class.java, NAME)
                .addCallback(CALLBACK)
                .build()

        /** A throwaway database for tests, with the same triggers. */
        fun inMemory(context: Context): DataraDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, DataraDatabase::class.java)
                .addCallback(CALLBACK)
                .build()
    }
}
