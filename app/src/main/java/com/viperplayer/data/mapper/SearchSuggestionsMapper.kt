package com.viperplayer.data.mapper

import com.viperplayer.data.mapper.PluginMapper.toDomain
import com.viperplayer.domain.model.SearchSuggestions
import com.viperplayer.plugin.v1.SearchSuggestionsResultV1

/**
 * Mapper for converting plugin search suggestions models to domain models.
 */
object SearchSuggestionsMapper {
    fun SearchSuggestionsResultV1.toDomain(pluginId: String): SearchSuggestions {
        return SearchSuggestions(
            pluginId = pluginId,
            suggestions = this.suggestions,
            items = this.items.map { it.toDomain(pluginId) }
        )
    }
}
