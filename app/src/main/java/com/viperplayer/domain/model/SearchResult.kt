package com.viperplayer.domain.model

/**
 * Search results containing multiple types organized in sections.
 */
data class SearchResult(
    val sections: List<SearchSection> = emptyList(),
    val nextCursor: String? = null
) {
    val isEmpty: Boolean
        get() = sections.isEmpty()

    val hasMore: Boolean
        get() = nextCursor != null
}


