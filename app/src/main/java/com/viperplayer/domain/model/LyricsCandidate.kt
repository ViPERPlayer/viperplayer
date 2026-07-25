package com.viperplayer.domain.model

/**
 * One candidate result from a lyrics-provider search (feature parity with a manual lyric-match
 * picker). Carries the display metadata a user needs to tell candidates apart — [trackName],
 * [artistName], [albumName], [durationMs] and whether it is line-[synced] — plus the already-parsed
 * [lyrics] payload so that picking a candidate applies it with no second network round-trip.
 */
data class LyricsCandidate(
    val trackName: String,
    val artistName: String?,
    val albumName: String?,
    val durationMs: Long?,
    val synced: Boolean,
    val lyrics: Lyrics,
)
