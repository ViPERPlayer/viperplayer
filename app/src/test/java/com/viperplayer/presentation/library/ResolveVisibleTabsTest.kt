package com.viperplayer.presentation.library

import com.viperplayer.domain.model.LibraryTabSetting
import com.viperplayer.domain.model.LibraryTabsConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [resolveVisibleTabs] — the presentation-layer adapter that maps a persisted
 * [LibraryTabsConfig] to the ordered list of visible [LibraryTab] enum values the Library TabRow
 * renders. The reconciliation itself is covered by LibraryTabsConfigTest; these assert the enum mapping
 * and the "never empty" guarantee the screen relies on.
 */
class ResolveVisibleTabsTest {

    @Test
    fun emptyConfig_yieldsAllTabsInEnumOrder() {
        assertEquals(LibraryTab.entries.toList(), resolveVisibleTabs(LibraryTabsConfig.EMPTY))
    }

    @Test
    fun reorderedAndHidden_configMapsToEnumOrder() {
        val cfg = LibraryTabsConfig(
            listOf(
                LibraryTabSetting(LibraryTab.PLAYLISTS.name, true),
                LibraryTabSetting(LibraryTab.SONGS.name, false),
                LibraryTabSetting(LibraryTab.ARTISTS.name, true),
                LibraryTabSetting(LibraryTab.ALBUMS.name, true),
                LibraryTabSetting(LibraryTab.GENRES.name, false),
            )
        )

        // SONGS + GENRES hidden → dropped; the rest kept in the stored order.
        assertEquals(
            listOf(LibraryTab.PLAYLISTS, LibraryTab.ARTISTS, LibraryTab.ALBUMS),
            resolveVisibleTabs(cfg),
        )
    }

    @Test
    fun allHidden_neverEmpty_fallsBackToFirstTab() {
        val cfg = LibraryTabsConfig(
            LibraryTab.entries.map { LibraryTabSetting(it.name, visible = false) }
        )

        assertEquals(listOf(LibraryTab.SONGS), resolveVisibleTabs(cfg))
    }
}
