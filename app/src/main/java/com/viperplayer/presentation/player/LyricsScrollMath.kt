package com.viperplayer.presentation.player

/**
 * Pure, framework-free math for the lyrics renderer's motion polish. Everything here is expressed in
 * plain numbers (pixels, milliseconds, fractions) so it can be unit-tested on the JVM without Compose
 * or Android — the Composable in [PlayerLyrics] feeds it real `LazyListState` / layout measurements.
 */
object LyricsScrollMath {

    /**
     * The gap (in px) between the viewport top and the active line's top when the line is centered,
     * i.e. `viewportHeight/2 - itemHeight/2`. This is a **positive magnitude**, not a ready-to-pass
     * `scrollOffset`.
     *
     * `LazyListState.animateScrollToItem(index, scrollOffset)` scrolls so the item's top sits at
     * `viewportTop + scrollOffset`, where a *positive* `scrollOffset` moves the item further up/off the
     * top edge. To leave a gap of this magnitude above the line, the caller passes the **negated**
     * value (see `PlayerLyrics`: `offset = -centeredScrollOffsetPx(...)`). When the item is taller than
     * the viewport this returns 0, pinning the line's top to the viewport top so its start stays visible
     * rather than scrolling its middle off-screen.
     *
     * @param viewportHeightPx height of the scrollable lyrics area, in px.
     * @param itemHeightPx measured height of the active line's row, in px.
     * @return the top-gap magnitude to center the line; the caller negates it for `animateScrollToItem`.
     */
    fun centeredScrollOffsetPx(viewportHeightPx: Int, itemHeightPx: Int): Int {
        if (viewportHeightPx <= 0) return 0
        if (itemHeightPx >= viewportHeightPx) return 0
        return ((viewportHeightPx - itemHeightPx) / 2).coerceAtLeast(0)
    }

    /**
     * Progress fraction `0f..1f` through the active line's *current* word, used to smoothly fill a
     * word as it is sung rather than snapping it fully on at its start.
     *
     * The word runs from [wordStartMs] until [wordEndMs] (typically the next word's start, or the
     * line's end for the last word). Returns:
     *  - `0f` before the word has started ([positionMs] < [wordStartMs]),
     *  - `1f` at or after the word's end,
     *  - the linear fraction in between.
     *
     * Guards a zero- or negative-length word (start >= end): such a word is treated as instantaneous,
     * so it reports `1f` once its start has passed and `0f` before — never a divide-by-zero.
     */
    fun wordProgressFraction(positionMs: Long, wordStartMs: Long, wordEndMs: Long): Float {
        if (positionMs <= wordStartMs) return 0f
        if (wordEndMs <= wordStartMs) return 1f // zero/negative-length word: instantaneous
        if (positionMs >= wordEndMs) return 1f
        return (positionMs - wordStartMs).toFloat() / (wordEndMs - wordStartMs).toFloat()
    }

    /**
     * Whether auto-scroll should currently follow the active line, given that the user may have just
     * dragged the list manually. Auto-scroll is suppressed for [resumeDelayMs] after the last manual
     * interaction so the renderer does not yank the list back while the user is reading elsewhere; it
     * resumes automatically once the quiet period elapses.
     *
     * @param autoScrollEnabled the user's auto-scroll setting; when off, auto-scroll never runs.
     * @param lastUserInteractionMs timestamp (same clock as [nowMs]) of the last manual scroll, or a
     *   negative value when the user has never interacted.
     * @param nowMs the current time on the same clock.
     * @param resumeDelayMs how long to stay paused after a manual scroll.
     * @return true when the renderer should animate to the active line.
     */
    fun shouldAutoScroll(
        autoScrollEnabled: Boolean,
        lastUserInteractionMs: Long,
        nowMs: Long,
        resumeDelayMs: Long,
    ): Boolean {
        if (!autoScrollEnabled) return false
        if (lastUserInteractionMs < 0) return true
        return nowMs - lastUserInteractionMs >= resumeDelayMs
    }

    /** Default quiet period (ms) after a manual scroll before auto-scroll resumes. */
    const val AUTO_SCROLL_RESUME_DELAY_MS = 2500L
}
