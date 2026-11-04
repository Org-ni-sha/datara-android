package com.capstone.datara.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.capstone.datara.data.cycle.RoomTrackingCycleRepository
import com.capstone.datara.data.cycle.RoomUsageReadingRepository
import com.capstone.datara.data.telemetry.RoomTelemetryRepository
import com.capstone.datara.domain.cycle.QuotaCycleManagerContract
import org.junit.runner.RunWith

/** The cycle use-case contract against the Room repositories the app binds. */
@RunWith(AndroidJUnit4::class)
class RoomQuotaCycleManagerTest : QuotaCycleManagerContract() {

    private lateinit var db: DataraDatabase

    override fun createStorage(): Storage {
        db = DataraDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        return Storage(
            RoomTrackingCycleRepository(db.trackingCycleDao()),
            RoomTelemetryRepository(db.telemetryDao()),
            RoomUsageReadingRepository(db.telemetryDao())
        )
    }

    override fun closeStorage() = db.close()
}
