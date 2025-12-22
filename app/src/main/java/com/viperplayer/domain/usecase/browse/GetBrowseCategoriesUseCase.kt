package com.viperplayer.domain.usecase.browse

import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.repository.PluginRepository
import javax.inject.Inject

/**
 * Use case for getting browse categories.
 */
class GetBrowseCategoriesUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(
        cursor: String? = null,
        limit: Int = 20
    ): Result<PagedResult<BrowseCategory>> {
        return repository.getBrowseCategories(cursor, limit)
    }
}

/**
 * Use case for getting category contents.
 */
class GetCategoryContentsUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(
        pluginId: String,
        categoryId: String,
        cursor: String? = null,
        limit: Int = 20
    ): Result<SearchResult> {
        return repository.getCategoryContents(pluginId, categoryId, cursor, limit)
    }
}

