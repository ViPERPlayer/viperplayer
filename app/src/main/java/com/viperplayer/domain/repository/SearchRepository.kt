package com.viperplayer.domain.repository

import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    // Retrieves search suggestions for the given query, asynchronously.
    // Different plugins will take different amount of times to return the data.
    suspend fun getSuggestions(query: String): Flow<List<Result<List<String>>>>
}
