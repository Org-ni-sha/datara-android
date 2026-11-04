package com.capstone.datara.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.data.telemetry.RoomTelemetryRepository
import com.capstone.datara.data.telemetry.UsageSample
import com.capstone.datara.telemetry.DurableCheckpoint
import com.capstone.datara.telemetry.TelemetryCheckpoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What D4 lacked: samples and checkpoints survive closing and reopening the database file. */
@RunWith(AndroidJUnit4::class)
class TelemetryPersistenceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "telemetry-persistence-test.db"

    private fun open() = Room.databaseBuilder(context, DataraDatabase::class.java, name)
        .addCallback(DataraDatabase.CALLBACK)
        .build()

    private val sample = UsageSample(
        intervalStart = 1_000, intervalEnd = 31_000, elapsedMs = 30_000, bytesRx = 640, bytesTx = 10,
        status = "OK", networkType = "LTE", activeNetworkAtSample = "LTE", signalDbm = -95,
        carrier = "GLOBE", utcOffsetMinutes = 480, schemaVersion = 3
    )
    private val checkpoint = TelemetryCheckpoint(1_640, 110, 31_000, 31_000, 31_000, 31_000, mobileUpAtReading = false)

    @Before
    @After
    fun deleteFile() {
        context.deleteDatabase(name)
    }

    @Test
    fun samplesAndCheckpointsSurviveReopening() = runBlocking {
        val first = open()
        RoomTelemetryRepository(first.telemetryDao()).apply {
            commitPrimary(sample, checkpoint)
            commitDurable(
                listOf(DurableUsageReading(since = 0, until = 600_000, bytesRx = 900, bytesTx = 20, status = "OK", schemaVersion = 3)),
                DurableCheckpoint(anchor = 0, rx = 900, tx = 20, takenAt = 600_000)
            )
        }
        first.close()

        val reopened = open()
        val repository = RoomTelemetryRepository(reopened.telemetryDao())
        assertEquals(checkpoint, repository.primaryCheckpoint())
        assertEquals(listOf(sample), repository.observeSamples().first())
        assertEquals(DurableCheckpoint(0, 900, 20, 600_000), repository.durableCheckpoint())
        assertEquals(920L, repository.observeDurableReadings().first().single().bytesTotal)
        reopened.close()
    }

    @Test
    fun aFailedSampleInsertLeavesTheCheckpointUnchanged() = runBlocking {
        val db = open()
        val repository = RoomTelemetryRepository(db.telemetryDao())
        repository.commitPrimary(sample, checkpoint)
        // Same id: the insert fails, so the transaction must not move the checkpoint either
        runCatching { repository.commitPrimary(sample, checkpoint.copy(mobileRx = 9_999)) }
        assertEquals(checkpoint, repository.primaryCheckpoint())
        assertEquals(1, repository.observeSamples().first().size)
        db.close()
    }

    @Test
    fun emptyDatabaseHasNoCheckpoints() = runBlocking {
        val db = open()
        val repository = RoomTelemetryRepository(db.telemetryDao())
        assertNull(repository.primaryCheckpoint())
        assertNull(repository.durableCheckpoint())
        db.close()
    }
}
