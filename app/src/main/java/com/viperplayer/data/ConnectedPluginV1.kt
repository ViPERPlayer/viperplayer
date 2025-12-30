package com.viperplayer.data

import android.content.ServiceConnection
import com.viperplayer.data.source.PluginException
import com.viperplayer.domain.model.PluginCapabilities
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.plugin.sdk.v1.ISearchSuggestionsCallback
import com.viperplayer.plugin.sdk.v1.IViperPluginV1
import com.viperplayer.plugin.sdk.v1.SearchSuggestionsResultV1
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ConnectedPluginV1(
    val info: PluginInfo,
    val capabilities: PluginCapabilities,
    val service: IViperPluginV1,
    val connection: ServiceConnection
) {
    suspend fun getSearchSuggestions(query: String): Result<SearchSuggestionsResultV1> {
        return runCatching {
            suspendCoroutine { continuation ->
                service.getSearchSuggestions(query, object : ISearchSuggestionsCallback.Stub() {
                    override fun onSuccess(result: SearchSuggestionsResultV1) {
                        continuation.resume(result)
                    }

                    override fun onFailure(errorCode: Int, message: String?) {
                        continuation.resumeWithException(PluginException(errorCode, message))
                    }
                })
            }
        }
    }
}