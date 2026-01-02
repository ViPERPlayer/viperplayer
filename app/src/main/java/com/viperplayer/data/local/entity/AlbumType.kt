package com.viperplayer.data.local.entity

import androidx.room.TypeConverter
import com.viperplayer.domain.model.AlbumType as DomainAlbumType

/**
 * Room type converter for AlbumType.
 */
object AlbumTypeConverter {
    @TypeConverter
    @JvmStatic
    fun fromDomainType(type: DomainAlbumType): AlbumType = when (type) {
        DomainAlbumType.ALBUM -> AlbumType.ALBUM
        DomainAlbumType.SINGLE -> AlbumType.SINGLE
        DomainAlbumType.EP -> AlbumType.EP
        DomainAlbumType.COMPILATION -> AlbumType.COMPILATION
    }

    @TypeConverter
    @JvmStatic
    fun toDomainType(type: AlbumType): DomainAlbumType = when (type) {
        AlbumType.ALBUM -> DomainAlbumType.ALBUM
        AlbumType.SINGLE -> DomainAlbumType.SINGLE
        AlbumType.EP -> DomainAlbumType.EP
        AlbumType.COMPILATION -> DomainAlbumType.COMPILATION
    }
}

/**
 * Room enum for AlbumType.
 */
enum class AlbumType {
    ALBUM, SINGLE, EP, COMPILATION
}

