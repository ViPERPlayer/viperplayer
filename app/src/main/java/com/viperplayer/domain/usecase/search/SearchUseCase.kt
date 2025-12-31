package com.viperplayer.domain.usecase.search

import com.viperplayer.domain.repository.PluginRepository
import javax.inject.Inject

/**
 * Use case for searching across all plugins.
 */
class SearchUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(
        query: String,
        types: Int = PluginRepository.SEARCH_TYPE_ALL,
        cursor: String? = null,
        limit: Int = 20
    ): Result<com.viperplayer.plugin.sdk.v1.SearchResult> {
        return repository.search(query, types, cursor, limit)
    }
}

