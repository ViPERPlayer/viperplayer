package com.viperplayer.data.repository

import com.viperplayer.data.local.dao.SearchHistoryDao
import com.viperplayer.data.local.entity.SearchHistoryEntity
import com.viperplayer.domain.model.SearchSuggestions
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val pluginRepository: PluginRepository,
    private val searchHistoryDao: SearchHistoryDao
) : SearchRepository {
    override suspend fun getSuggestions(query: String): Flow<List<Result<SearchSuggestions>>> {
        return pluginRepository.getSearchSuggestions(query)
    }

    override suspend fun saveSearchHistory(query: String) {
        if (query.isNotBlank()) {
            val trimmedQuery = query.trim()
            searchHistoryDao.insert(SearchHistoryEntity(query = trimmedQuery))
        }
    }

    override fun getRecentHistory(limit: Int): Flow<List<String>> {
        return searchHistoryDao.getRecentHistory(limit).map { entities ->
            entities.map { it.query }
        }
    }

    override fun getHistoryContaining(query: String, limit: Int): Flow<List<String>> {
        return searchHistoryDao.getHistoryContaining(query.trim(), limit).map { entities ->
            entities.map { it.query }
        }
    }

    override suspend fun removeHistoryEntry(query: String) {
        searchHistoryDao.deleteByQuery(query)
    }
}