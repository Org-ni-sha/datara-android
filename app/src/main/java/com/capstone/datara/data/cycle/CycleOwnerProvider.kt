package com.capstone.datara.data.cycle

import com.capstone.datara.data.local.device.DeviceRepository
import com.capstone.datara.data.repository.AuthRepository
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Who a cycle belongs to: the signed-in user on this install. */
data class CycleOwner(val userId: String, val deviceId: String)

/** The current [CycleOwner], or null while signed out (or while the session is restoring). */
interface CycleOwnerProvider {
    val owner: Flow<CycleOwner?>
}

/**
 * The signed-in user from the Supabase session, and this install's `device_id` for that account
 * from [DeviceRepository] (D2), which registers the `devices` row on first use.
 */
@Singleton
class SessionCycleOwnerProvider @Inject constructor(
    auth: AuthRepository,
    private val devices: DeviceRepository
) : CycleOwnerProvider {

    override val owner: Flow<CycleOwner?> = auth.sessionStatus
        .map { status -> (status as? SessionStatus.Authenticated)?.session?.user?.id }
        .distinctUntilChanged()
        .map { userId -> userId?.let { CycleOwner(it, devices.ensureRegistered(it).deviceId) } }
}
