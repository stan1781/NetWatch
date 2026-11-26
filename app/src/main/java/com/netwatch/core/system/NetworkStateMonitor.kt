package com.netwatch.core.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.netwatch.domain.calculator.InternetPathResolver
import com.netwatch.domain.model.InternetPath
import com.netwatch.domain.model.InternetPathState
import com.netwatch.domain.model.ObservedNetwork
import com.netwatch.domain.model.ObservedTransport
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
    val path: InternetPathState = InternetPathState.Unknown,
) {
    val billableMobile: Boolean get() = path.billableMobile
}

class NetworkStateMonitor(private val context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    fun current(isTethering: Boolean = false): NetworkState {
        val cm = manager ?: return NetworkState(
            hasInternet = false,
            isMobile = false,
            isWifi = false,
            isValidated = false,
            transportLabel = "未知",
            path = InternetPathState.Unknown.withTethering(isTethering),
        )
        return resolve(cm, isTethering)
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
                // Always re-resolve the active path (including VPN underlying
                // networks). Do not label the callback's caps as-is: a VPN
                // default network would otherwise appear as "其他网络" or even
                // "移动数据".
                trySend(current())
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun resolve(cm: ConnectivityManager, isTethering: Boolean): NetworkState {
        val active = cm.activeNetwork
        val activeCaps = active?.let { runCatching { cm.getNetworkCapabilities(it) }.getOrNull() }
        val activeObserved = activeCaps?.toObserved()

        // getUnderlyingNetworks() is not a public third-party API in the SDK
        // we compile against. When the default network is a VPN, resolve the
        // real path from the other networks reported by getAllNetworks() and
        // their public NetworkCapabilities (Wi-Fi / Ethernet / general cellular).
        val allNetworks = buildList {
            val networks = runCatching { cm.allNetworks }.getOrNull().orEmpty()
            for (network in networks) {
                val caps = runCatching { cm.getNetworkCapabilities(network) }.getOrNull() ?: continue
                add(caps.toObserved())
            }
        }
        val underlying = if (activeObserved?.isVpn == true) {
            allNetworks.filter { !it.isVpn && it.hasInternet }
        } else {
            emptyList()
        }

        val path = InternetPathResolver.resolve(
            active = activeObserved,
            underlying = underlying,
            allNetworks = allNetworks,
            isTethering = isTethering,
        )
        val validated = when {
            underlying.isNotEmpty() -> underlying.any { it.isValidated } || (activeObserved?.isValidated == true)
            activeObserved != null -> activeObserved.isValidated
            else -> false
        }
        val hasInternet = when (path.path) {
            InternetPath.NONE -> false
            else -> activeObserved?.hasInternet == true || path.path != InternetPath.OTHER || allNetworks.any { it.hasInternet }
        }
        return NetworkState(
            hasInternet = hasInternet,
            isMobile = path.path == InternetPath.MOBILE || path.hasConcurrentBillableCellular,
            isWifi = path.path == InternetPath.WIFI,
            isValidated = validated,
            transportLabel = path.transportLabel,
            path = path,
        )
    }
}

internal fun NetworkCapabilities.toObserved(): ObservedNetwork {
    val transports = buildSet {
        if (hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add(ObservedTransport.WIFI)
        if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add(ObservedTransport.CELLULAR)
        if (hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add(ObservedTransport.ETHERNET)
        if (hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add(ObservedTransport.VPN)
        if (isEmpty()) add(ObservedTransport.OTHER)
    }
    return ObservedNetwork(
        transports = transports,
        hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        hasIms = hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS),
        hasMmtel = hasCapability(NetworkCapabilities.NET_CAPABILITY_MMTEL),
    )
}
