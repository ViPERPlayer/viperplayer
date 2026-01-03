package com.viperplayer.domain.model

/**
 * Category content type.
 */
enum class CategoryContentType {
    CATEGORIES, PLAYLISTS, ALBUMS, ARTISTS, SONGS, MIXED
}

/**
 * Represents a browsable category.
 */
data class BrowseCategory(
    val id: String,
    val pluginId: String,
    val name: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val contentType: CategoryContentType = CategoryContentType.MIXED
)

