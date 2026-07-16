package com.viperplayer.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for [deriveReplayGainMode], which resolves the persisted ReplayGain mode while
 * preserving back-compat with the legacy `replay_gain_album_mode` boolean.
 *
 * The key regression this guards against: an existing user who never enabled album mode (new key
 * absent, legacy boolean == false) must stay on per-track normalization and NOT be silently flipped
 * to SMART on upgrade. SMART is the default only for fresh installs (both keys never written).
 */
class ReplayGainModeDerivationTest {

    // --- explicit mode wins -------------------------------------------------

    @Test
    fun explicitMode_wins_overLegacyAlbumModeTrue() {
        assertEquals(
            ReplayGainMode.TRACK,
            deriveReplayGainMode(explicit = ReplayGainMode.TRACK, legacyAlbumMode = true)
        )
    }

    @Test
    fun explicitMode_wins_overLegacyAlbumModeFalse() {
        assertEquals(
            ReplayGainMode.SMART,
            deriveReplayGainMode(explicit = ReplayGainMode.SMART, legacyAlbumMode = false)
        )
    }

    @Test
    fun explicitMode_wins_overLegacyAlbumModeNull() {
        assertEquals(
            ReplayGainMode.ALBUM,
            deriveReplayGainMode(explicit = ReplayGainMode.ALBUM, legacyAlbumMode = null)
        )
    }

    // --- legacy fallback (new key never written) ----------------------------

    @Test
    fun noExplicit_legacyAlbumModeTrue_isAlbum() {
        assertEquals(
            ReplayGainMode.ALBUM,
            deriveReplayGainMode(explicit = null, legacyAlbumMode = true)
        )
    }

    /** Regression: existing per-track user must stay on TRACK, not flip to SMART. */
    @Test
    fun noExplicit_legacyAlbumModeFalse_isTrack_notFlippedToSmart() {
        assertEquals(
            ReplayGainMode.TRACK,
            deriveReplayGainMode(explicit = null, legacyAlbumMode = false)
        )
    }

    // --- fresh install ------------------------------------------------------

    @Test
    fun freshInstall_bothKeysNeverWritten_isSmart() {
        assertEquals(
            ReplayGainMode.SMART,
            deriveReplayGainMode(explicit = null, legacyAlbumMode = null)
        )
    }
}
