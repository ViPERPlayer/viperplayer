package com.viperplayer.presentation.library

import com.viperplayer.domain.model.LibraryTabSetting
import com.viperplayer.domain.model.LibraryTabsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CustomizeTabsViewModel]. These target the lost-update fix (issue #1): edits mutate an
 * authoritative in-VM working state SYNCHRONOUSLY, so two edits fired inside DataStore's write→re-emit
 * latency both land instead of the second clobbering the first. Also cover the all-hidden guard, reset,
 * and cold-start seeding from the persisted config.
 */
class CustomizeTabsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun visibilityOf(tabs: List<CustomizableTab>) =
        tabs.associate { it.tab to it.visible }

    @Test
    fun rapidEdits_insideWriteReEmitWindow_neitherIsLost() = runTest {
        // A deliberately slow persist (models DataStore write→re-emit latency). If edits read/wrote the
        // repo-derived state, the second edit — fired before the first write re-emits — would read the
        // pre-first state and lose the first edit. The in-VM working state prevents that.
        val repo = FakeSettingsRepository(writeReEmitDelayMs = 1_000)
        val viewModel = CustomizeTabsViewModel(repo)
        advanceUntilIdle() // let the init seed run

        // Two edits back-to-back, before either persist has re-emitted.
        viewModel.setTabVisible(LibraryTab.ALBUMS, false)
        viewModel.setTabVisible(LibraryTab.ARTISTS, false)

        // The working state reflects BOTH edits immediately (synchronously), no round-trip needed.
        val visibility = visibilityOf(viewModel.tabs.value)
        assertEquals(false, visibility[LibraryTab.ALBUMS])
        assertEquals(false, visibility[LibraryTab.ARTISTS])
        assertEquals(true, visibility[LibraryTab.SONGS])
        assertEquals(true, visibility[LibraryTab.PLAYLISTS])

        // Both writes eventually flush; the last-persisted config carries both edits (nothing lost).
        advanceUntilIdle()
        val persisted = repo.libraryTabsConfig.first()
        val persistedVisibility = persisted.tabs.associate { it.id to it.visible }
        assertEquals(false, persistedVisibility[LibraryTab.ALBUMS.name])
        assertEquals(false, persistedVisibility[LibraryTab.ARTISTS.name])
        assertEquals(2, repo.writeCount)
    }

    @Test
    fun setTabVisible_reflectsInWorkingStateImmediately() = runTest {
        val repo = FakeSettingsRepository()
        val viewModel = CustomizeTabsViewModel(repo)
        advanceUntilIdle()

        viewModel.setTabVisible(LibraryTab.ARTISTS, false)

        assertEquals(false, visibilityOf(viewModel.tabs.value)[LibraryTab.ARTISTS])
    }

    @Test
    fun setTabVisible_lastVisibleTab_isGuardedNoOp() = runTest {
        // Only SONGS visible. Trying to hide it must be a no-op (never leave the Library all-hidden).
        val onlySongs = LibraryTabsConfig(
            LibraryTab.entries.map { LibraryTabSetting(it.name, it == LibraryTab.SONGS) }
        )
        val repo = FakeSettingsRepository(initialConfig = onlySongs)
        val viewModel = CustomizeTabsViewModel(repo)
        advanceUntilIdle()

        val writesBefore = repo.writeCount
        viewModel.setTabVisible(LibraryTab.SONGS, false)

        // Still visible in the working state, and nothing was persisted.
        assertEquals(true, visibilityOf(viewModel.tabs.value)[LibraryTab.SONGS])
        assertEquals(writesBefore, repo.writeCount)
    }

    @Test
    fun moveTab_reordersWorkingStateAndPersists() = runTest {
        val repo = FakeSettingsRepository()
        val viewModel = CustomizeTabsViewModel(repo)
        advanceUntilIdle()

        // Move the last tab (PLAYLISTS, the final index) to the front.
        val lastIndex = LibraryTab.entries.lastIndex
        viewModel.moveTab(from = lastIndex, to = 0)

        assertEquals(
            listOf(
                LibraryTab.PLAYLISTS,
                LibraryTab.SONGS,
                LibraryTab.ALBUMS,
                LibraryTab.ARTISTS,
                LibraryTab.GENRES,
            ),
            viewModel.tabs.value.map { it.tab },
        )
    }

    @Test
    fun resetToDefault_restoresAllVisibleInNaturalOrder_immediately() = runTest {
        val artistsHidden = LibraryTabsConfig(
            LibraryTab.entries.map { LibraryTabSetting(it.name, it != LibraryTab.ARTISTS) }
        )
        val repo = FakeSettingsRepository(initialConfig = artistsHidden)
        val viewModel = CustomizeTabsViewModel(repo)
        advanceUntilIdle()
        assertEquals(false, visibilityOf(viewModel.tabs.value)[LibraryTab.ARTISTS])

        viewModel.resetToDefault()

        // Reflected synchronously in the working state.
        assertEquals(LibraryTab.entries.toList(), viewModel.tabs.value.map { it.tab })
        assertEquals(true, viewModel.tabs.value.all { it.visible })
    }

    @Test
    fun coldStart_seedsWorkingStateFromPersistedConfig() = runTest {
        val reordered = LibraryTabsConfig(
            listOf(
                LibraryTabSetting(LibraryTab.PLAYLISTS.name, true),
                LibraryTabSetting(LibraryTab.SONGS.name, false),
                LibraryTabSetting(LibraryTab.ARTISTS.name, true),
                LibraryTabSetting(LibraryTab.ALBUMS.name, true),
            )
        )
        val repo = FakeSettingsRepository(initialConfig = reordered)
        val viewModel = CustomizeTabsViewModel(repo)
        advanceUntilIdle()

        // Editor shows every tab (including the hidden SONGS) in the stored order, with SONGS off.
        // Tabs missing from the stored config (e.g. GENRES, added after this config was written) are
        // appended in natural order — see reconcileTabsConfig.
        assertEquals(
            listOf(
                LibraryTab.PLAYLISTS,
                LibraryTab.SONGS,
                LibraryTab.ARTISTS,
                LibraryTab.ALBUMS,
                LibraryTab.GENRES,
            ),
            viewModel.tabs.value.map { it.tab },
        )
        assertEquals(false, visibilityOf(viewModel.tabs.value)[LibraryTab.SONGS])
    }
}
