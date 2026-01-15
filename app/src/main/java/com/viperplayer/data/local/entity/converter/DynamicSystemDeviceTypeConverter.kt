package com.viperplayer.data.local.entity.converter

import androidx.room.TypeConverter
import com.viperplayer.domain.model.DynamicSystemDeviceType

/**
 * Type converter for DynamicSystemDeviceType enum.
 */
class DynamicSystemDeviceTypeConverter {
    @TypeConverter
    fun fromDeviceType(deviceType: DynamicSystemDeviceType): String {
        return deviceType.name
    }

    @TypeConverter
    fun toDeviceType(name: String): DynamicSystemDeviceType {
        return try {
            DynamicSystemDeviceType.valueOf(name)
        } catch (e: IllegalArgumentException) {
            DynamicSystemDeviceType.EXTREME_HEADPHONE_V2
        }
    }
}

