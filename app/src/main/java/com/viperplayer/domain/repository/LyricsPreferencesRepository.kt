package com.viperplayer.domain.repository

import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.MediaId
import kotlinx.coroutines.flow.Flow

/**
 * Per-song lyrics preferences: the manual timing [offset] and the manual lyric-match [override],
 * both keyed by [MediaId] and persisted so they survive re-opening the player. Backs two
 * feature-parity gaps (timing offset + manual lyric-match picker); the fetch/render pipeline reads
 * these to shift the active line and to substitute a user-chosen lyric set for the auto-fetched one.
 */
interface LyricsPreferencesRepository {

    /** The persisted timing offset (ms, may be negative) for [mediaId]; `0` when never set. */
    fun offset(mediaId: MediaId): Flow<Long>

    /** Persist [offsetMs] as the timing offset for [mediaId]. `0` resets it. */
    suspend fun setOffset(mediaId: MediaId, offsetMs: Long)

    /** The manually-picked override lyrics for [mediaId], or `null` when it uses auto-fetched lyrics. */
    fun override(mediaId: MediaId): Flow<Lyrics?>

    /** Persist [lyrics] as the manual override for [mediaId], replacing the auto-fetched result. */
    suspend fun setOverride(mediaId: MediaId, lyrics: Lyrics)

    /** Remove any manual override for [mediaId], reverting to auto-fetched lyrics. */
    suspend fun clearOverride(mediaId: MediaId)
}
