package com.capstone.datara.data.local.device

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.capstone.datara.data.local.dao.DeviceDao
import com.capstone.datara.data.local.entity.Device
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Where this install keeps its `device_id`s, one per signed-in account. */
interface DeviceIdStore {
    fun get(userId: String): String?
    fun put(userId: String, deviceId: String)
}

/** Hardware metadata of this phone (ROADMAP D2). No permission needed. */
interface HardwareInfo {
    /** `Build.MODEL`, e.g. "M2012K11AG". */
    val model: String

    /** `Build.VERSION.SDK_INT`, e.g. 34. */
    val sdkInt: Int
}

class BuildHardwareInfo @Inject constructor() : HardwareInfo {
    override val model: String get() = Build.MODEL
    override val sdkInt: Int get() = Build.VERSION.SDK_INT
}

/**
 * SharedPreferences, excluded from Auto Backup and device transfer (res/xml backup rules): a
 * restored copy on another phone would otherwise reuse this phone's `device_id`.
 */
class PreferencesDeviceIdStore @Inject constructor(
    @ApplicationContext context: Context
) : DeviceIdStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(userId: String): String? = prefs.getString(KEY_PREFIX + userId, null)

    // commit, not apply: the id must be on disk before a row that uses it is written
    override fun put(userId: String, deviceId: String) = prefs.edit(commit = true) { putString(KEY_PREFIX + userId, deviceId) }

    companion object {
        const val PREFS_NAME = "datara_device"
        private const val KEY_PREFIX = "device_id_"
    }
}

/**
 * Registers this install in `devices`. The `device_id` is generated once per install and account
 * and then reused, so a reinstall (or a second account on the same phone, since `device_id` is the
 * Postgres primary key) gets a new row.
 *
 * The carrier is not stored here: D4 detects it on every sample (`RadioInfoSource.carrierName`),
 * and it is recorded per `data_usage` row, so SIM changes are covered.
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val dao: DeviceDao,
    private val ids: DeviceIdStore,
    private val hardware: HardwareInfo
) {
    private val lock = Mutex()

    /**
     * This install's device for [userId], created on first use. An OS update changes the SDK
     * level, so the row is refreshed (and queued for upload) when the hardware info differs.
     */
    suspend fun ensureRegistered(userId: String, now: Long = System.currentTimeMillis()): Device = lock.withLock {
        val deviceId = ids.get(userId) ?: UUID.randomUUID().toString().also { ids.put(userId, it) }
        val existing = dao.get(deviceId)
        when {
            existing == null -> Device(
                deviceId = deviceId,
                userId = userId,
                deviceModel = hardware.model,
                androidSdkInt = hardware.sdkInt,
                deviceName = null,
                createdAt = now
            ).also { dao.upsert(it) }

            existing.deviceModel != hardware.model || existing.androidSdkInt != hardware.sdkInt ->
                existing.copy(deviceModel = hardware.model, androidSdkInt = hardware.sdkInt, syncedAt = null)
                    .also { dao.upsert(it) }

            else -> existing
        }
    }

    /** This install's `device_id` for [userId], or null before [ensureRegistered]. */
    fun deviceId(userId: String): String? = ids.get(userId)
}
