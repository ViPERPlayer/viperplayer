package com.viperplayer.presentation.player

/**
 * Android-free pure logic for the now-playing player UI. Everything the player's gestures and
 * indicators depend on that can be reasoned about without Compose or a MediaController lives here so
 * it can be unit-tested on the JVM. The Composables call these; the ViewModel/MediaController only
 * gets the resulting index math / commands.
 */
object PlayerQueueLogic {

    /**
     * Result of removing [removeIndex] from a queue whose now-playing item is at [currentIndex].
     * [newCurrentIndex] is where the now-playing item lands after the removal (it never goes below
     * 0, and clamps to the last remaining item if the current track itself was removed and was last),
     * or null when the queue becomes empty. [currentRemoved] is true when the removed row *was* the
     * now-playing track (the caller may want to advance playback in that case).
     */
    data class RemovalResult(val newCurrentIndex: Int?, val currentRemoved: Boolean)

    /**
     * Computes how removing [removeIndex] shifts the now-playing index. Pure index math shared by the
     * queue sheet so the highlight and playback intent stay consistent regardless of which row the
     * user swipes away. Out-of-range [removeIndex] is a no-op (returns the unchanged current index).
     */
    fun removalResult(currentIndex: Int, removeIndex: Int, size: Int): RemovalResult {
        if (removeIndex !in 0 until size) {
            return RemovalResult(currentIndex.takeIf { size > 0 }, currentRemoved = false)
        }
        val newSize = size - 1
        if (newSize == 0) return RemovalResult(null, currentRemoved = removeIndex == currentIndex)
        val currentRemoved = removeIndex == currentIndex
        val newCurrent = when {
            // Removed a row before the current one: current shifts up by one.
            removeIndex < currentIndex -> currentIndex - 1
            // Removed the current row: stay at the same slot, which now holds the next track,
            // clamping when the removed track was the last item.
            currentRemoved -> currentIndex.coerceAtMost(newSize - 1)
            // Removed a row after the current one: current index is unchanged.
            else -> currentIndex
        }
        return RemovalResult(newCurrent, currentRemoved)
    }

    /**
     * Moves the now-playing highlight to track a drag-reorder from [fromIndex] to [toIndex]. When the
     * dragged row *is* the current track, the highlight follows it to [toIndex]. Otherwise the
     * highlight stays on the same song, whose index shifts if the moved row crossed over it. Pure
     * counterpart to the MediaController's atomic move(from, to). Out-of-range indices are a no-op.
     */
    fun currentIndexAfterMove(currentIndex: Int, fromIndex: Int, toIndex: Int, size: Int): Int {
        if (fromIndex == toIndex) return currentIndex
        if (fromIndex !in 0 until size || toIndex !in 0 until size) return currentIndex
        return when {
            fromIndex == currentIndex -> toIndex
            // The current track sits between the drag's endpoints and gets shifted by the move.
            fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex - 1
            fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
    }

    /**
     * Applies a move on an in-memory list (the queue sheet's live working copy), returning a new list
     * with the element at [from] reinserted at [to]. Out-of-range indices return the list unchanged.
     */
    fun <T> List<T>.movedItem(from: Int, to: Int): List<T> {
        if (from !in indices || to !in indices || from == to) return this
        return toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Stable, unique keys for a queue that may contain the same track more than once (e.g. after
     * "duplicate in queue" or adding a track twice). A LazyColumn / reorderable list requires each
     * item's key to be unique *and* to travel with the item as it is reordered — so a bare MediaId
     * string is unsafe. This disambiguates repeats by appending a per-id occurrence counter, so the
     * first "A" is `A#0`, the second `A#1`, and the pairing survives any reorder of the list.
     */
    fun <T> queueKeys(items: List<T>, id: (T) -> String): List<String> {
        val seen = HashMap<String, Int>()
        return items.map { item ->
            val base = id(item)
            val occurrence = seen.getOrDefault(base, 0)
            seen[base] = occurrence + 1
            "$base#$occurrence"
        }
    }

    /**
     * The fraction [0f, 1f] of a track that a horizontal position on the seek bar corresponds to.
     * [x] and [width] are in the same units (pixels); a non-positive width yields 0. Used by both the
     * tap-to-seek and drag-to-scrub paths so they agree exactly.
     */
    fun seekFraction(x: Float, width: Float): Float {
        if (width <= 0f) return 0f
        return (x / width).coerceIn(0f, 1f)
    }

    /** Converts a [0f,1f] seek fraction to a position in ms for a track of [durationMs]. */
    fun fractionToPositionMs(fraction: Float, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        return (fraction.coerceIn(0f, 1f) * durationMs).toLong()
    }

    /**
     * The played fraction [0f,1f] of a track, guarding a zero/unknown duration. Shared by the seek
     * bar so the thumb and the buffered indicator both derive from the same rounding.
     */
    fun progressFraction(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }
}
