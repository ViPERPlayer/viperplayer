package com.viperplayer.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * A byline reference to an artist (the credited artists on a [Song] or [Album]).
 *
 * Unlike the entity [Artist] (a real, navigable [MediaItem]), an [ArtistRef] is just a name that
 * may or may not be linked. `id == null` means the plugin gave us no concrete link — the ref is
 * unlinked and not navigable. A linked ref carries a real [MediaId] (never one with a blank
 * sourceId — absence of a link is `null`).
 */
@Serializable
@Parcelize
@Immutable
data class ArtistRef(
    val name: String,
    val id: MediaId? = null
) : Parcelable

/**
 * Promotes a linked byline ref to an entity [Artist] for a navigation boundary (nav callbacks are
 * typed `(Artist) -> Unit`). Returns null for an unlinked ref (no id), which is never navigable.
 */
fun ArtistRef.toEntity(): Artist? = id?.let { Artist(id = it, name = name) }
