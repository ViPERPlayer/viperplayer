package com.viperplayer.presentation.search.model

class SearchItem(
    val id: String,
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