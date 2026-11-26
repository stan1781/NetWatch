package com.netwatch.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netwatch.core.permissions.PermissionChecker
import com.netwatch.core.util.ByteFormatter
import com.netwatch.di.LocalAppContainer
import com.netwatch.ui.appViewModel
import com.netwatch.ui.components.InfoCard
import com.netwatch.ui.components.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onOpenSimPlan: (Int) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = appViewModel { SettingsViewModel(container) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val running by vm.serviceRunning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = container.permissionChecker
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    val current = settings ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("SIM 卡套餐设置") }
        item {
            InfoCard {
                Text("套餐容量、重置日和角色在此进入各卡页面配置。不要把全设备流量当成某一张卡的官方用量。")
                Button(onClick = { onOpenSimPlan(0) }, modifier = Modifier.fillMaxWidth()) { Text("SIM 1 套餐设置（备用）") }
                Button(onClick = { onOpenSimPlan(1) }, modifier = Modifier.fillMaxWidth()) { Text("SIM 2 套餐设置（主要）") }
            }
        }

        item { SectionTitle("流量监控设置") }
        item {
            InfoCard {
                ToggleRow("实时监控", current.realtimeMonitoringEnabled) { vm.setMonitoring(it, context) }
                ToggleRow("下载速度监控", current.downloadSpeedMonitoringEnabled) { vm.update { s -> s.copy(downloadSpeedMonitoringEnabled = it) } }
                ToggleRow("上传速度监控", current.uploadSpeedMonitoringEnabled) { vm.update { s -> s.copy(uploadSpeedMonitoringEnabled = it) } }
                ToggleRow("热点监控", current.hotspotMonitoringEnabled) { vm.update { s -> s.copy(hotspotMonitoringEnabled = it) } }
                NumberRow("采样间隔（毫秒）", current.sampleIntervalMs.toString()) { raw ->
                    raw.toIntOrNull()?.coerceIn(500, 10_000)?.let { value -> vm.update { s -> s.copy(sampleIntervalMs = value) } }
                }
                NumberRow("滚动窗口（秒）", current.rollingWindowSeconds.toString()) { raw ->
                    raw.toIntOrNull()?.coerceIn(10, 600)?.let { value -> vm.update { s -> s.copy(rollingWindowSeconds = value) } }
                }
            }
        }

        item { SectionTitle("流量预警设置") }
        item {
            InfoCard {
                NumberRow("高速下载阈值（MB/s）", (current.downloadAlertBytesPerSec / (1024.0 * 1024.0)).let { if (it == it.toLong().toDouble()) it.toLong().toString() else "%.2f".format(it) }) { raw ->
                    raw.toDoubleOrNull()?.takeIf { it > 0 }?.let { mb ->
                        vm.update { s -> s.copy(downloadAlertBytesPerSec = (mb * 1024 * 1024).toLong()) }
                    }
                }
                NumberRow("持续秒数", current.downloadAlertDurationSec.toString()) { raw ->
                    raw.toIntOrNull()?.coerceIn(1, 60)?.let { value -> vm.update { s -> s.copy(downloadAlertDurationSec = value) } }
                }
                NumberRow("滚动窗口流量阈值（MB）", (current.rollingAlertBytes / (1024.0 * 1024.0)).toLong().toString()) { raw ->
                    raw.toDoubleOrNull()?.takeIf { it > 0 }?.let { mb ->
                        vm.update { s -> s.copy(rollingAlertBytes = (mb * 1024 * 1024).toLong()) }
                    }
                }
                NumberRow("套餐使用率预警（%）", current.planUsageAlertPercent.toString()) { raw ->
                    raw.toIntOrNull()?.coerceIn(1, 100)?.let { value -> vm.update { s -> s.copy(planUsageAlertPercent = value) } }
                }
            }
        }

        item { SectionTitle("通知设置") }
        item {
            InfoCard {
                ToggleRow("通知总开关", current.notificationsEnabled) { vm.update { s -> s.copy(notificationsEnabled = it) } }
                ToggleRow("通知声音", current.notificationSound) { vm.update { s -> s.copy(notificationSound = it) } }
                ToggleRow("通知振动", current.notificationVibrate) { vm.update { s -> s.copy(notificationVibrate = it) } }
                NumberRow("冷却时间（秒）", current.notificationCooldownSec.toString()) { raw ->
                    raw.toIntOrNull()?.coerceIn(0, 3600)?.let { value -> vm.update { s -> s.copy(notificationCooldownSec = value) } }
                }
                Text("声音和振动在系统通知渠道中生效；更改后会使用新的渠道配置。", style = MaterialTheme.typography.bodySmall)
            }
        }

        item { SectionTitle("后台运行设置") }
        item {
            InfoCard {
                ToggleRow("后台监控", current.backgroundMonitoringEnabled) { vm.setBackground(it, context) }
                Text("前台服务状态：${if (running) "运行中" else "未运行"}")
                Text("电池优化豁免：${if (permissions.isIgnoringBatteryOptimizations()) "已忽略" else "未忽略"}")
                OutlinedButton(onClick = { context.startActivity(permissions.batteryOptimizationIntent()) }) {
                    Text("请求忽略电池优化")
                }
                Text("HyperOS 可能还有“自启动”“后台活动”等额外限制，需在系统设置中为 NetWatch 打开，否则息屏后可能停止采样。", style = MaterialTheme.typography.bodySmall)
            }
        }

        item { SectionTitle("数据与权限") }
        item {
            InfoCard {
                Text("使用情况访问：${if (permissions.hasUsageAccess()) "已授予" else "未授予"}")
                OutlinedButton(onClick = { context.startActivity(permissions.usageAccessSettingsIntent()) }) { Text("打开使用情况访问设置") }
                Text("SIM 信息（READ_PHONE_STATE）：${if (permissions.hasReadPhoneState()) "已授予" else "未授予"}")
                Text("通知权限：${if (permissions.hasNotifications() && permissions.areNotificationsEnabled()) "已授予" else "未授予或被关闭"}")
                OutlinedButton(onClick = { context.startActivity(permissions.appNotificationSettingsIntent()) }) { Text("打开通知设置") }
                Button(onClick = onOpenDiagnostics) { Text("系统 API 诊断") }
                OutlinedButton(onClick = { scope.launch { runCatching { vm.export(context) } } }) { Text("导出本地数据") }
                OutlinedButton(onClick = { confirmClear = true }) { Text("清理本地数据") }
            }
        }

        item { SectionTitle("关于") }
        item {
            InfoCard(onClick = onOpenAbout) {
                Text("查看版本、系统信息和项目说明")
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清理本地数据？") },
            text = { Text("将删除历史统计、预警、热点会话和 SIM 身份记录。设置不会被清除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearData()
                    confirmClear = false
                    Toast.makeText(context, "已清理", Toast.LENGTH_SHORT).show()
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
fun NumberRow(label: String, value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { onCommit(text) }) { Text("保存") }
    }
}
