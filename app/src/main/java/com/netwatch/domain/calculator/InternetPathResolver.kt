package com.netwatch.domain.calculator

import com.netwatch.domain.model.InternetPath
import com.netwatch.domain.model.InternetPathState
import com.netwatch.domain.model.ObservedNetwork
import com.netwatch.domain.model.ObservedTransport

/**
 * Pure resolution of the general internet path, including VPN-over-Wi-Fi and
 * OEM dual-channel (WLAN+) cellular that is in use at the same time.
 *
 * IMS-only cellular is never treated as "on mobile data."
 * Tethering is applied by the caller via [InternetPathState.withTethering].
 */
object InternetPathResolver {

    fun resolve(
        active: ObservedNetwork?,
        underlying: List<ObservedNetwork>,
        allNetworks: List<ObservedNetwork>,
        isTethering: Boolean = false,
    ): InternetPathState {
        val vpn = active?.isVpn == true || underlying.any { it.isVpn }
        val pathNetworks = pathNetworks(active, underlying, allNetworks)
        val pathHasWifi = pathNetworks.any { it.isWifi }
        val pathHasEthernet = pathNetworks.any { it.isEthernet }
        val pathHasGeneralCellular = pathNetworks.any { it.isGeneralCellularInternet(treatAsInternetPath = true) }

        val path = when {
            pathHasWifi -> InternetPath.WIFI
            pathHasEthernet -> InternetPath.ETHERNET
            pathHasGeneralCellular -> InternetPath.MOBILE
            active == null && pathNetworks.isEmpty() -> InternetPath.NONE
            else -> InternetPath.OTHER
        }

        val pathIsUnmeteredStyle = path == InternetPath.WIFI || path == InternetPath.ETHERNET
        val concurrentBillableCellular = if (pathIsUnmeteredStyle) {
            allNetworks.any { network ->
                network.isGeneralCellularInternet(treatAsInternetPath = false)
            }
        } else {
            false
        }

        val billableMobile = path == InternetPath.MOBILE || concurrentBillableCellular
        val notes = buildList {
            if (vpn && path == InternetPath.WIFI) {
                add("当前默认网络是 VPN，底层为 Wi-Fi，按 Wi-Fi 上网处理。")
            }
            if (vpn && path == InternetPath.ETHERNET) {
                add("当前默认网络是 VPN，底层为以太网，按以太网上网处理。")
            }
            if (vpn && path == InternetPath.MOBILE) {
                add("当前默认网络是 VPN，底层为移动数据。")
            }
            if (concurrentBillableCellular) {
                add("检测到同时使用的通用移动数据（双通道 / WLAN+），移动增量仍计入套餐。")
            }
            if (allNetworks.any { it.isCellular && (it.hasIms || it.hasMmtel) && !it.isGeneralCellularInternet(path == InternetPath.MOBILE && it in pathNetworks) }) {
                add("存在 IMS/通话蜂窝承载，这不是通用上网路径，不会当作正在使用移动数据。")
            }
        }

        return InternetPathState(
            path = path,
            isVpn = vpn,
            isTethering = false,
            hasConcurrentBillableCellular = concurrentBillableCellular,
            billableMobile = billableMobile,
            transportLabel = label(path, vpn, concurrentBillableCellular),
            notes = notes,
        ).withTethering(isTethering)
    }

    /**
     * Networks that actually carry the default internet path.
     * If the active network is a VPN, prefer its underlying networks; if those
     * are missing, fall back to other non-VPN internet networks (common with
     * some VPN apps).
     */
    private fun pathNetworks(
        active: ObservedNetwork?,
        underlying: List<ObservedNetwork>,
        allNetworks: List<ObservedNetwork>,
    ): List<ObservedNetwork> {
        if (active == null) return emptyList()
        if (active.isVpn) {
            val fromUnderlying = underlying.filterNot { it.isVpn }
            if (fromUnderlying.isNotEmpty()) return fromUnderlying
            val fallback = allNetworks.filter { !it.isVpn && it.hasInternet }
            if (fallback.isNotEmpty()) return preferred(fallback)
            return listOf(active)
        }
        return listOf(active)
    }

    private fun preferred(candidates: List<ObservedNetwork>): List<ObservedNetwork> {
        val ranked = candidates.sortedWith(
            compareByDescending<ObservedNetwork> { it.isValidated }
                .thenByDescending { it.isWifi }
                .thenByDescending { it.isEthernet }
                .thenByDescending { it.isGeneralCellularInternet(treatAsInternetPath = true) },
        )
        val best = ranked.first()
        // If both Wi-Fi and general cellular are present as candidates (no
        // explicit underlying list), prefer Wi-Fi as the path and let the
        // concurrent-cellular check handle WLAN+.
        return if (best.isWifi || best.isEthernet) {
            listOf(best)
        } else {
            listOf(best)
        }
    }

    private fun label(path: InternetPath, vpn: Boolean, concurrentCellular: Boolean): String {
        val base = when (path) {
            InternetPath.WIFI -> if (vpn) "Wi-Fi（VPN）" else "Wi-Fi"
            InternetPath.ETHERNET -> if (vpn) "以太网（VPN）" else "以太网"
            InternetPath.MOBILE -> if (vpn) "移动数据（VPN）" else "移动数据"
            InternetPath.OTHER -> if (vpn) "VPN" else "其他网络"
            InternetPath.NONE -> "未连接"
        }
        return if (concurrentCellular && path != InternetPath.MOBILE) {
            "$base + 移动数据"
        } else {
            base
        }
    }
}
