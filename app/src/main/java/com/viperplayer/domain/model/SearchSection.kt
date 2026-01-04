package com.viperplayer.domain.model

/**
 * A section of search results.
 */
data class SearchSection(
    val type: SearchSectionType,
    val items: List<MediaItem> = emptyList()
)

/**
 * Type of search result section.
 */
enum class SearchSectionType {
    TOP_RESULT,
    OTHER
}
