package com.viperplayer.domain.model

/**
 * Single source of truth for "can we navigate to this?".
 *
 * A media item is only a valid navigation target when it points at a real, linked entity. Some
 * plugin items (e.g. unlinked a plugin artists) come back without a concrete link — today that
 * shows up as a blank [MediaId.sourceId]. Callers (tappable artist spans, "Go to artist" / "Go to
 * album" menu items, Song Info tiles) MUST route through these helpers rather than inspecting the
 * id directly, so the rule can evolve in one place.
 *
 * (When `Artist.id` becomes nullable — null meaning "unlinked" — only the bodies below change to an
 * `id != null` check; every call site stays as-is.)
 */

/** True when this artist has a real, navigable target. */
fun Artist.isNavigable(): Boolean = id.sourceId.isNotBlank()

/** True when this album has a real, navigable target. */
fun Album.isNavigable(): Boolean = id.sourceId.isNotBlank()

/** The first artist with a navigable target, or null when none is linked. */
fun Song.navigableArtist(): Artist? = artists.firstOrNull { it.isNavigable() }

/** The album only when it exists and is navigable, else null. */
fun Song.navigableAlbum(): Album? = album?.takeIf { it.isNavigable() }

/** The first navigable artist of an album, or null when none is linked. */
fun Album.navigableArtist(): Artist? = artists.firstOrNull { it.isNavigable() }
