package com.viperplayer.data.repository

import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val pluginRepository: PluginRepository
) : SearchRepository {
    override suspend fun getSuggestions(query: String): Flow<List<Result<List<String>>>> {
        return pluginRepository.getSearchSuggestions(query)
    }
}