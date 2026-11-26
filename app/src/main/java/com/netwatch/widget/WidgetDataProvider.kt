package com.netwatch.widget

import android.content.Context
import com.netwatch.NetWatchApplication
import com.netwatch.core.util.ByteFormatter
import com.netwatch.core.util.TimeUtils
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.NetworkKind
import com.netwatch.service.TrafficMonitorService

data class WidgetUiData(
    val defaultSimLabel: String,
    val carrier: String,
    val downloadSpeed: String,
    val uploadSpeed: String,
    val todayMobile: String,
    val planLine: String,
    val confidenceNote: String,
)

object WidgetDataProvider {
    fun placeholder(): WidgetUiData = WidgetUiData(
        defaultSimLabel = "默认数据卡：未知",
        carrier = "—",
        downloadSpeed = "↓ —",
        uploadSpeed = "↑ —",
        todayMobile = "今日移动数据：—",
        planLine = "套餐：—",
        confidenceNote = "打开应用并授予权限后刷新",
    )

    suspend fun load(context: Context): WidgetUiData {
        val app = context.applicationContext as? NetWatchApplication ?: return placeholder()
        val container = app.container
        val sim = container.simManager.current()
        val defaultCard = sim.cards.firstOrNull { it.isDefaultData }
        val sample = TrafficMonitorService.latestSample.value
        val today = container.networkStatsRepository.devicePeriod(
            NetworkKind.MOBILE,
            TimeUtils.startOfDay(TimeUtils.today()),
            TimeUtils.endOfDayExclusive(TimeUtils.today()),
        )
        val plans = container.observePlanUsageUseCase.compute(sim)
        val plan = defaultCard?.let { card -> plans.firstOrNull { it.slotIndex == card.slotIndex } }
        val planLine = if (plan == null) {
            "套餐：无默认数据卡"
        } else {
            val used = ByteFormatter.formatBytes(plan.usedBytes)
            val cap = ByteFormatter.formatBytes(plan.generalCapacityBytes)
            val percent = ByteFormatter.formatPercent(plan.usagePercent)
            val quality = if (plan.userCalibrated) "手动校准" else "估算"
            "SIM ${plan.slotIndex + 1} 已用 $used / $cap（$percent，$quality）"
        }
        val todayLine = if (today.confidence == DataConfidence.UNAVAILABLE) {
            "今日移动数据：不可用（需使用情况访问权限）"
        } else {
            "今日移动数据：${ByteFormatter.formatBytes(today.volume.total)}（全设备，系统统计）"
        }
        return WidgetUiData(
            defaultSimLabel = defaultCard?.let { "${it.label}（默认数据卡）" } ?: "默认数据卡：未识别",
            carrier = defaultCard?.carrierName ?: defaultCard?.displayName ?: "运营商未知",
            downloadSpeed = "↓ ${ByteFormatter.formatSpeed(sample?.displayDownloadBytesPerSec)}",
            uploadSpeed = "↑ ${ByteFormatter.formatSpeed(sample?.displayUploadBytesPerSec)}",
            todayMobile = todayLine,
            planLine = planLine,
            confidenceNote = if (plan?.userCalibrated == true) {
                val path = sample?.displayPathLabel?.let { "当前为 $it。" } ?: ""
                path + "速度为 TrafficStats 实测；分卡套餐为手动校准加估算增量，非运营商官方数据"
            } else {
                "速度为 TrafficStats 实测；分卡套餐为估算"
            },
        )
    }
}
