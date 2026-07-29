package com.viperplayer.domain.repository

import com.viperplayer.domain.model.Daylist

/**
 * Produces the current **daylist** for the active time-of-day slot, fully on-device.
 *
 * The daylist is derived from the user's per-bucket taste + library audio embeddings (the same kNN
 * machinery behind "For You"), given a generated descriptive title/description. It is CACHED per
 * `(bucket, epochDay)` slot so the same slot returns the same daylist within its window, and REGENERATED
 * when the time-of-day bucket rolls over. No login, no network.
 *
 * Like the rest of the recommender, this degrades gracefully — [currentDaylist] returns `null` (rather
 * than throwing) whenever a daylist can't be built (recommendations disabled, cold taste, or a library
 * that isn't indexed yet), so the Home surface simply hides itself with no nag.
 */
interface DaylistRepository {

    /**
     * The daylist for the CURRENT time-of-day slot, or `null` when one can't be produced (smart
     * recommendations off, no warm bucket taste yet, or an unindexed library). Cheap on repeat calls
     * within the same slot (cached); regenerates when the bucket/day rolls over. Never throws.
     */
    suspend fun currentDaylist(): Daylist?
}
