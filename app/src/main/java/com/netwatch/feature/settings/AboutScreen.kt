package com.netwatch.feature.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.netwatch.BuildConfig
import com.netwatch.ui.components.InfoCard
import com.netwatch.ui.components.KeyValueRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于 NetWatch") },
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
                    KeyValueRow("应用版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    KeyValueRow("系统版本", "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                    KeyValueRow("设备", "${Build.MANUFACTURER} ${Build.MODEL}")
                    KeyValueRow("开源许可证", "Apache License 2.0")
                    Text("NetWatch 是个人双 SIM 流量与实时网络监控工具。数据只保存在本机，不连接服务器，也不收集与流量监控无关的个人信息。", style = MaterialTheme.typography.bodyMedium)
                    Text("原则：数据真实性优先于功能数量。无法从官方公开 API 获得的数据会标记为不可用或估算，绝不用假数据冒充分卡统计。", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
