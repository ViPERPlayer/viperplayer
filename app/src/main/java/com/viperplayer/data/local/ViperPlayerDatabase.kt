package com.viperplayer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.viperplayer.data.local.dao.AlbumDao
import com.viperplayer.data.local.dao.ArtistDao
import com.viperplayer.data.local.dao.CrossRefDao
import com.viperplayer.data.local.dao.GenreDao
import com.viperplayer.data.local.dao.PlayEventDao
import com.viperplayer.data.local.dao.OutboxDao
import com.viperplayer.data.local.dao.PlaylistDao
import com.viperplayer.data.local.dao.SearchHistoryDao
import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.dao.ViperPresetDao
import com.viperplayer.data.local.entity.AlbumArtistCrossRef
import com.viperplayer.data.local.entity.AlbumEntity
import com.viperplayer.data.local.entity.AlbumTypeConverter
import com.viperplayer.data.local.entity.ArtistEntity
import com.viperplayer.data.local.entity.ArtistGenreCrossRef
import com.viperplayer.data.local.entity.GenreEntity
import com.viperplayer.data.local.entity.OutboxEntity
import com.viperplayer.data.local.entity.PlayEventEntity
import com.viperplayer.data.local.entity.PlaylistEntity
import com.viperplayer.data.local.entity.PlaylistSongCrossRef
import com.viperplayer.data.local.entity.QueueSongCrossRef
import com.viperplayer.data.local.entity.SearchHistoryEntity
import com.viperplayer.data.local.entity.SongArtistCrossRef
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.entity.ViperDdcCoeffEntity
import com.viperplayer.data.local.entity.ViperPresetEntity
import com.viperplayer.data.local.entity.converter.DynamicSystemDeviceTypeConverter

/**
 * Room database for ViperPlayer.
 */
@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        SongEntity::class,
        PlaylistEntity::class,
        GenreEntity::class,
        SongArtistCrossRef::class,
        AlbumArtistCrossRef::class,
        PlaylistSongCrossRef::class,
        ArtistGenreCrossRef::class,
        SearchHistoryEntity::class,
        QueueSongCrossRef::class,
        ViperPresetEntity::class,
        ViperDdcCoeffEntity::class,
        PlayEventEntity::class,
        OutboxEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(
    AlbumTypeConverter::class,
    DynamicSystemDeviceTypeConverter::class
)
abstract class ViperPlayerDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun genreDao(): GenreDao
    abstract fun crossRefDao(): CrossRefDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun viperPresetDao(): ViperPresetDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun outboxDao(): OutboxDao
}

