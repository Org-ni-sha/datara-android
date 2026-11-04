package com.capstone.datara.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.capstone.datara.data.telemetry.RoomTelemetryRepository
import com.capstone.datara.telemetry.TelemetryCollectorContract
import org.junit.After
import org.junit.runner.RunWith

/** The telemetry collector contract against the Room repository the app binds. */
@RunWith(AndroidJUnit4::class)
class RoomTelemetryCollectorTest : TelemetryCollectorContract() {

    private lateinit var db: DataraDatabase

    override fun createRepository(): RoomTelemetryRepository {
        db = DataraDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        return RoomTelemetryRepository(db.telemetryDao())
    }

    @After
    fun closeDb() = db.close()
}
