package com.netwatch.feature.dashboard

import com.netwatch.core.util.ByteFormatter
import com.netwatch.domain.model.AlertEvent
import com.netwatch.domain.model.AlertType
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope
import com.netwatch.domain.model.InternetPath
import com.netwatch.domain.model.SimRole
import java.util.Locale
import kotlin.math.abs

/**
 * Presentation-only mapping for the Dashboard 2.0 home screen.
 * Does not change plan math, alert triggers, or data collection.
 */
object DashboardPresentation {

    fun roleShortLabel(role: SimRole?): String = when (role) {
        SimRole.PRIMARY -> "主卡"
        SimRole.BACKUP -> "备用卡"
        null -> ""
    }

    fun roleLongLabel(role: SimRole?): String = when (role) {
        SimRole.PRIMARY -> "主要流量卡"
        SimRole.BACKUP -> "备用流量卡"
        null -> ""
    }

    /**
     * Compact credibility chip. When [userCalibrated] is true, the label is
     * "已校准" rather than dressing a mid-cycle calibration as 实测.
     */
    fun compactConfidenceLabel(
        confidence: DataConfidence,
        userCalibrated: Boolean = false,
        scope: DataScope? = null,
        includeScope: Boolean = false,
    ): String {
        val base = if (userCalibrated) {
            "已校准"
        } else {
            when (confidence) {
                DataConfidence.REAL -> "实测"
                DataConfidence.ESTIMATED -> "估算"
                DataConfidence.UNAVAILABLE -> "不可用"
            }
        }
        if (!includeScope) return base
        val scopeLabel = when (scope) {
            DataScope.DEVICE_WIDE -> "全设备"
            DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM -> "按默认数据卡归属"
            DataScope.PER_APP -> "按应用"
            DataScope.HOTSPOT_SESSION -> "热点会话"
            DataScope.UNKNOWN, null -> null
        }
        return if (scopeLabel == null) base else "$base · $scopeLabel"
    }

    fun headerNetworkStatus(hasInternet: Boolean, isValidated: Boolean): String = when {
        !hasInternet -> "未连接"
        isValidated -> "网络正常"
        else -> "网络未验证"
    }

    fun connectionStatusLabel(hasInternet: Boolean, isValidated: Boolean, path: InternetPath?): String = when {
        path == InternetPath.NONE || !hasInternet -> "未连接"
        isValidated -> "已连接"
        else -> "未验证"
    }

    /**
     * Newest-first [alerts], collapsing repeats of the same type and SIM slot
     * for home display only. Does not change stored history or triggers.
     */
    fun recentHomeAlerts(alerts: List<AlertEvent>, limit: Int = 2): List<AlertEvent> {
        val seen = linkedSetOf<String>()
        val result = mutableListOf<AlertEvent>()
        for (alert in alerts) {
            val key = "${alert.type}:${alert.slotIndex}"
            if (!seen.add(key)) continue
            result += alert
            if (result.size >= limit) break
        }
        return result
    }

    fun alertTitle(alert: AlertEvent): String = when (alert.type) {
        AlertType.PLAN_USAGE -> alert.message
        AlertType.HIGH_DOWNLOAD_SPEED -> "高速下载"
        AlertType.ROLLING_WINDOW -> "短时流量过大"
    }

    fun alertDescription(alert: AlertEvent): String = when (alert.type) {
        AlertType.PLAN_USAGE -> {
            val percent = formatUsedPercent(alert.planPercent)
            if (percent == "—") alert.message else "套餐使用率已达到 $percent"
        }
        AlertType.HIGH_DOWNLOAD_SPEED -> {
            val speed = alert.triggerSpeedBytesPerSec
            if (speed != null) "检测到高速下载（${ByteFormatter.formatSpeed(speed)}）" else alert.message
        }
        AlertType.ROLLING_WINDOW -> {
            val volume = alert.triggerVolumeBytes
            if (volume != null) "短时流量 ${ByteFormatter.formatBytes(volume)}" else alert.message
        }
    }

    /**
     * Used percentage for display. Whole numbers drop the trailing .0 so
     * 80.0 becomes "80%", while 18.16 becomes "18.2%".
     */
    fun formatUsedPercent(percent: Double?, placeholder: String = "—"): String {
        if (percent == null) return placeholder
        val roundedTenth = Math.round(percent * 10.0) / 10.0
        return if (abs(roundedTenth - Math.round(roundedTenth)) < 0.05) {
            String.format(Locale.US, "%.0f%%", roundedTenth)
        } else {
            String.format(Locale.US, "%.1f%%", roundedTenth)
        }
    }

    fun usedProgress(percent: Double?): Float =
        ((percent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)

    fun speedSourceLabel(
        confidence: DataConfidence,
        onUnmeteredPath: Boolean,
    ): String {
        val base = compactConfidenceLabel(confidence)
        return if (onUnmeteredPath || confidence != DataConfidence.UNAVAILABLE) {
            if (base == "不可用") base else "$base · 全设备"
        } else {
            base
        }
    }
}
