package com.viperplayer.presentation.search.model

import com.viperplayer.domain.model.MediaId

class SearchItem(
    val id: MediaId,
    val type: Type,
    val artworkUrl: String?,
    val title: String,
    val subtitle: String?,
    val isActive: Boolean,
    val badges: List<ItemBadge>,
) {
    enum class Type {
        SONG,
        ALBUM,
        ARTIST,
        PLAYLIST,
    }
}