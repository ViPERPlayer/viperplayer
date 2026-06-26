package com.viperplayer.domain.usecase.search

import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.model.SearchFilter
import javax.inject.Inject

/**
 * UseCase for performing search.
 */
class SearchUseCase @Inject constructor(
    private val pluginRepository: PluginRepository
) {
    suspend operator fun invoke(
        query: String,
        filter: SearchFilter? = null,
        cursor: String? = null
    ): Result<SearchResult> {
        return pluginRepository.search(query, filter, cursor)
    }
}
