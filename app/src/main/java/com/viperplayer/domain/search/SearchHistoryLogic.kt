package com.viperplayer.domain.search

/**
 * Pure, Android-free logic for maintaining a recent-search list. Kept out of the DAO/repository so
 * the ordering / dedupe / cap / normalization rules are unit-testable without Room or coroutines.
 *
 * This object is the canonical spec for those rules. On-device the reactive path is served by
 * SQL for a live [kotlinx.coroutines.flow.Flow] (see `SearchHistoryDao.upsertCapped` /
 * `getRecentHistory`), which mirrors the same rules; [normalize] and [MAX_HISTORY] are shared by
 * both paths so the persisted spelling and cap can never diverge.
 *
 * Rules:
 * - Most-recent-first ordering.
 * - Blank (empty / whitespace-only) queries are ignored.
 * - Dedupe is case-insensitive AND whitespace-normalized: re-searching an existing entry (in any
 *   case, with extra surrounding/inner spacing) moves it to the front and keeps the newest spelling
 *   rather than adding a duplicate.
 * - The list is capped at [MAX_HISTORY]; the oldest entries fall off the end.
 */
object SearchHistoryLogic {

    /** Maximum number of recent searches retained. */
    const val MAX_HISTORY: Int = 50

    /**
     * Normalizes a raw query for storage and display: trims and collapses internal runs of
     * whitespace to a single space. Returns the cleaned query (may be blank if the input was
     * blank). This is the spelling that gets persisted.
     */
    fun normalize(query: String): String =
        query.trim().replace(WHITESPACE_RUN, " ")

    /**
     * Returns the canonical key used to decide whether two queries are "the same" entry:
     * normalized + lowercased. Never persisted — only used for dedupe comparisons.
     */
    fun dedupeKey(query: String): String =
        normalize(query).lowercase()

    /** True if [query] is blank once normalized (and therefore should not be stored). */
    fun isBlank(query: String): Boolean = normalize(query).isEmpty()

    /**
     * Adds [rawQuery] to [current] following the recent-search rules and returns the new list.
     * The input list is treated as most-recent-first. Blank queries return the list unchanged.
     *
     * @param cap maximum retained entries (defaults to [MAX_HISTORY]).
     */
    fun add(
        current: List<String>,
        rawQuery: String,
        cap: Int = MAX_HISTORY,
    ): List<String> {
        val normalized = normalize(rawQuery)
        if (normalized.isEmpty()) return current

        val key = normalized.lowercase()
        val deduped = current.filterNot { dedupeKey(it) == key }
        val updated = ArrayList<String>(deduped.size + 1)
        updated.add(normalized)
        updated.addAll(deduped)
        return if (updated.size > cap) updated.subList(0, cap).toList() else updated
    }

    /**
     * Removes every entry matching [rawQuery] case-insensitively (after normalization) from
     * [current], preserving order of the rest.
     */
    fun remove(current: List<String>, rawQuery: String): List<String> {
        val key = dedupeKey(rawQuery)
        if (key.isEmpty()) return current
        return current.filterNot { dedupeKey(it) == key }
    }

    /** Clears the entire history. */
    fun clear(): List<String> = emptyList()

    private val WHITESPACE_RUN = Regex("\\s+")
}
