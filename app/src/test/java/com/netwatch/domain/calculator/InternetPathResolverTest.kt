package com.netwatch.domain.calculator

import com.netwatch.domain.model.InternetPath
import com.netwatch.domain.model.ObservedNetwork
import com.netwatch.domain.model.ObservedTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetPathResolverTest {

    @Test
    fun wifiPathIsNotBillable() {
        val state = InternetPathResolver.resolve(
            active = wifi(),
            underlying = emptyList(),
            allNetworks = listOf(wifi()),
        )
        assertEquals(InternetPath.WIFI, state.path)
        assertFalse(state.billableMobile)
        assertFalse(state.isVpn)
        assertEquals("Wi-Fi", state.transportLabel)
    }

    @Test
    fun ethernetPathIsNotBillable() {
        val state = InternetPathResolver.resolve(
            active = ethernet(),
            underlying = emptyList(),
            allNetworks = listOf(ethernet()),
        )
        assertEquals(InternetPath.ETHERNET, state.path)
        assertFalse(state.billableMobile)
        assertEquals("以太网", state.transportLabel)
    }

    @Test
    fun mobilePathIsBillable() {
        val state = InternetPathResolver.resolve(
            active = cellularInternet(),
            underlying = emptyList(),
            allNetworks = listOf(cellularInternet()),
        )
        assertEquals(InternetPath.MOBILE, state.path)
        assertTrue(state.billableMobile)
        assertEquals("移动数据", state.transportLabel)
    }

    @Test
    fun vpnOverWifiIsTreatedAsWifi() {
        val state = InternetPathResolver.resolve(
            active = vpn(),
            underlying = listOf(wifi()),
            allNetworks = listOf(vpn(), wifi(), imsCellular()),
        )
        assertEquals(InternetPath.WIFI, state.path)
        assertTrue(state.isVpn)
        assertFalse(state.billableMobile)
        assertFalse(state.hasConcurrentBillableCellular)
        assertEquals("Wi-Fi（VPN）", state.transportLabel)
    }

    @Test
    fun vpnWithoutUnderlyingFallsBackToWifiInternet() {
        val state = InternetPathResolver.resolve(
            active = vpn(),
            underlying = emptyList(),
            allNetworks = listOf(vpn(), wifi()),
        )
        assertEquals(InternetPath.WIFI, state.path)
        assertTrue(state.isVpn)
        assertFalse(state.billableMobile)
    }

    @Test
    fun imsCellularOnWifiIsNotBillable() {
        val state = InternetPathResolver.resolve(
            active = wifi(),
            underlying = emptyList(),
            allNetworks = listOf(wifi(), imsCellular()),
        )
        assertEquals(InternetPath.WIFI, state.path)
        assertFalse(state.billableMobile)
        assertFalse(state.hasConcurrentBillableCellular)
    }

    @Test
    fun imsCellularDoesNotMakeVpnOverWifiBillable() {
        val state = InternetPathResolver.resolve(
            active = vpn(),
            underlying = listOf(wifi()),
            allNetworks = listOf(vpn(), wifi(), imsCellular()),
        )
        assertFalse(state.billableMobile)
        assertFalse(state.hasConcurrentBillableCellular)
    }

    @Test
    fun concurrentGeneralCellularOnWifiIsBillable() {
        val state = InternetPathResolver.resolve(
            active = wifi(),
            underlying = emptyList(),
            allNetworks = listOf(wifi(), cellularInternet()),
        )
        assertEquals(InternetPath.WIFI, state.path)
        assertTrue(state.hasConcurrentBillableCellular)
        assertTrue(state.billableMobile)
        assertEquals("Wi-Fi + 移动数据", state.transportLabel)
    }

    @Test
    fun tetheringIsBillableEvenOnWifi() {
        val state = InternetPathResolver.resolve(
            active = wifi(),
            underlying = emptyList(),
            allNetworks = listOf(wifi()),
            isTethering = true,
        )
        assertTrue(state.isTethering)
        assertTrue(state.billableMobile)
    }

    @Test
    fun tetheringOnMobileKeepsHotspotLabel() {
        val state = InternetPathResolver.resolve(
            active = cellularInternet(),
            underlying = emptyList(),
            allNetworks = listOf(cellularInternet()),
            isTethering = true,
        )
        assertEquals(InternetPath.MOBILE, state.path)
        assertTrue(state.billableMobile)
        assertEquals("热点", state.transportLabel)
    }

    @Test
    fun disconnectedHasNoPath() {
        val state = InternetPathResolver.resolve(
            active = null,
            underlying = emptyList(),
            allNetworks = emptyList(),
        )
        assertEquals(InternetPath.NONE, state.path)
        assertFalse(state.billableMobile)
        assertEquals("未连接", state.transportLabel)
    }

    private fun wifi() = ObservedNetwork(
        transports = setOf(ObservedTransport.WIFI),
        hasInternet = true,
        isValidated = true,
        hasIms = false,
        hasMmtel = false,
    )

    private fun ethernet() = ObservedNetwork(
        transports = setOf(ObservedTransport.ETHERNET),
        hasInternet = true,
        isValidated = true,
        hasIms = false,
        hasMmtel = false,
    )

    private fun cellularInternet() = ObservedNetwork(
        transports = setOf(ObservedTransport.CELLULAR),
        hasInternet = true,
        isValidated = true,
        hasIms = false,
        hasMmtel = false,
    )

    private fun imsCellular() = ObservedNetwork(
        transports = setOf(ObservedTransport.CELLULAR),
        hasInternet = false,
        isValidated = false,
        hasIms = true,
        hasMmtel = true,
    )

    private fun vpn() = ObservedNetwork(
        transports = setOf(ObservedTransport.VPN),
        hasInternet = true,
        isValidated = true,
        hasIms = false,
        hasMmtel = false,
    )
}
