package com.viperplayer.domain.model

/**
 * Search results containing multiple types organized in sections.
 */
data class SearchResult(
    val items: List<MediaItem> = emptyList(),
    val nextCursor: String? = null
) {
    val isEmpty: Boolean
        get() = items.isEmpty()

    val hasMore: Boolean
        get() = nextCursor != null
}


