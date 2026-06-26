package com.viperplayer.domain.model

/**
 * Domain-level filter for search. Mapped to the plugin SDK's media types at the data boundary.
 */
enum class SearchFilter {
    SONG,
    ALBUM,
    ARTIST,
    PLAYLIST,
}
