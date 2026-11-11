package com.capstone.datara.data.cycle

import com.capstone.datara.data.local.device.DeviceRepository
import com.capstone.datara.data.repository.AuthRepository
import com.capstone.datara.data.session.signedInUserIds
import kotlinx.coroutines.flow.Flow
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

    // Offline after the token expired (RefreshFailure) the owner stays the saved session's user,
    // so forecasts and alerts keep running; signedInUserIds drops the repeat when it returns.
    override val owner: Flow<CycleOwner?> = auth.sessionStatus
        .signedInUserIds(auth.savedSession)
        .map { userId -> userId?.let { CycleOwner(it, devices.ensureRegistered(it).deviceId) } }
}
