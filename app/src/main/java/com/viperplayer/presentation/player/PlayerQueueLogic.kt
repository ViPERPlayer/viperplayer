package com.viperplayer.presentation.player

import kotlin.math.PI
import kotlin.math.sin

/**
 * Android-free pure logic for the now-playing player UI. Everything the player's gestures and
 * indicators depend on that can be reasoned about without Compose or a MediaController lives here so
 * it can be unit-tested on the JVM. The Composables call these; the ViewModel/MediaController only
 * gets the resulting index math / commands.
 */
object PlayerQueueLogic {

    // --- Playback speed / pitch (issue #8) ---

    // Two distinct ranges, single-sourced here so production doesn't drift from the unit-tested math:
    //   * The ENGINE range ([MIN_SPEED]..[MAX_SPEED] / [MIN_PITCH]..[MAX_PITCH]) is the hard bound the
    //     controller clamps to — what ExoPlayer's Sonic tempo/pitch stretcher accepts safely.
    //   * The UI range ([SPEED_UI_RANGE] / [PITCH_UI_RANGE]) is what the dialog sliders expose. It is
    //     intentionally narrower than the engine range (the extreme tempos/pitches are unpleasant),
    //     so a slider value is always within the engine bound and [clampSpeed]/[clampPitch] are a
    //     no-op for it; the clamps still guard any programmatic/out-of-band request.

    /** Inclusive tempo range the engine (ExoPlayer/Sonic) accepts; the controller clamps to it. */
    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 3f

    /** Inclusive pitch range the engine accepts. */
    const val MIN_PITCH = 0.5f
    const val MAX_PITCH = 2f

    /** The (narrower) tempo range the speed-dialog slider exposes to the user. */
    val SPEED_UI_RANGE = 0.5f..2f

    /** The (narrower) pitch range the speed-dialog slider exposes to the user. */
    val PITCH_UI_RANGE = 0.5f..2f

    /** Clamp a requested tempo to the engine-safe [MIN_SPEED]..[MAX_SPEED] range. */
    fun clampSpeed(speed: Float): Float = speed.coerceIn(MIN_SPEED, MAX_SPEED)

    /** Clamp a requested pitch to the engine-safe [MIN_PITCH]..[MAX_PITCH] range. */
    fun clampPitch(pitch: Float): Float = pitch.coerceIn(MIN_PITCH, MAX_PITCH)

    // --- Song radio queue building (issue #7) ---

    /**
     * Builds the song-radio queue from a [seedId] and its [relatedIds]: the seed always comes first,
     * followed by the related items with the seed removed and any duplicate ids de-duplicated (first
     * occurrence wins), preserving related order. Pure id math so radio-queue construction can be
     * unit-tested independently of Song hydration and the plugin fetch.
     */
    fun <ID> radioQueueIds(seedId: ID, relatedIds: List<ID>): List<ID> {
        val result = ArrayList<ID>(relatedIds.size + 1)
        val seen = HashSet<ID>()
        result.add(seedId)
        seen.add(seedId)
        for (id in relatedIds) {
            if (seen.add(id)) result.add(id)
        }
        return result
    }

    // --- Wavy seek bar geometry (issue #10) ---

    /**
     * The vertical offset (in the seek bar's Y units) of the played-portion wave at horizontal
     * position [x], for a sine wave of the given [wavelength] and [amplitude] shifted by [phase]
     * radians. A zero/negative [amplitude] or [wavelength] yields a flat line (0), so a paused /
     * reduced-motion bar reduces to a straight segment through the same helper. Pure so the wave's
     * shape can be unit-tested without a Canvas.
     */
    fun waveOffset(x: Float, wavelength: Float, amplitude: Float, phase: Float): Float {
        if (amplitude <= 0f || wavelength <= 0f) return 0f
        val angle = (x / wavelength).toDouble() * 2.0 * PI + phase
        return amplitude * sin(angle).toFloat()
    }

    /**
     * The target wave amplitude fraction [0f,1f] for the played portion: full waviness only while the
     * track is actively playing, the user is not dragging, and motion is not disabled (reduced-motion
     * / animator scale 0). Otherwise flat (0). The caller animates toward this so the wave settles
     * smoothly rather than snapping.
     */
    fun waveAmplitudeTarget(isPlaying: Boolean, isDragging: Boolean, motionEnabled: Boolean): Float =
        if (isPlaying && !isDragging && motionEnabled) 1f else 0f

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
