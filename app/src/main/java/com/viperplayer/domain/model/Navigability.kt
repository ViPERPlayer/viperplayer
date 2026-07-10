package com.viperplayer.domain.model

/**
 * Single source of truth for "can we navigate to this?".
 *
 * A media item is only a valid navigation target when it points at a real, linked entity. A byline
 * [ArtistRef] is linked only when its [ArtistRef.id] is non-null (absence of a link is `null`).
 * Entity [Artist]/[Album] items always carry a real [MediaId]. Callers (tappable artist spans, "Go
 * to artist" / "Go to album" menu items, Song Info tiles) MUST route through these helpers rather
 * than inspecting the id directly, so the rule can evolve in one place.
 */

/** True when this byline ref points at a real, linked artist. */
fun ArtistRef.isNavigable(): Boolean = id != null

/** True when this artist has a real, navigable target (entity artists always do). */
fun Artist.isNavigable(): Boolean = true

/** True when this album has a real, navigable target (entity albums always do). */
fun Album.isNavigable(): Boolean = true

/** The first byline ref with a navigable target, or null when none is linked. */
fun Song.navigableArtist(): ArtistRef? = artists.firstOrNull { it.isNavigable() }

/** The album only when it exists and is navigable, else null. */
fun Song.navigableAlbum(): Album? = album?.takeIf { it.isNavigable() }

/** The first navigable byline ref of an album, or null when none is linked. */
fun Album.navigableArtist(): ArtistRef? = artists.firstOrNull { it.isNavigable() }
