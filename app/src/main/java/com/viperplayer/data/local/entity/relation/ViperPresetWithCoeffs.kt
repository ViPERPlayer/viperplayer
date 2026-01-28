package com.viperplayer.data.local.entity.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.viperplayer.data.local.entity.ViperDdcCoeffEntity
import com.viperplayer.data.local.entity.ViperPresetEntity

data class ViperPresetWithCoeffs(
    @Embedded val preset: ViperPresetEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "presetId"
    )
    val ddcCoeffs: List<ViperDdcCoeffEntity>
)
