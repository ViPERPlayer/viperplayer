package com.viperplayer.domain.model

/**
 * A section of search results.
 */
data class SearchSection(
    val type: SearchSectionType,
    val items: List<SearchSectionItem> = emptyList()
)

/**
 * Type of search result section.
 */
enum class SearchSectionType {
    TOP_RESULT,
    OTHER
}

/**
 * An individual search result item (song, artist, album, or playlist).
 */
data class SearchSectionItem(
    val type: SearchSectionItemType,
    val song: Song? = null,
    val artist: Artist? = null,
    val album: Album? = null,
    val playlist: Playlist? = null
)

/**
 * Type of search section item.
 */
enum class SearchSectionItemType {
    SONG, ARTIST, ALBUM, PLAYLIST
}

