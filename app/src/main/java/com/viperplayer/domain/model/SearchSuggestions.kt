package com.viperplayer.domain.model

/**
 * Search suggestions from a plugin.
 */
data class SearchSuggestions(
    val pluginId: String,
    val suggestions: List<String> = emptyList(),
    val items: List<SearchSuggestionItem> = emptyList()
)

/**
 * A single search suggestion item (song, artist, album, or playlist).
 */
data class SearchSuggestionItem(
    val type: SearchSuggestionType,
    val song: Song? = null,
    val artist: Artist? = null,
    val album: Album? = null,
    val playlist: Playlist? = null
)

/**
 * Type of search suggestion item.
 */
enum class SearchSuggestionType {
    SONG, ARTIST, ALBUM, PLAYLIST
}

