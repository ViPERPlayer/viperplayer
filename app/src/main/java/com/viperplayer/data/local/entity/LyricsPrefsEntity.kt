package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-song lyrics preferences, keyed by the song's canonical [com.viperplayer.domain.model.MediaId]
 * `encode()` string. Backs two feature-parity gaps:
 *
 *  - [offsetMs] — the manual timing offset (ms, may be negative) applied to synced lyrics for this
 *    song. `0` when the user has never nudged it.
 *  - [overrideJson] — a manually-picked lyric-match override (serialized [Lyrics]) that replaces the
 *    auto-fetched result for this song, or `null` when the song uses the auto-fetched lyrics.
 *
 * A row exists as soon as *either* is set; both can be cleared independently (an all-default row is
 * pruned by the DAO).
 */
@Entity(tableName = "lyrics_prefs")
data class LyricsPrefsEntity(
    @PrimaryKey
    val mediaId: String,
    val offsetMs: Long = 0L,
    val overrideJson: String? = null,
)
