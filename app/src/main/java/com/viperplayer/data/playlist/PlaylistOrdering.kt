package com.viperplayer.data.playlist

/**
 * Pure ordering helpers for local-playlist edits. Kept free of Android/Room types so the
 * position-management logic behind add / remove / reorder can be unit-tested directly and reused by
 * [com.viperplayer.data.repository.MediaLibraryRepositoryImpl].
 */
object PlaylistOrdering {

    /** The position a newly-appended song should take, given the current max position (or null). */
    fun nextPosition(currentMaxPosition: Int?): Int = (currentMaxPosition ?: -1) + 1

    /**
     * Move the item at [fromIndex] to [toIndex] within [items], returning the reordered list. Returns
     * the list unchanged when either index is out of range or the move is a no-op.
     */
    fun <T> move(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
        if (fromIndex == toIndex) return items
        if (fromIndex !in items.indices || toIndex !in items.indices) return items
        return items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }
}
