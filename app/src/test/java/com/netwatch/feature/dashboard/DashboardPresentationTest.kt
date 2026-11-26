package com.netwatch.feature.dashboard

import com.netwatch.domain.model.AlertEvent
import com.netwatch.domain.model.AlertType
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope
import com.netwatch.domain.model.InternetPath
import com.netwatch.domain.model.SimRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardPresentationTest {

    @Test
    fun roleLabels() {
        assertEquals("主卡", DashboardPresentation.roleShortLabel(SimRole.PRIMARY))
        assertEquals("备用卡", DashboardPresentation.roleShortLabel(SimRole.BACKUP))
        assertEquals("主要流量卡", DashboardPresentation.roleLongLabel(SimRole.PRIMARY))
        assertEquals("备用流量卡", DashboardPresentation.roleLongLabel(SimRole.BACKUP))
        assertEquals("", DashboardPresentation.roleShortLabel(null))
    }

    @Test
    fun compactConfidencePrefersCalibratedOverEstimated() {
        assertEquals(
            "已校准",
            DashboardPresentation.compactConfidenceLabel(
                DataConfidence.ESTIMATED,
                userCalibrated = true,
            ),
        )
        assertEquals("实测", DashboardPresentation.compactConfidenceLabel(DataConfidence.REAL))
        assertEquals("估算", DashboardPresentation.compactConfidenceLabel(DataConfidence.ESTIMATED))
        assertEquals("不可用", DashboardPresentation.compactConfidenceLabel(DataConfidence.UNAVAILABLE))
        assertEquals(
            "实测 · 全设备",
            DashboardPresentation.compactConfidenceLabel(
                DataConfidence.REAL,
                scope = DataScope.DEVICE_WIDE,
                includeScope = true,
            ),
        )
    }

    @Test
    fun networkAndConnectionStatusDoNotTreatWifiAsInvalidSim() {
        assertEquals("网络正常", DashboardPresentation.headerNetworkStatus(hasInternet = true, isValidated = true))
        assertEquals("网络未验证", DashboardPresentation.headerNetworkStatus(hasInternet = true, isValidated = false))
        assertEquals("未连接", DashboardPresentation.headerNetworkStatus(hasInternet = false, isValidated = false))
        assertEquals(
            "已连接",
            DashboardPresentation.connectionStatusLabel(true, true, InternetPath.WIFI),
        )
        assertEquals(
            "未连接",
            DashboardPresentation.connectionStatusLabel(false, false, InternetPath.NONE),
        )
    }

    @Test
    fun formatUsedPercentDropsWholeDecimals() {
        assertEquals("80%", DashboardPresentation.formatUsedPercent(80.0))
        assertEquals("18.2%", DashboardPresentation.formatUsedPercent(18.16))
        assertEquals("0%", DashboardPresentation.formatUsedPercent(0.0))
        assertEquals("100%", DashboardPresentation.formatUsedPercent(100.0))
        assertEquals("—", DashboardPresentation.formatUsedPercent(null))
    }

    @Test
    fun usedProgressUsesUsedRatioNotRemaining() {
        assertEquals(0.80f, DashboardPresentation.usedProgress(80.0), 0.0001f)
        assertEquals(0.182f, DashboardPresentation.usedProgress(18.2), 0.0001f)
        assertEquals(0f, DashboardPresentation.usedProgress(null), 0.0001f)
        assertEquals(1f, DashboardPresentation.usedProgress(150.0), 0.0001f)
    }

    @Test
    fun recentHomeAlertsCollapsesSameTypeAndSlot() {
        val first = alert(1, AlertType.PLAN_USAGE, slot = 0, at = 3_000L, message = "SIM 1 套餐使用率过高", percent = 81.0)
        val duplicate = alert(2, AlertType.PLAN_USAGE, slot = 0, at = 2_000L, message = "SIM 1 套餐使用率过高", percent = 85.0)
        val otherSlot = alert(3, AlertType.PLAN_USAGE, slot = 1, at = 1_500L, message = "SIM 2 套餐使用率过高", percent = 90.0)
        val speed = alert(4, AlertType.HIGH_DOWNLOAD_SPEED, slot = 1, at = 1_000L, message = "下载速度持续偏高", speed = 6L * 1024 * 1024)
        val visible = DashboardPresentation.recentHomeAlerts(listOf(first, duplicate, otherSlot, speed), limit = 2)
        assertEquals(listOf(1L, 3L), visible.map { it.id })
        assertEquals("SIM 1 套餐使用率过高", DashboardPresentation.alertTitle(first))
        assertEquals("套餐使用率已达到 81%", DashboardPresentation.alertDescription(first))
        assertEquals("高速下载", DashboardPresentation.alertTitle(speed))
        assertTrue(DashboardPresentation.alertDescription(speed).contains("MB/s"))
    }

    @Test
    fun speedSourceIsDeviceWideNeverPerSim() {
        assertEquals(
            "实测 · 全设备",
            DashboardPresentation.speedSourceLabel(DataConfidence.REAL, onUnmeteredPath = true),
        )
        assertEquals(
            "估算 · 全设备",
            DashboardPresentation.speedSourceLabel(DataConfidence.ESTIMATED, onUnmeteredPath = false),
        )
        assertEquals(
            "不可用",
            DashboardPresentation.speedSourceLabel(DataConfidence.UNAVAILABLE, onUnmeteredPath = false),
        )
    }

    private fun alert(
        id: Long,
        type: AlertType,
        slot: Int?,
        at: Long,
        message: String,
        percent: Double? = null,
        speed: Long? = null,
    ) = AlertEvent(
        id = id,
        type = type,
        triggeredAtMs = at,
        triggerSpeedBytesPerSec = speed,
        triggerVolumeBytes = null,
        planPercent = percent,
        slotIndex = slot,
        subscriptionId = null,
        message = message,
        confidence = DataConfidence.ESTIMATED,
    )
}
