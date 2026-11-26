package com.netwatch.feature.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
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
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope
import com.netwatch.domain.model.NetworkKind
import com.netwatch.ui.appViewModel
import com.netwatch.ui.components.ConfidenceChip
import com.netwatch.ui.components.InfoCard
import com.netwatch.ui.components.KeyValueRow
import com.netwatch.ui.components.SectionTitle

@Composable
fun StatisticsScreen() {
    val container = LocalAppContainer.current
    val vm = appViewModel { StatisticsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("时间范围") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsRange.entries.forEach { range ->
                    if (range != StatsRange.CUSTOM) {
                        FilterChip(
                            selected = state.range == range,
                            onClick = { vm.setRange(range) },
                            label = {
                                Text(
                                    when (range) {
                                        StatsRange.TODAY -> "今日"
                                        StatsRange.YESTERDAY -> "昨日"
                                        StatsRange.LAST_7 -> "近 7 天"
                                        StatsRange.THIS_MONTH -> "本月"
                                        StatsRange.CUSTOM -> "自定义"
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NetworkKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = { vm.setKind(kind) },
                        label = {
                            Text(
                                when (kind) {
                                    NetworkKind.MOBILE -> "移动"
                                    NetworkKind.WIFI -> "Wi-Fi"
                                    NetworkKind.TOTAL -> "合计"
                                },
                            )
                        },
                    )
                }
            }
        }

        item { SectionTitle("流量概览（全设备）") }
        item {
            val overview = state.overview
            InfoCard {
                if (!state.usageAccessGranted) {
                    Text("未授予使用情况访问权限，系统统计不可用。可在设置中开启。")
                    ConfidenceChip(DataConfidence.UNAVAILABLE, DataScope.DEVICE_WIDE)
                } else if (overview == null) {
                    Text(if (state.loading) "正在读取系统统计…" else "暂无数据")
                } else {
                    KeyValueRow("下载", ByteFormatter.formatBytes(overview.volume.rxBytes))
                    KeyValueRow("上传", ByteFormatter.formatBytes(overview.volume.txBytes))
                    KeyValueRow("合计", ByteFormatter.formatBytes(overview.volume.total))
                    ConfidenceChip(overview.confidence, DataScope.DEVICE_WIDE)
                    Text("系统只提供全设备统计。下面不会把这些数字拆成虚假的 SIM 1 / SIM 2 数据。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item { SectionTitle("每日趋势（全设备）") }
        item {
            InfoCard {
                if (state.daily.isEmpty()) {
                    Text("所选范围内没有已同步的每日数据。")
                } else {
                    val max = state.daily.maxOf { it.rx + it.tx }.coerceAtLeast(1L)
                    state.daily.forEach { point ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            KeyValueRow(
                                TimeUtils.formatDate(point.date),
                                "↓ ${ByteFormatter.formatBytes(point.rx)}  ↑ ${ByteFormatter.formatBytes(point.tx)}",
                            )
                            LinearProgressIndicator(
                                progress = { ((point.rx + point.tx).toFloat() / max).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                            )
                        }
                    }
                }
            }
        }

        item { SectionTitle("双卡归属（估算）") }
        item {
            InfoCard {
                Text("以下用量按“当时默认数据卡”归属移动数据增量，不是运营商官方分卡账单。应用未运行的时段不会被计入。", style = MaterialTheme.typography.bodySmall)
                if (state.attributed.isEmpty()) {
                    Text("所选范围内还没有归属记录。打开实时监控并保持后台运行后才会积累。")
                } else {
                    state.attributed.forEach { row ->
                        val title = row.slotIndex?.let { "SIM ${it + 1}" } ?: "未知卡槽"
                        KeyValueRow(
                            "$title · ${row.identityKey}",
                            ByteFormatter.formatBytes(row.rx + row.tx),
                        )
                    }
                    ConfidenceChip(DataConfidence.ESTIMATED, DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM)
                }
            }
        }

        item { SectionTitle("应用流量排行") }
        item {
            if (state.apps.isEmpty()) {
                InfoCard { Text(if (state.usageAccessGranted) "该范围内没有应用统计。" else "需要使用情况访问权限。") }
            }
        }
        items(state.apps.take(30), key = { it.uid }) { app ->
            val total = state.apps.sumOf { it.total }.coerceAtLeast(1L)
            InfoCard {
                Text(app.label, style = MaterialTheme.typography.titleSmall)
                if (app.packageName != null && app.packageName != app.label) {
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                }
                KeyValueRow("下载", ByteFormatter.formatBytes(app.rxBytes))
                KeyValueRow("上传", ByteFormatter.formatBytes(app.txBytes))
                KeyValueRow("合计", ByteFormatter.formatBytes(app.total))
                KeyValueRow("占比", ByteFormatter.formatPercent(app.total.toDouble() / total * 100.0))
                ConfidenceChip(app.confidence, app.scope)
            }
        }

        item { SectionTitle("热点历史") }
        item {
            if (state.sessions.isEmpty()) {
                InfoCard { Text("还没有热点会话记录。") }
            }
        }
        items(state.sessions, key = { it.id }) { session ->
            InfoCard {
                KeyValueRow("开始", TimeUtils.formatDateTime(session.startedAtMs))
                KeyValueRow("结束", session.endedAtMs?.let { TimeUtils.formatDateTime(it) } ?: "进行中")
                KeyValueRow("下载", ByteFormatter.formatBytes(session.rxBytes))
                KeyValueRow("上传", ByteFormatter.formatBytes(session.txBytes))
                KeyValueRow("接口", session.interfaces.joinToString().ifEmpty { "—" })
                session.defaultDataSlotIndex?.let { KeyValueRow("当时默认卡", "SIM ${it + 1}") }
                ConfidenceChip(session.confidence, DataScope.HOTSPOT_SESSION)
            }
        }

        item { SectionTitle("预警历史") }
        item {
            if (state.alerts.isEmpty()) {
                InfoCard { Text("暂无预警。") }
            }
        }
        items(state.alerts, key = { it.id }) { alert ->
            InfoCard {
                Text(alert.message, style = MaterialTheme.typography.titleSmall)
                KeyValueRow(
                    "类型",
                    when (alert.type) {
                        AlertType.HIGH_DOWNLOAD_SPEED -> "高速下载"
                        AlertType.ROLLING_WINDOW -> "短时流量过大"
                        AlertType.PLAN_USAGE -> "套餐使用率"
                    },
                )
                KeyValueRow("时间", TimeUtils.formatDateTime(alert.triggeredAtMs))
                alert.triggerSpeedBytesPerSec?.let { KeyValueRow("触发速度", ByteFormatter.formatSpeed(it)) }
                alert.triggerVolumeBytes?.let { KeyValueRow("触发流量", ByteFormatter.formatBytes(it)) }
                alert.planPercent?.let { KeyValueRow("套餐占用", ByteFormatter.formatPercent(it)) }
                KeyValueRow("SIM 卡", alert.slotIndex?.let { "SIM ${it + 1}" } ?: "无法确认")
                ConfidenceChip(alert.confidence)
            }
        }
    }
}
