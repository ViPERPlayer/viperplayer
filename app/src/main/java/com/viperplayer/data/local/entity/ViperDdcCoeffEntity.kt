package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity to store individual DDC coefficients.
 * Relates to ViperPresetEntity via presetId.
 */
@Entity(
    tableName = "viper_ddc_coeffs",
    foreignKeys = [
        ForeignKey(
            entity = ViperPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("presetId")]
)
data class ViperDdcCoeffEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val presetId: Long,
    val sampleRate: Int,
    val index: Int,
    val value: Float
)
