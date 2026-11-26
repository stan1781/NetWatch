package com.netwatch.core.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class NetworkState(
    val hasInternet: Boolean,
    val isMobile: Boolean,
    val isWifi: Boolean,
    val isValidated: Boolean,
    val transportLabel: String,
)

class NetworkStateMonitor(private val context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    fun current(): NetworkState {
        val cm = manager ?: return NetworkState(false, false, false, false, "未知")
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        return from(caps)
    }

    fun observe(): Flow<NetworkState> = callbackFlow {
        val cm = manager
        if (cm == null) {
            trySend(current())
            awaitClose { }
            return@callbackFlow
        }
        trySend(current())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(current())
            }

            override fun onLost(network: Network) {
                trySend(current())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(from(networkCapabilities))
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun from(caps: NetworkCapabilities?): NetworkState {
        if (caps == null) return NetworkState(false, false, false, false, "未连接")
        val mobile = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val label = when {
            mobile -> "移动数据"
            wifi -> "Wi-Fi"
            ethernet -> "以太网"
            else -> "其他网络"
        }
        return NetworkState(internet, mobile, wifi, validated, label)
    }
}
