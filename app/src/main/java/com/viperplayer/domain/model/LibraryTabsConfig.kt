package com.viperplayer.domain.model

/**
 * One entry in a persisted library-tabs configuration: a tab identified by its stable [id] plus
 * whether it is [visible]. The [id] is a stable string key (the [LibraryTab]-style enum name), kept as
 * a plain [String] so this model has no dependency on the presentation-layer tab enum and stays a pure,
 * JVM-testable domain type.
 */
data class LibraryTabSetting(
    val id: String,
    val visible: Boolean,
)

/**
 * A user's library-tabs configuration: an ordered list of [LibraryTabSetting]. The order of [tabs] is
 * the order the tabs appear in the TabRow; each entry's [LibraryTabSetting.visible] flag hides/shows
 * that tab.
 *
 * This is the persisted shape. It is deliberately tolerant of drift between what was stored and the set
 * of tabs the app actually ships today — see [resolveVisibleTabIds], which reconciles a stored config
 * against the current full tab set (dropping removed ids, appending new ones, de-duping, guarding the
 * all-hidden case). Keeping that reconciliation in a pure function makes it the unit-test target.
 */
data class LibraryTabsConfig(
    val tabs: List<LibraryTabSetting>,
) {
    /**
     * Serialize to a compact, order-preserving string: `id:0|id:1|…` where the flag is `1` for visible
     * and `0` for hidden. Ids are enum-name-shaped (no delimiters), so a plain split round-trips.
     */
    fun serialize(): String =
        tabs.joinToString(separator = ENTRY_SEPARATOR) { setting ->
            "${setting.id}$FIELD_SEPARATOR${if (setting.visible) "1" else "0"}"
        }

    companion object {
        private const val ENTRY_SEPARATOR = "|"
        private const val FIELD_SEPARATOR = ":"

        /** The empty config (nothing stored yet). Callers treat this as "use defaults". */
        val EMPTY = LibraryTabsConfig(emptyList())

        /**
         * Parse a string produced by [serialize] back into a [LibraryTabsConfig]. Lenient: blank input
         * yields [EMPTY]; malformed entries (missing/garbled flag) are skipped rather than throwing, so
         * a corrupt or partially-written value degrades gracefully to the defaults path.
         */
        fun deserialize(raw: String?): LibraryTabsConfig {
            if (raw.isNullOrBlank()) return EMPTY
            val settings = raw.split(ENTRY_SEPARATOR).mapNotNull { entry ->
                val idx = entry.lastIndexOf(FIELD_SEPARATOR)
                if (idx <= 0 || idx == entry.length - 1) return@mapNotNull null
                val id = entry.substring(0, idx)
                val visible = when (entry.substring(idx + 1)) {
                    "1" -> true
                    "0" -> false
                    else -> return@mapNotNull null
                }
                LibraryTabSetting(id, visible)
            }
            return LibraryTabsConfig(settings)
        }

        /**
         * A default config for [allTabIds]: every tab in its natural order, all visible. This is the
         * behavior-preserving default so an un-customized library is unchanged.
         */
        fun default(allTabIds: List<String>): LibraryTabsConfig =
            LibraryTabsConfig(allTabIds.map { LibraryTabSetting(it, visible = true) })
    }
}

/**
 * Reconcile a stored [config] against the current [allTabIds] and return the FULL ordered list of
 * [LibraryTabSetting] — every shipped tab exactly once, in the configured order, carrying its stored
 * visibility (with newly-shipped tabs appended as visible). This is the shape the "Customize tabs"
 * editor operates on (it shows hidden tabs too, so it can un-hide them). Pure and Android-free.
 *
 * Same reconciliation rules as [resolveVisibleTabIds] minus the visible-only filter and the all-hidden
 * guard (the editor legitimately needs to render hidden tabs; the guard is applied at display time).
 *
 * @param allTabIds the canonical full set of tab ids the app ships, in their natural default order.
 * @param config the user's persisted configuration ([LibraryTabsConfig.EMPTY] = never customized).
 */
fun reconcileTabsConfig(
    allTabIds: List<String>,
    config: LibraryTabsConfig,
): List<LibraryTabSetting> {
    val known = allTabIds.toSet()
    val seen = HashSet<String>()
    val ordered = ArrayList<LibraryTabSetting>()
    for (setting in config.tabs) {
        if (setting.id in known && seen.add(setting.id)) {
            ordered += setting
        }
    }
    for (id in allTabIds) {
        if (seen.add(id)) {
            ordered += LibraryTabSetting(id, visible = true)
        }
    }
    return ordered
}

/**
 * Reconcile a stored [config] against the current [allTabIds] (the full set of tabs the app ships) and
 * return the ordered list of ids that should be VISIBLE, in the configured order. Pure and free of any
 * Android / presentation dependency, so it is the direct unit-test target.
 *
 * Rules, in order:
 * 1. **Dedupe** — if a stored config lists the same id twice, only the first occurrence is honored.
 * 2. **Drop unknown/removed ids** — entries whose id is not in [allTabIds] (e.g. a tab removed in a
 *    later app version) are ignored.
 * 3. **Append new tabs as visible** — any id in [allTabIds] not present in the stored config (e.g. a
 *    tab added in a later app version) is appended at the end, visible, preserving [allTabIds] order.
 * 4. **All-hidden guard** — if, after the above, no tab would be visible, fall back to showing the
 *    first tab of [allTabIds] so the Library is never left with an empty TabRow.
 *
 * @param allTabIds the canonical full set of tab ids the app ships, in their natural default order.
 * @param config the user's persisted configuration ([LibraryTabsConfig.EMPTY] = never customized).
 * @return the ordered ids of the tabs to display, never empty when [allTabIds] is non-empty.
 */
fun resolveVisibleTabIds(
    allTabIds: List<String>,
    config: LibraryTabsConfig,
): List<String> {
    val visible = reconcileTabsConfig(allTabIds, config).filter { it.visible }.map { it.id }

    // All-hidden guard: never leave the Library with no tabs. Fall back to the first default tab.
    return visible.ifEmpty { allTabIds.take(1) }
}
