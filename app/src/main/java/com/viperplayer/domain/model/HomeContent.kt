package com.viperplayer.domain.model

/**
 * Container for home screen content provided by a plugin.
 */
data class HomeContent(
    val quickPicks: List<MediaItem>? = null,
    val sections: List<HomeSection> = emptyList()
)

/**
 * Represents a custom section on the home screen provided by a plugin.
 */
data class HomeSection(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
    val layout: Layout = Layout.LIST
) {
    enum class Layout {
        LIST,
        GRID
    }
}
