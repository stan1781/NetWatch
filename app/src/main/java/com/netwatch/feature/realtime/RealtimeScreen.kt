package com.netwatch.feature.realtime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netwatch.core.util.ByteFormatter
import com.netwatch.di.LocalAppContainer
import com.netwatch.domain.model.DataConfidence
import com.netwatch.service.TrafficMonitorService
import com.netwatch.ui.components.ConfidenceChip
import com.netwatch.ui.components.InfoCard
import com.netwatch.ui.components.KeyValueRow
import com.netwatch.ui.components.SpeedChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealtimeScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val sample by TrafficMonitorService.latestSample.collectAsStateWithLifecycle()
    val samples by TrafficMonitorService.samples.collectAsStateWithLifecycle()
    val running by TrafficMonitorService.running.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val window = settings?.rollingWindowSeconds ?: 60
    val cutoff = (sample?.elapsedRealtimeMs ?: 0) - window * 1000L
    val rolling = samples.filter { it.elapsedRealtimeMs >= cutoff }.sumOf { it.mobileDelta.total }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("实时监控") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoCard {
                    Text(if (running) "前台服务运行中" else "监控服务未运行，请在设置中打开实时监控。")
                    KeyValueRow("采样间隔", "${settings?.sampleIntervalMs ?: "—"} ms")
                    KeyValueRow("默认数据卡槽", sample?.defaultDataSlotIndex?.let { "SIM ${it + 1}" } ?: "未知")
                    KeyValueRow("订阅 ID", sample?.defaultDataSubscriptionId?.toString() ?: "—")
                    KeyValueRow("移动下载", ByteFormatter.formatSpeed(sample?.downloadBytesPerSec))
                    KeyValueRow("移动上传", ByteFormatter.formatSpeed(sample?.uploadBytesPerSec))
                    KeyValueRow("全设备下载", ByteFormatter.formatSpeed(sample?.totalDownloadBytesPerSec))
                    KeyValueRow("全设备上传", ByteFormatter.formatSpeed(sample?.totalUploadBytesPerSec))
                    KeyValueRow("最近 ${window} 秒移动流量", ByteFormatter.formatBytes(rolling))
                    SpeedChart(samples)
                    ConfidenceChip(sample?.confidence ?: DataConfidence.UNAVAILABLE)
                    Text("速度来自 TrafficStats 计数差。切换默认数据卡时会重置基线，避免出现虚假尖峰。预警阈值请到设置中修改。", style = MaterialTheme.typography.bodySmall)
                    if (sample?.mobileCountersSupported == false) {
                        Text("本机不支持移动计数器。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
