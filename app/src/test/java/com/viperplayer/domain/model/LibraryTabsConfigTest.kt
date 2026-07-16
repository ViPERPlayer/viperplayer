package com.viperplayer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure library-tabs config model: the [resolveVisibleTabIds] /
 * [reconcileTabsConfig] reconciliation and the [LibraryTabsConfig] serialization round-trip.
 *
 * These are the primary unit-test target for the reorderable/toggleable tabs feature — the whole point
 * of the design is that all the fiddly reconciliation (dedupe, drop-unknown, append-new, all-hidden
 * guard, order) lives in Android-free pure functions that can be exercised directly here.
 */
class LibraryTabsConfigTest {

    // The canonical shipped tab set used across the tests (mirrors LibraryTab.ALL_IDS but kept literal
    // so the tests don't depend on the presentation enum).
    private val allTabs = listOf("SONGS", "ALBUMS", "ARTISTS", "PLAYLISTS")

    private fun config(vararg entries: Pair<String, Boolean>) =
        LibraryTabsConfig(entries.map { LibraryTabSetting(it.first, it.second) })

    @Test
    fun default_emptyConfig_allTabsVisibleInOriginalOrder() {
        // No stored config → every tab visible, in the natural default order (behavior unchanged).
        val visible = resolveVisibleTabIds(allTabs, LibraryTabsConfig.EMPTY)

        assertEquals(allTabs, visible)
    }

    @Test
    fun default_helperProducesAllVisibleInOrder() {
        val default = LibraryTabsConfig.default(allTabs)

        assertEquals(
            allTabs.map { LibraryTabSetting(it, visible = true) },
            default.tabs,
        )
        assertEquals(allTabs, resolveVisibleTabIds(allTabs, default))
    }

    @Test
    fun reordered_configApplied_inStoredOrder() {
        // A user-reordered config (all visible) is honored exactly.
        val reordered = config(
            "PLAYLISTS" to true,
            "SONGS" to true,
            "ARTISTS" to true,
            "ALBUMS" to true,
        )

        assertEquals(
            listOf("PLAYLISTS", "SONGS", "ARTISTS", "ALBUMS"),
            resolveVisibleTabIds(allTabs, reordered),
        )
    }

    @Test
    fun hiddenTab_isOmittedFromVisibleList() {
        // ARTISTS hidden → dropped from the visible list, order of the rest preserved.
        val cfg = config(
            "SONGS" to true,
            "ALBUMS" to true,
            "ARTISTS" to false,
            "PLAYLISTS" to true,
        )

        assertEquals(
            listOf("SONGS", "ALBUMS", "PLAYLISTS"),
            resolveVisibleTabIds(allTabs, cfg),
        )
    }

    @Test
    fun newTab_absentFromStoredConfig_isAppendedVisible() {
        // Stored config predates the ARTISTS + PLAYLISTS tabs; they must be appended, visible, in the
        // canonical order, after the stored entries.
        val cfg = config(
            "ALBUMS" to true,
            "SONGS" to false,
        )

        assertEquals(
            // ALBUMS (stored, visible), SONGS is hidden so dropped, then the new tabs appended visible.
            listOf("ALBUMS", "ARTISTS", "PLAYLISTS"),
            resolveVisibleTabIds(allTabs, cfg),
        )
        // The full reconcile keeps the hidden SONGS entry (editor needs it) in the right place.
        assertEquals(
            listOf(
                LibraryTabSetting("ALBUMS", true),
                LibraryTabSetting("SONGS", false),
                LibraryTabSetting("ARTISTS", true),
                LibraryTabSetting("PLAYLISTS", true),
            ),
            reconcileTabsConfig(allTabs, cfg),
        )
    }

    @Test
    fun unknownId_inStoredConfig_isDropped() {
        // "FOLDERS" is not a tab the app ships (e.g. removed in a later version) → ignored entirely.
        val cfg = config(
            "SONGS" to true,
            "FOLDERS" to true,
            "ALBUMS" to true,
        )

        val visible = resolveVisibleTabIds(allTabs, cfg)

        assertEquals(false, visible.contains("FOLDERS"))
        // SONGS + ALBUMS honored in order, then the still-missing ARTISTS/PLAYLISTS appended visible.
        assertEquals(listOf("SONGS", "ALBUMS", "ARTISTS", "PLAYLISTS"), visible)
    }

    @Test
    fun allHidden_guarded_fallsBackToFirstTab() {
        // Every tab hidden must never leave the Library empty — the first default tab stays visible.
        val cfg = config(
            "SONGS" to false,
            "ALBUMS" to false,
            "ARTISTS" to false,
            "PLAYLISTS" to false,
        )

        assertEquals(listOf("SONGS"), resolveVisibleTabIds(allTabs, cfg))
    }

    @Test
    fun duplicateIds_deduped_firstWins() {
        // A malformed config listing SONGS twice (visible then hidden) honors only the first occurrence.
        val cfg = config(
            "SONGS" to true,
            "ALBUMS" to true,
            "SONGS" to false,
        )

        val reconciled = reconcileTabsConfig(allTabs, cfg)

        // SONGS appears exactly once, using the FIRST (visible) entry.
        assertEquals(1, reconciled.count { it.id == "SONGS" })
        assertEquals(LibraryTabSetting("SONGS", true), reconciled.first { it.id == "SONGS" })
        assertEquals(listOf("SONGS", "ALBUMS", "ARTISTS", "PLAYLISTS"), resolveVisibleTabIds(allTabs, cfg))
    }

    @Test
    fun serialization_roundTrips() {
        val original = config(
            "PLAYLISTS" to true,
            "SONGS" to false,
            "ARTISTS" to true,
            "ALBUMS" to false,
        )

        val roundTripped = LibraryTabsConfig.deserialize(original.serialize())

        assertEquals(original, roundTripped)
    }

    @Test
    fun deserialize_blankOrNull_isEmpty() {
        assertEquals(LibraryTabsConfig.EMPTY, LibraryTabsConfig.deserialize(null))
        assertEquals(LibraryTabsConfig.EMPTY, LibraryTabsConfig.deserialize(""))
        assertEquals(LibraryTabsConfig.EMPTY, LibraryTabsConfig.deserialize("   "))
    }

    @Test
    fun deserialize_skipsMalformedEntries() {
        // A garbled/partial entry ("ALBUMS" with no flag, "ARTISTS:x" bad flag) is skipped; the valid
        // ones survive. This keeps a corrupt persisted value from crashing — it degrades to what parses.
        val cfg = LibraryTabsConfig.deserialize("SONGS:1|ALBUMS|ARTISTS:x|PLAYLISTS:0")

        assertEquals(
            listOf(
                LibraryTabSetting("SONGS", true),
                LibraryTabSetting("PLAYLISTS", false),
            ),
            cfg.tabs,
        )
    }

    @Test
    fun emptyAllTabs_returnsEmpty() {
        // Degenerate guard: no shipped tabs → nothing to show (and no crash on take(1)).
        assertEquals(emptyList<String>(), resolveVisibleTabIds(emptyList(), LibraryTabsConfig.EMPTY))
    }
}
