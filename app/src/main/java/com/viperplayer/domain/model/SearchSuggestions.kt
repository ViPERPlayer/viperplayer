package com.viperplayer.domain.model

/**
 * Search suggestions from a plugin.
 */
data class SearchSuggestions(
    val pluginId: String,
    val suggestions: List<String> = emptyList(),
    val items: List<MediaItem> = emptyList()
)
