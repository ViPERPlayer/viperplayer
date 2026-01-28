package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.viperplayer.data.local.entity.ViperDdcCoeffEntity
import com.viperplayer.data.local.entity.ViperPresetEntity
import com.viperplayer.data.local.entity.relation.ViperPresetWithCoeffs
import kotlinx.coroutines.flow.Flow

/**
 * DAO for ViPER preset operations.
 */
@Dao
interface ViperPresetDao {
    @Transaction
    @Query("SELECT * FROM viper_presets ORDER BY updatedAt DESC")
    fun getAllPresets(): Flow<List<ViperPresetWithCoeffs>>

    @Transaction
    @Query("SELECT * FROM viper_presets WHERE id = :id")
    suspend fun getPresetById(id: Long): ViperPresetWithCoeffs?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: ViperPresetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoeffs(coeffs: List<ViperDdcCoeffEntity>)

    @Query("DELETE FROM viper_ddc_coeffs WHERE presetId = :presetId")
    suspend fun deleteCoeffsByPresetId(presetId: Long)

    @Update
    suspend fun updatePreset(preset: ViperPresetEntity)

    @Transaction
    suspend fun updatePresetWithCoeffs(preset: ViperPresetEntity, coeffs: List<ViperDdcCoeffEntity>) {
        updatePreset(preset)
        deleteCoeffsByPresetId(preset.id)
        if (coeffs.isNotEmpty()) {
            // Ensure coeffs have correct presetId
            val coeffsWithId = coeffs.map { it.copy(presetId = preset.id) }
            insertCoeffs(coeffsWithId)
        }
    }

    @Delete
    suspend fun deletePreset(preset: ViperPresetEntity)

    @Query("DELETE FROM viper_presets WHERE id = :id")
    suspend fun deletePresetById(id: Long)

    // Device-specific queries
    @Transaction
    @Query("SELECT * FROM viper_presets WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getPresetByDeviceId(deviceId: String): ViperPresetWithCoeffs?

    @Transaction
    @Query("SELECT * FROM viper_presets WHERE deviceId IS NULL")
    fun getDefaultPresets(): Flow<List<ViperPresetWithCoeffs>>

    @Transaction
    @Query("SELECT * FROM viper_presets WHERE deviceId IS NOT NULL")
    fun getDevicePresets(): Flow<List<ViperPresetWithCoeffs>>

    @Query("DELETE FROM viper_presets WHERE deviceId = :deviceId")
    suspend fun deletePresetsByDeviceId(deviceId: String)
}

