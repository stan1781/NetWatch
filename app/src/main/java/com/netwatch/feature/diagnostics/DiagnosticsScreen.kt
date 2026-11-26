package com.netwatch.feature.diagnostics

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.netwatch.BuildConfig
import com.netwatch.di.LocalAppContainer
import com.netwatch.service.TrafficMonitorService
import com.netwatch.ui.components.InfoCard
import com.netwatch.ui.components.KeyValueRow
import com.netwatch.ui.components.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val permissions = container.permissionChecker
    var probe by remember { mutableStateOf("正在检测…") }
    var simText by remember { mutableStateOf("正在检测…") }

    LaunchedEffect(Unit) {
        probe = withContext(Dispatchers.IO) { container.networkStatsRepository.probe() }
        val sim = container.simManager.current()
        simText = buildString {
            append("默认数据订阅 ID：${sim.defaultDataSubscriptionId ?: "无"}\n")
            append("默认数据卡槽：${sim.defaultDataSlotIndex?.let { it + 1 } ?: "未识别"}\n")
            sim.cards.forEach { card ->
                append("SIM ${card.slotIndex + 1}: 订阅=${card.subscriptionId ?: "无"} 运营商=${card.carrierName ?: "未知"} 状态=${card.identificationStatus}\n")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系统 API 诊断") },
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
            item { SectionTitle("环境") }
            item {
                InfoCard {
                    KeyValueRow("应用版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    KeyValueRow("Android SDK", Build.VERSION.SDK_INT.toString())
                    KeyValueRow("设备", "${Build.MANUFACTURER} ${Build.MODEL}")
                    KeyValueRow("发行版", Build.DISPLAY)
                }
            }
            item { SectionTitle("权限") }
            item {
                InfoCard {
                    KeyValueRow("使用情况访问", yesNo(permissions.hasUsageAccess()))
                    KeyValueRow("READ_PHONE_STATE", yesNo(permissions.hasReadPhoneState()))
                    KeyValueRow("通知权限", yesNo(permissions.hasNotifications()))
                    KeyValueRow("通知总开关", yesNo(permissions.areNotificationsEnabled()))
                    KeyValueRow("忽略电池优化", yesNo(permissions.isIgnoringBatteryOptimizations()))
                    KeyValueRow("前台服务", if (TrafficMonitorService.running.value) "运行中" else "未运行")
                }
            }
            item { SectionTitle("NetworkStatsManager") }
            item { InfoCard { Text(probe) } }
            item { SectionTitle("SIM / 订阅") }
            item { InfoCard { Text(simText) } }
            item { SectionTitle("能力说明") }
            item {
                InfoCard {
                    Text("● 全设备移动 / Wi-Fi / 分应用统计：在授予使用情况访问后，来自 NetworkStatsManager，标记为“实测”。")
                    Text("● 按 SIM 卡拆分：公开 API 不能向第三方应用提供 subscriberId，也无法按订阅查询官方账单。本应用按默认数据卡期间的移动数据增量归属，标记为“估算”。")
                    Text("● 定向 / 不计费流量：公开 API 不可用。")
                    Text("● 实时速度：TrafficStats 计数差，标记为“实测”。切换默认数据卡会重置基线。")
                    Text("● 热点：TetheringManager 识别开关与接口；会话流量按开启期间移动数据增量估算。")
                    Text("● 不会读取 IMSI 或电话号码。")
                }
            }
        }
    }
}

private fun yesNo(value: Boolean) = if (value) "是" else "否"
