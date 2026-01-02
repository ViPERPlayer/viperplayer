package com.viperplayer.domain.repository

import com.viperplayer.plugin.v1.SearchSuggestionsResultV1
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    // Retrieves search suggestions for the given query, asynchronously.
    // Different plugins will take different amount of times to return the data.
    suspend fun getSuggestions(query: String): Flow<List<Result<SearchSuggestionsResultV1>>>
    
    // Saves a search query to history
    suspend fun saveSearchHistory(query: String)
    
    // Gets recent search history (when query is empty)
    fun getRecentHistory(limit: Int = 10): Flow<List<String>>
    
    // Gets search history containing the query
    fun getHistoryContaining(query: String, limit: Int = 10): Flow<List<String>>
    
    // Removes a search history entry
    suspend fun removeHistoryEntry(query: String)
}
