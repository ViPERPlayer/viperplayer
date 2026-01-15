package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.viperplayer.data.local.entity.ViperPresetEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for ViPER preset operations.
 */
@Dao
interface ViperPresetDao {
    @Query("SELECT * FROM viper_presets ORDER BY updatedAt DESC")
    fun getAllPresets(): Flow<List<ViperPresetEntity>>

    @Query("SELECT * FROM viper_presets WHERE id = :id")
    suspend fun getPresetById(id: Long): ViperPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: ViperPresetEntity): Long

    @Update
    suspend fun updatePreset(preset: ViperPresetEntity)

    @Delete
    suspend fun deletePreset(preset: ViperPresetEntity)

    @Query("DELETE FROM viper_presets WHERE id = :id")
    suspend fun deletePresetById(id: Long)

    // Device-specific queries
    @Query("SELECT * FROM viper_presets WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getPresetByDeviceId(deviceId: String): ViperPresetEntity?

    @Query("SELECT * FROM viper_presets WHERE deviceId IS NULL")
    fun getDefaultPresets(): Flow<List<ViperPresetEntity>>

    @Query("SELECT * FROM viper_presets WHERE deviceId IS NOT NULL")
    fun getDevicePresets(): Flow<List<ViperPresetEntity>>

    @Query("DELETE FROM viper_presets WHERE deviceId = :deviceId")
    suspend fun deletePresetsByDeviceId(deviceId: String)
}

