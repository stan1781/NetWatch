package com.netwatch.domain.model

/**
 * The network actually used for general internet, after looking through a VPN
 * to its underlying transports.
 */
enum class InternetPath {
    WIFI,
    ETHERNET,
    MOBILE,
    OTHER,
    NONE,
}

/**
 * A single network observed through public [android.net.NetworkCapabilities].
 * This is a plain data holder so path resolution can be unit-tested.
 */
data class ObservedNetwork(
    val transports: Set<ObservedTransport>,
    val hasInternet: Boolean,
    val isValidated: Boolean,
    val hasIms: Boolean,
    val hasMmtel: Boolean,
) {
    val isVpn: Boolean get() = ObservedTransport.VPN in transports
    val isWifi: Boolean get() = ObservedTransport.WIFI in transports
    val isEthernet: Boolean get() = ObservedTransport.ETHERNET in transports
    val isCellular: Boolean get() = ObservedTransport.CELLULAR in transports

    /**
     * IMS/MMTEL cellular that is not the general internet path, e.g. a
     * standing IMS bearer that can keep TelephonyManager.DATA_CONNECTED
     * true while the phone is on Wi-Fi.
     */
    val isImsOnlyCellular: Boolean
        get() = isCellular && (hasIms || hasMmtel) && !hasInternet

    /**
     * Cellular that can carry general (billable) internet, excluding IMS-only
     * bearers. A cellular network that also advertises IMS/MMTEL is only
     * treated as general internet when [treatAsInternetPath] is true, i.e. it
     * is the resolved default/underlying path itself.
     */
    fun isGeneralCellularInternet(treatAsInternetPath: Boolean = false): Boolean {
        if (!isCellular || !hasInternet) return false
        if (hasIms || hasMmtel) return treatAsInternetPath
        return true
    }
}

enum class ObservedTransport {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    OTHER,
}

data class InternetPathState(
    val path: InternetPath,
    val isVpn: Boolean = false,
    val isTethering: Boolean = false,
    val hasConcurrentBillableCellular: Boolean = false,
    val billableMobile: Boolean,
    val transportLabel: String,
    val notes: List<String> = emptyList(),
) {
    fun withTethering(tethering: Boolean): InternetPathState {
        if (!tethering) return copy(isTethering = false)
        if (isTethering && billableMobile) return this
        val label = when {
            path == InternetPath.MOBILE && !isVpn -> "热点"
            transportLabel.contains("热点") -> transportLabel
            else -> "$transportLabel · 热点"
        }
        return copy(isTethering = true, billableMobile = true, transportLabel = label)
    }

    companion object {
        val Mobile = InternetPathState(
            path = InternetPath.MOBILE,
            billableMobile = true,
            transportLabel = "移动数据",
        )
        val Wifi = InternetPathState(
            path = InternetPath.WIFI,
            billableMobile = false,
            transportLabel = "Wi-Fi",
        )
        val WifiVpn = InternetPathState(
            path = InternetPath.WIFI,
            isVpn = true,
            billableMobile = false,
            transportLabel = "Wi-Fi（VPN）",
        )
        val Ethernet = InternetPathState(
            path = InternetPath.ETHERNET,
            billableMobile = false,
            transportLabel = "以太网",
        )
        val Tethering = InternetPathState(
            path = InternetPath.MOBILE,
            isTethering = true,
            billableMobile = true,
            transportLabel = "热点",
        )
        val Unknown = InternetPathState(
            path = InternetPath.NONE,
            billableMobile = false,
            transportLabel = "未知",
        )
        val Disconnected = InternetPathState(
            path = InternetPath.NONE,
            billableMobile = false,
            transportLabel = "未连接",
        )
    }
}
