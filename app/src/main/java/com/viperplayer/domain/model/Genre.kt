package com.viperplayer.domain.model

import androidx.compose.runtime.Immutable

/**
 * A music genre in the local library, with the number of songs tagged with it. Backed by the `genres`
 * table; the count comes from the `song_genres` join. Identified by its stable Room row [id] (the key the
 * genre DAO/nav route use) and its display [name].
 */
@Immutable
data class Genre(
    val id: Long,
    val name: String,
    val songCount: Int,
)
