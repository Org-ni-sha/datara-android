package com.capstone.datara.data.cycle

import android.content.Context
import com.capstone.datara.data.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Who a cycle belongs to: the signed-in user on this install. */
data class CycleOwner(val userId: String, val deviceId: String)

/** The current [CycleOwner], or null while signed out (or while the session is restoring). */
interface CycleOwnerProvider {
    val owner: Flow<CycleOwner?>
}

/**
 * The `devices.device_id` of this install: a UUID generated on first use and kept in
 * SharedPreferences, so it is stable across restarts and replaced by a reinstall (as the ERD
 * describes). The `devices` row itself is written by sync (C3), not here.
 */
interface DeviceIdProvider {
    fun deviceId(): String
}

@Singleton
class PreferencesDeviceIdProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DeviceIdProvider {

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    @Synchronized
    override fun deviceId(): String = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_DEVICE_ID, it).apply()
    }

    private companion object {
        const val PREFS = "datara_device"
        const val KEY_DEVICE_ID = "device_id"
    }
}

@Singleton
class SessionCycleOwnerProvider @Inject constructor(
    auth: AuthRepository,
    private val devices: DeviceIdProvider
) : CycleOwnerProvider {

    override val owner: Flow<CycleOwner?> = auth.sessionStatus
        .map { status -> (status as? SessionStatus.Authenticated)?.session?.user?.id }
        .distinctUntilChanged()
        .map { userId -> userId?.let { CycleOwner(it, devices.deviceId()) } }
}
