package com.viperplayer.domain.model

/**
 * A page of items with a cursor to the next page. Domain-owned (no plugin-SDK dependency) so the
 * rest of the app doesn't couple to the wire types.
 */
data class PagedResult<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val totalCount: Int? = null,
) {
    val hasMore: Boolean get() = nextCursor != null
    val isEmpty: Boolean get() = items.isEmpty()
    val size: Int get() = items.size

    fun <R> map(transform: (T) -> R): PagedResult<R> =
        PagedResult(items.map(transform), nextCursor, totalCount)

    companion object {
        fun <T> empty(): PagedResult<T> = PagedResult(emptyList())
    }
}
