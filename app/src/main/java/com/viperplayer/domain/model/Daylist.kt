package com.viperplayer.domain.model

import androidx.compose.runtime.Immutable
import com.viperplayer.domain.rec.TimeBucket

/**
 * A **daylist** for one time-of-day slot: a generated descriptive [title] + short
 * [description], the recommended [songs], and the [bucket] the slot represents. Fully on-device —
 * built from the user's per-bucket taste and library embeddings, no login/network (see
 * `DaylistRepository`).
 *
 * There are (up to) four slots a day — one per [TimeBucket] — and the naming/songs change as the bucket
 * rolls over. [generatedAtMs] stamps when this slot was materialized so the repository can decide when
 * the slot's window has passed and it should regenerate.
 *
 * Marked [Immutable] so it can be hoisted into Compose state without churning recomposition.
 */
@Immutable
data class Daylist(
    val bucket: TimeBucket,
    val title: String,
    val description: String,
    val songs: List<Song>,
    val generatedAtMs: Long,
)
