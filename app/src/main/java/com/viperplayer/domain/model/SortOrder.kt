package com.viperplayer.domain.model

/**
 * A single sort criterion offered in a library/detail sort menu.
 *
 * [DEFAULT] is the natural/original order of a given view (the DAO's existing default ordering, the
 * plugin query's order, or a playlist's insertion order). It is always the first option in every menu
 * and is selected initially, so behaviour is unchanged until the user picks something else. It has no
 * meaningful direction (treated as [SortDirection.ASCENDING]).
 *
 * The remaining options are the fields a view can sort by. Not every view supports every field — each
 * view exposes only the applicable subset (see the per-view `options` lists in the presentation layer).
 */
enum class SortOption {
    /** Natural / original order for the view. Always first, selected by default, no direction toggle. */
    DEFAULT,
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    DATE_ADDED,
    DATE_MODIFIED,
    DURATION,
    TRACK_NUMBER,
    YEAR,
    PLAY_COUNT,
}

/** Sort direction. [DEFAULT][SortOption.DEFAULT] is always treated as [ASCENDING]. */
enum class SortDirection {
    ASCENDING,
    DESCENDING;

    fun toggled(): SortDirection = if (this == ASCENDING) DESCENDING else ASCENDING
}

/**
 * A chosen sort order for a view: which field ([option]) and which [direction].
 *
 * Persisted per view via [com.viperplayer.domain.repository.SettingsRepository]. The default value is
 * [DEFAULT], which preserves the view's original order.
 */
data class SortOrder(
    val option: SortOption = SortOption.DEFAULT,
    val direction: SortDirection = SortDirection.ASCENDING,
) {
    /** True when this is the passthrough/original order (no reordering applied). */
    val isDefault: Boolean get() = option == SortOption.DEFAULT

    companion object {
        /** The passthrough order — every view starts here so behaviour is initially unchanged. */
        val DEFAULT = SortOrder(SortOption.DEFAULT, SortDirection.ASCENDING)
    }
}

/**
 * The distinct sortable views. Each persists its own [SortOrder] under a stable key, so the Songs tab,
 * Albums tab, an album's track list, etc. each remember their own chosen order independently.
 */
enum class SortView {
    LIBRARY_SONGS,
    LIBRARY_ALBUMS,
    LIBRARY_ARTISTS,
    LIBRARY_PLAYLISTS,
    ALBUM_TRACKS,
    ARTIST_SONGS,
    PLAYLIST_SONGS,
}
