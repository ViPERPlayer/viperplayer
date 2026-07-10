package com.viperplayer.data.local.entity.converter

import androidx.room.TypeConverter
import com.viperplayer.domain.model.ArtistRef
import kotlinx.serialization.json.Json

/**
 * Persists a song's ordered byline ([ArtistRef] list) as a JSON blob in a single nullable column.
 *
 * Unlinked byline artists have no [ArtistRef.id] and therefore cannot be [com.viperplayer.data.local.entity.ArtistEntity]
 * rows, so the full ordered list is stored here verbatim. Linked refs are still additionally written
 * to `song_artists` cross-refs (for reverse "songs by this artist" lookups). A null column means a
 * pre-migration row whose byline must be rebuilt from cross-refs.
 */
object ArtistRefListConverter {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    @JvmStatic
    fun fromArtistRefList(refs: List<ArtistRef>?): String? =
        refs?.let { json.encodeToString(it) }

    @TypeConverter
    @JvmStatic
    fun toArtistRefList(value: String?): List<ArtistRef>? =
        value?.let { json.decodeFromString<List<ArtistRef>>(it) }
}
