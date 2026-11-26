package com.netwatch.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netwatch.core.util.ByteFormatter
import com.netwatch.core.util.TimeUtils
import com.netwatch.di.LocalAppContainer
import com.netwatch.domain.model.AlertType
import com.netwatch.domain.model.InternetPath
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope
import com.netwatch.domain.model.PlanUsage
import com.netwatch.domain.model.SimCard
import com.netwatch.ui.appViewModel
import com.netwatch.ui.components.ConfidenceChip
import com.netwatch.ui.components.InfoCard
import com.netwatch.ui.components.KeyValueRow
import com.netwatch.ui.components.NoteText
import com.netwatch.ui.components.SectionTitle
import com.netwatch.ui.components.SpeedChart

@Composable
fun DashboardScreen(
    onOpenSimPlan: (Int) -> Unit,
    onOpenRealtime: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = appViewModel { DashboardViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("套餐总览") }
        items(2) { slot ->
            val card = state.sim?.cards?.firstOrNull { it.slotIndex == slot }
            val plan = state.plans.firstOrNull { it.slotIndex == slot }
            if (card != null && plan != null) {
                PlanOverviewCard(card, plan, onClick = { onOpenSimPlan(slot) })
            }
        }

        item { SectionTitle("当前数据卡") }
        item {
            val sim = state.sim
            val network = state.network
            val current = sim?.cards?.firstOrNull { it.isDefaultData }
            InfoCard {
                if (sim?.permissionGranted == false) {
                    Text("需要电话权限才能读取 SIM 卡槽、订阅 ID 和运营商名称。不会读取手机号或 IMSI。")
                }
                KeyValueRow("默认数据卡", current?.label ?: "未识别")
                KeyValueRow("运营商", current?.carrierName ?: current?.displayName ?: "不可用")
                KeyValueRow("当前网络", network?.transportLabel ?: "未知")
                KeyValueRow(
                    "移动数据",
                    when {
                        network?.path?.path == InternetPath.MOBILE -> "正在用于上网"
                        network?.path?.hasConcurrentBillableCellular == true -> "同时使用（双通道）"
                        network?.path?.isTethering == true -> "热点共享中"
                        sim?.mobileConnected == true -> "已连接，但不是当前上网路径（例如 IMS）"
                        else -> "未用于上网"
                    },
                )
                KeyValueRow("识别状态", current?.identificationStatus?.name ?: "UNKNOWN")
                KeyValueRow("订阅 ID", current?.subscriptionId?.toString() ?: "—")
                KeyValueRow("卡槽编号", current?.slotIndex?.toString() ?: "—")
                Text("订阅 ID 不是卡槽编号。", style = MaterialTheme.typography.bodySmall)
            }
        }

        item { SectionTitle("实时速度") }
        item {
            val sample = state.sample
            InfoCard(onClick = onOpenRealtime) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("下载（${sample?.displayPathLabel ?: "移动数据"}）", style = MaterialTheme.typography.labelMedium)
                        Text(ByteFormatter.formatSpeed(sample?.displayDownloadBytesPerSec), style = MaterialTheme.typography.headlineSmall)
                    }
                    Column {
                        Text("上传（${sample?.displayPathLabel ?: "移动数据"}）", style = MaterialTheme.typography.labelMedium)
                        Text(ByteFormatter.formatSpeed(sample?.displayUploadBytesPerSec), style = MaterialTheme.typography.headlineSmall)
                    }
                }
                KeyValueRow(
                    "最近 ${state.settings?.rollingWindowSeconds ?: 60} 秒移动流量",
                    ByteFormatter.formatBytes(state.rollingWindowBytes),
                )
                SpeedChart(state.samples)
                Text("蓝线为下载，黄线为上传，速度按当前上网路径显示。点击查看详情。采样来自 TrafficStats。", style = MaterialTheme.typography.bodySmall)
                if (sample?.mobileCountersUnreliable == true) {
                    Text("当前不是移动上网，但 TrafficStats 移动计数仍有增量，已忽略，不计入套餐与预警。", style = MaterialTheme.typography.bodySmall)
                }
                if (sample?.mobileCountersSupported == false) {
                    Text("本机移动计数器不可用，已改用全设备计数估算。", style = MaterialTheme.typography.bodySmall)
                }
                ConfidenceChip(sample?.confidence ?: DataConfidence.UNAVAILABLE)
            }
        }

        item { SectionTitle("今日流量") }
        item {
            val today = state.todayMobile
            InfoCard {
                if (today == null || today.confidence == DataConfidence.UNAVAILABLE) {
                    Text("今日全设备移动数据不可用。请在设置中授予“使用情况访问权限”。")
                    ConfidenceChip(DataConfidence.UNAVAILABLE, DataScope.DEVICE_WIDE)
                } else {
                    KeyValueRow("今日下载", ByteFormatter.formatBytes(today.volume.rxBytes))
                    KeyValueRow("今日上传", ByteFormatter.formatBytes(today.volume.txBytes))
                    KeyValueRow("今日合计", ByteFormatter.formatBytes(today.volume.total))
                    ConfidenceChip(today.confidence, DataScope.DEVICE_WIDE)
                    Text("这是 NetworkStatsManager 提供的全设备移动数据，不是某一张 SIM 卡的官方拆分。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item { SectionTitle("热点监控") }
        item {
            val hotspot = state.hotspot
            InfoCard {
                KeyValueRow("热点状态", if (hotspot?.isTethering == true) "共享中" else "未开启")
                KeyValueRow("接口", hotspot?.tetheredInterfaces?.joinToString().ifNullOrEmpty("—"))
                KeyValueRow("会话下载", ByteFormatter.formatBytes(hotspot?.session?.rxBytes))
                KeyValueRow("会话上传", ByteFormatter.formatBytes(hotspot?.session?.txBytes))
                Text(hotspot?.sourceLabel ?: "数据来源未知", style = MaterialTheme.typography.bodySmall)
                ConfidenceChip(hotspot?.confidence ?: DataConfidence.UNAVAILABLE, DataScope.HOTSPOT_SESSION)
                NoteText(hotspot?.notes.orEmpty())
            }
        }

        item { SectionTitle("最近预警") }
        item {
            InfoCard {
                if (state.recentAlerts.isEmpty()) {
                    Text("暂无预警。")
                } else {
                    state.recentAlerts.forEach { alert ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(alert.message, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                buildString {
                                    append(TimeUtils.formatDateTime(alert.triggeredAtMs))
                                    append(" · ")
                                    append(
                                        when (alert.type) {
                                            AlertType.HIGH_DOWNLOAD_SPEED -> "高速下载"
                                            AlertType.ROLLING_WINDOW -> "短时流量过大"
                                            AlertType.PLAN_USAGE -> "套餐使用率"
                                        },
                                    )
                                    alert.slotIndex?.let { append(" · SIM ").append(it + 1) }
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanOverviewCard(card: SimCard, plan: PlanUsage, onClick: () -> Unit) {
    val role = if (card.slotIndex == 1) "主要流量卡" else "备用流量卡"
    InfoCard(onClick = onClick) {
        Text("${card.label}  ·  $role", style = MaterialTheme.typography.titleSmall)
        Text(card.carrierName ?: card.displayName ?: "运营商未知", style = MaterialTheme.typography.bodySmall)
        KeyValueRow(
            if (plan.userCalibrated) "本周期已用（手动校准）" else "本周期已用",
            ByteFormatter.formatBytes(plan.usedBytes),
        )
        KeyValueRow("通用套餐", ByteFormatter.formatBytes(plan.generalCapacityBytes))
        KeyValueRow("定向套餐", ByteFormatter.formatBytes(plan.directedCapacityBytes))
        KeyValueRow(
            if (plan.userCalibrated) "通用剩余（手动校准）" else "通用剩余（估算）",
            ByteFormatter.formatBytes(plan.remainingGeneralBytes),
        )
        KeyValueRow("占用比例", ByteFormatter.formatPercent(plan.usagePercent))
        val percent = plan.usagePercent
        if (percent != null) {
            LinearProgressIndicator(
                progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ConfidenceChip(plan.usedConfidence, plan.usedScope, userCalibrated = plan.userCalibrated)
        NoteText(plan.notes.take(2))
    }
}

private fun String?.ifNullOrEmpty(fallback: String): String = if (this.isNullOrBlank()) fallback else this
