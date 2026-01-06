package com.viperplayer.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors network connectivity and provides a reactive flow of internet availability.
 */
@Singleton
class NetworkConnectivityChecker @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    /**
     * Flow that emits true when internet is available, false otherwise.
     */
    val isInternetAvailable: StateFlow<Boolean> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val available = isNetworkAvailable()
                Timber.d("Network available: $available")
                trySend(available)
            }
            
            override fun onLost(network: Network) {
                val available = isNetworkAvailable()
                Timber.d("Network lost: $available")
                trySend(available)
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val available = isNetworkAvailable()
                Timber.d("Network capabilities changed: $available")
                trySend(available)
            }
        }
        
        // Register network callback
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        
        // Emit initial state
        trySend(isNetworkAvailable())
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = isNetworkAvailable()
        )
    
    /**
     * Checks if internet is currently available.
     */
    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

