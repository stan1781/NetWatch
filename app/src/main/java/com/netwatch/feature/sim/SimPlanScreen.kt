package com.netwatch.feature.sim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netwatch.core.util.ByteFormatter
import com.netwatch.core.util.TimeUtils
import com.netwatch.di.LocalAppContainer
import com.netwatch.domain.calculator.BillingCycleCalculator
import com.netwatch.domain.calculator.PlanCalculator
import com.netwatch.domain.model.PlanConfig
import com.netwatch.domain.model.PlanUsage
import com.netwatch.domain.model.SimIdentificationStatus
import com.netwatch.domain.model.SimRole
import com.netwatch.feature.settings.ToggleRow
import com.netwatch.ui.components.ConfidenceChip
import com.netwatch.ui.components.InfoCard
import com.netwatch.ui.components.KeyValueRow
import com.netwatch.ui.components.NoteText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.ZoneId

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
    var remainingGeneral by remember(current.calibratedUsedBytes, current.calibrationAtMs, current.generalCapacityBytes) {
        val remainingBytes = if (current.calibratedUsedBytes != null && current.generalCapacityBytes != null) {
            (current.generalCapacityBytes - current.calibratedUsedBytes).coerceAtLeast(0L)
        } else {
            null
        }
        mutableStateOf(ByteFormatter.toGigabytesString(remainingBytes))
    }

    val plansUsage = remember { MutableStateFlow(emptyList<PlanUsage>()) }
    LaunchedEffect(slotIndex) {
        val sim = container.simManager.current()
        plansUsage.value = container.observePlanUsageUseCase.compute(sim)
    }
    val usage = plansUsage.collectAsStateWithLifecycle().value.firstOrNull { it.slotIndex == slotIndex }

    fun save(transform: (PlanConfig) -> PlanConfig) {
        CoroutineScope(Dispatchers.IO).launch {
            container.settingsRepository.updatePlan(slotIndex) { transform(it) }
        }
    }

    fun saveCapacityResetAndCalibration() {
        // Only persist when the user taps save. Do not auto-save on leave.
        CoroutineScope(Dispatchers.IO).launch {
            val generalBytes = ByteFormatter.parseGigabytes(general)
            val directedBytes = ByteFormatter.parseGigabytes(directed)
            val reset = resetDay.trim().toIntOrNull()?.takeIf { day -> day in 1..31 }
            val remainingBytes = ByteFormatter.parseGigabytes(remainingGeneral)
            val manualUsed = PlanCalculator.usedFromRemaining(generalBytes, remainingBytes)
            val zone = ZoneId.systemDefault()
            val today = TimeUtils.today()
            val cycle = BillingCycleCalculator.currentCycle(today, reset, zone)
            val startMs = cycle.start.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = cycle.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
            val sim = container.simManager.current()
            val card = sim.cards.firstOrNull { it.slotIndex == slotIndex }
            val identity = card?.identityKey
            val attributed = if (identity != null && card.identificationStatus == SimIdentificationStatus.IDENTIFIED) {
                container.networkStatsRepository.attributedUsage(identity, startMs, endMs)?.total
            } else {
                null
            }
            val now = System.currentTimeMillis()
            val calibrating = manualUsed != null
            container.settingsRepository.updatePlan(slotIndex) {
                it.copy(
                    generalCapacityBytes = generalBytes,
                    directedCapacityBytes = directedBytes,
                    resetDayOfMonth = reset,
                    calibratedUsedBytes = manualUsed,
                    calibratedDirectedUsedBytes = null,
                    calibrationAtMs = if (calibrating) now else null,
                    calibrationBaselineAttributedBytes = if (calibrating) (attributed ?: 0L) else null,
                )
            }
            plansUsage.value = container.observePlanUsageUseCase.compute(sim)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    OutlinedTextField(
                        value = remainingGeneral,
                        onValueChange = { remainingGeneral = it },
                        label = { Text("通用剩余流量（GB，手动校准，留空则仅用估算）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    TextButton(onClick = { saveCapacityResetAndCalibration() }) {
                        Text("保存容量、重置日与校准")
                    }
                    Text(
                        "填写当前通用剩余流量，并与通用容量一起保存。保存时按「已用 = 容量 − 剩余」校准，之后剩余会随监控到的增量减少。这不是运营商或系统官方数据。公开 API 无法看到定向流量，因此只需校准通用剩余。留空剩余并保存则恢复为仅估算。未设置容量时无法校准。新计费周期开始时会自动清除校准。离开本页不会自动保存容量、重置日或校准。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val calibratedAt = current.calibrationAtMs
                    if (calibratedAt != null && current.calibratedUsedBytes != null) {
                        Text(
                            "上次校准时间：${TimeUtils.formatDateTime(calibratedAt)}。新计费周期开始后将自动清除。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("公开 API 无法区分定向流量。未校准前，已用流量是全部移动数据（按默认数据卡归属的估算），剩余通用流量仅为估算。", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                InfoCard {
                    Text("本周期用量", style = MaterialTheme.typography.titleSmall)
                    KeyValueRow(
                        if (usage?.userCalibrated == true) "已用（手动校准）" else "已用",
                        ByteFormatter.formatBytes(usage?.usedBytes),
                    )
                    if (usage?.userCalibrated == true && usage.calibratedBaseUsedBytes != null) {
                        val remainingAtCalibration = usage.generalCapacityBytes?.let { cap ->
                            (cap - usage.calibratedBaseUsedBytes).coerceAtLeast(0L)
                        }
                        KeyValueRow("校准时尚余", ByteFormatter.formatBytes(remainingAtCalibration))
                        KeyValueRow("校准后增量（估算）", ByteFormatter.formatBytes(usage.attributedAfterCalibrationBytes))
                    }
                    KeyValueRow(
                        if (usage?.userCalibrated == true) "通用剩余（手动校准）" else "通用剩余（估算）",
                        ByteFormatter.formatBytes(usage?.remainingGeneralBytes),
                    )
                    KeyValueRow("占用", ByteFormatter.formatPercent(usage?.usagePercent))
                    usage?.let {
                        ConfidenceChip(it.usedConfidence, it.usedScope, userCalibrated = it.userCalibrated)
                        NoteText(it.notes)
                    }
                }
            }
        }
    }
}
