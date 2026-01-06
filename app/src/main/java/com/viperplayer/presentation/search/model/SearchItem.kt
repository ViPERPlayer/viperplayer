package com.viperplayer.presentation.search.model

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song

class SearchItem(
    val id: MediaId,
    val type: Type,
    val artworkUrl: String?,
    val title: String,
    val subtitle: String?,
    val isActive: Boolean,
    val badges: List<ItemBadge>,
    val song: Song? = null, // Store full Song object when type is SONG to avoid fetching it again
) {
    enum class Type {
        SONG,
        ALBUM,
        ARTIST,
        PLAYLIST,
    }
}