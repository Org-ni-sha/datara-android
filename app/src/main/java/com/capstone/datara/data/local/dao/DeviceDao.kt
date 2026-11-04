package com.capstone.datara.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.capstone.datara.data.local.entity.Device

@Dao
interface DeviceDao {

    @Upsert
    suspend fun upsert(device: Device)

    @Query("SELECT * FROM devices WHERE device_id = :deviceId")
    suspend fun get(deviceId: String): Device?

    @Query("SELECT * FROM devices WHERE synced_at IS NULL")
    suspend fun unsynced(): List<Device>

    @Query("UPDATE devices SET synced_at = :syncedAt WHERE device_id = :deviceId")
    suspend fun markSynced(deviceId: String, syncedAt: Long)
}
