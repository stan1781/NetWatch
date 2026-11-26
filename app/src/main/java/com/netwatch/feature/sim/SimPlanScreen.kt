package com.netwatch.feature.sim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netwatch.core.util.ByteFormatter
import com.netwatch.di.LocalAppContainer
import com.netwatch.domain.model.PlanConfig
import com.netwatch.domain.model.SimRole
import com.netwatch.feature.settings.ToggleRow
import com.netwatch.ui.components.ConfidenceChip
import com.netwatch.ui.components.InfoCard
import com.netwatch.ui.components.KeyValueRow
import com.netwatch.ui.components.NoteText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimPlanScreen(slotIndex: Int, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val planFlow = remember(slotIndex) {
        container.settingsRepository.settings.map { it.simPlans[slotIndex] }
    }
    val plan by planFlow.collectAsStateWithLifecycle(initialValue = null)
    val current = plan ?: return
    var general by remember(current.generalCapacityBytes) { mutableStateOf(ByteFormatter.toGigabytesString(current.generalCapacityBytes)) }
    var directed by remember(current.directedCapacityBytes) { mutableStateOf(ByteFormatter.toGigabytesString(current.directedCapacityBytes)) }
    var resetDay by remember(current.resetDayOfMonth) { mutableStateOf(current.resetDayOfMonth?.toString().orEmpty()) }

    val plansUsage = remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.netwatch.domain.model.PlanUsage>()) }
    androidx.compose.runtime.LaunchedEffect(slotIndex) {
        val sim = container.simManager.current()
        plansUsage.value = container.observePlanUsageUseCase.compute(sim)
    }
    val usage = plansUsage.collectAsStateWithLifecycle().value.firstOrNull { it.slotIndex == slotIndex }

    fun save(transform: (PlanConfig) -> PlanConfig) {
        CoroutineScope(Dispatchers.IO).launch {
            container.settingsRepository.updatePlan(slotIndex) { transform(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SIM ${slotIndex + 1} 套餐设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoCard {
                    Text("角色")
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = current.role == SimRole.PRIMARY,
                            onClick = { save { it.copy(role = SimRole.PRIMARY) } },
                            label = { Text("主要流量卡") },
                        )
                        FilterChip(
                            selected = current.role == SimRole.BACKUP,
                            onClick = { save { it.copy(role = SimRole.BACKUP) } },
                            label = { Text("备用流量卡") },
                        )
                    }
                    ToggleRow("套餐监控", current.monitoringEnabled) { enabled -> save { it.copy(monitoringEnabled = enabled) } }
                    OutlinedTextField(
                        value = general,
                        onValueChange = { general = it },
                        label = { Text("通用流量容量（GB，留空表示未设置）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = directed,
                        onValueChange = { directed = it },
                        label = { Text("定向流量容量（GB，留空表示未设置）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = resetDay,
                        onValueChange = { resetDay = it },
                        label = { Text("每月重置日（1–31，留空表示未设置）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    TextButton(
                        onClick = {
                            save {
                                it.copy(
                                    generalCapacityBytes = ByteFormatter.parseGigabytes(general),
                                    directedCapacityBytes = ByteFormatter.parseGigabytes(directed),
                                    resetDayOfMonth = resetDay.trim().toIntOrNull()?.takeIf { day -> day in 1..31 },
                                )
                            }
                        },
                    ) { Text("保存容量与重置日") }
                    Text("公开 API 无法区分定向流量。已用流量是全部移动数据（按默认数据卡归属的估算），剩余通用流量仅为估算。", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                InfoCard {
                    Text("本周期用量", style = MaterialTheme.typography.titleSmall)
                    KeyValueRow("已用", ByteFormatter.formatBytes(usage?.usedBytes))
                    KeyValueRow("剩余（估算）", ByteFormatter.formatBytes(usage?.remainingGeneralBytes))
                    KeyValueRow("占用", ByteFormatter.formatPercent(usage?.usagePercent))
                    usage?.let {
                        ConfidenceChip(it.usedConfidence, it.usedScope)
                        NoteText(it.notes)
                    }
                }
            }
        }
    }
}
