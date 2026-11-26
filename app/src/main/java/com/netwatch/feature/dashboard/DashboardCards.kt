package com.netwatch.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netwatch.core.system.NetworkState
import com.netwatch.core.util.ByteFormatter
import com.netwatch.core.util.FormattedQuantity
import com.netwatch.core.util.TimeUtils
import com.netwatch.domain.model.AlertEvent
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope
import com.netwatch.domain.model.HotspotStatus
import com.netwatch.domain.model.InternetPath
import com.netwatch.domain.model.PlanUsage
import com.netwatch.domain.model.SimCard
import com.netwatch.domain.model.SimIdentificationStatus
import com.netwatch.domain.model.SimRole
import com.netwatch.domain.model.SimSnapshot
import com.netwatch.domain.model.SpeedSample
import com.netwatch.domain.model.TrafficPeriod
import com.netwatch.ui.components.InfoCard
import com.netwatch.ui.components.SpeedChart
import com.netwatch.ui.components.StatusChip

private val CompactCardPadding = PaddingValues(12.dp)
private val TightCardPadding = PaddingValues(8.dp)

@Composable
private fun dashboardCardPadding(): PaddingValues {
    val width = LocalConfiguration.current.screenWidthDp
    return if (width < 360) TightCardPadding else CompactCardPadding
}

@Composable
private fun heroMaxSize(): TextUnit {
    val width = LocalConfiguration.current.screenWidthDp
    return if (width < 360) 22.sp else 26.sp
}

@Composable
private fun heroUnitSize(): TextUnit {
    val width = LocalConfiguration.current.screenWidthDp
    return if (width < 360) 12.sp else 14.sp
}

@Composable
fun DualSimOverview(
    plans: List<PlanUsage>,
    cards: List<SimCard>,
    roles: Map<Int, SimRole>,
    onOpenSimPlan: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(0, 1).forEach { slot ->
            val plan = plans.firstOrNull { it.slotIndex == slot }
            val card = cards.firstOrNull { it.slotIndex == slot }
            SimUsageCard(
                slotIndex = slot,
                card = card,
                plan = plan,
                role = roles[slot],
                onOpenPlan = { onOpenSimPlan(slot) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
fun SimUsageCard(
    slotIndex: Int,
    card: SimCard?,
    plan: PlanUsage?,
    role: SimRole?,
    onOpenPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width = LocalConfiguration.current.screenWidthDp
    val roleLabel = DashboardPresentation.roleShortLabel(role)
    var menuOpen by remember { mutableStateOf(false) }
    val remaining = ByteFormatter.formatBytesParts(plan?.remainingGeneralBytes)
    val usedLabel = DashboardPresentation.formatUsedPercent(plan?.usagePercent)
    val chip = when {
        card?.identificationStatus == SimIdentificationStatus.ABSENT -> "不可用"
        card?.identificationStatus == SimIdentificationStatus.PERMISSION_REQUIRED -> "不可用"
        plan == null -> "不可用"
        plan.remainingGeneralBytes == null && plan.usedBytes == null && plan.generalCapacityBytes == null -> "不可用"
        else -> DashboardPresentation.compactConfidenceLabel(
            confidence = plan.usedConfidence,
            userCalibrated = plan.userCalibrated,
        )
    }
    val carrier = when (card?.identificationStatus) {
        SimIdentificationStatus.ABSENT -> "未插入"
        SimIdentificationStatus.PERMISSION_REQUIRED -> "需要电话权限"
        else -> card?.carrierName ?: card?.displayName ?: "运营商未知"
    }
    InfoCard(
        modifier = modifier,
        onClick = onOpenPlan,
        contentPadding = dashboardCardPadding(),
        verticalSpacing = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SIM ${slotIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (roleLabel.isNotEmpty()) {
                Text(
                    text = " · $roleLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(if (width < 360) 28.dp else 32.dp),
            ) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "更多",
                    modifier = Modifier.size(18.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("手动校准") },
                        onClick = {
                            menuOpen = false
                            onOpenPlan()
                        },
                    )
                }
            }
        }
        Text(
            text = carrier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        HeroQuantity(
            parts = remaining,
            maxValueSize = heroMaxSize(),
            unitSize = heroUnitSize(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "已用 $usedLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            LinearProgressIndicator(
                progress = { DashboardPresentation.usedProgress(plan?.usagePercent) },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp),
            )
        }
        Text(
            text = "已用 ${ByteFormatter.formatBytes(plan?.usedBytes)} / ${ByteFormatter.formatBytes(plan?.generalCapacityBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        StatusChip(chip)
    }
}

@Composable
fun CurrentDataSimCard(
    sim: SimSnapshot?,
    network: NetworkState?,
    roles: Map<Int, SimRole>,
    modifier: Modifier = Modifier,
) {
    val current = sim?.cards?.firstOrNull { it.isDefaultData }
        ?: sim?.defaultDataSlotIndex?.let { slot -> sim.cards.firstOrNull { it.slotIndex == slot } }
    val roleLabel = DashboardPresentation.roleLongLabel(current?.slotIndex?.let { roles[it] })
    val carrier = current?.carrierName ?: current?.displayName
    val subtitle = listOfNotNull(carrier, roleLabel.takeIf { it.isNotEmpty() }).joinToString(" · ")
    val simLabel = current?.label ?: "未识别"
    val pathLabel = network?.transportLabel ?: "未知"
    val connection = DashboardPresentation.connectionStatusLabel(
        hasInternet = network?.hasInternet == true,
        isValidated = network?.isValidated == true,
        path = network?.path?.path,
    )

    InfoCard(
        modifier = modifier,
        contentPadding = dashboardCardPadding(),
        verticalSpacing = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "当前数据卡",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = simLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (sim?.permissionGranted == false) {
                    Text(
                        text = "需要电话权限才能识别 SIM 卡。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = connection,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = pathLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
            }
        }
    }
}

@Composable
fun RealtimeSpeedCard(
    sample: SpeedSample?,
    samples: List<SpeedSample>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val download = ByteFormatter.formatSpeedParts(sample?.displayDownloadBytesPerSec)
    val upload = ByteFormatter.formatSpeedParts(sample?.displayUploadBytesPerSec)
    val unmetered = isUnmeteredDisplayPath(sample?.path?.path)
    val chip = DashboardPresentation.speedSourceLabel(
        confidence = sample?.confidence ?: DataConfidence.UNAVAILABLE,
        onUnmeteredPath = unmetered,
    )
    InfoCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = dashboardCardPadding(),
        verticalSpacing = 6.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "实时网速",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            StatusChip(chip)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeedColumn(
                icon = Icons.Outlined.Download,
                label = "下载",
                parts = download,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            SpeedColumn(
                icon = Icons.Outlined.Upload,
                label = "上传",
                parts = upload,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
        }
        SpeedChart(samples = samples, height = 64.dp)
        if (sample?.mobileCountersUnreliable == true) {
            Text(
                text = "非移动上网时的移动计数已忽略。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (sample?.mobileCountersSupported == false) {
            Text(
                text = "本机移动计数器不可用，已改用全设备计数估算。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpeedColumn(
    icon: ImageVector,
    label: String,
    parts: FormattedQuantity,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HeroQuantity(
            parts = parts,
            maxValueSize = heroMaxSize(),
            unitSize = heroUnitSize(),
            valueColor = color,
        )
    }
}

@Composable
fun TodayTrafficCard(
    today: TrafficPeriod?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unavailable = today == null || today.confidence == DataConfidence.UNAVAILABLE
    val chip = if (unavailable) {
        "不可用"
    } else {
        DashboardPresentation.compactConfidenceLabel(
            confidence = today.confidence,
            scope = DataScope.DEVICE_WIDE,
            includeScope = true,
        )
    }
    InfoCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = dashboardCardPadding(),
        verticalSpacing = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "今日流量",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            StatusChip(chip)
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "查看统计",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (unavailable) {
            Text(
                text = "今日全设备移动数据不可用，请在设置中授予使用情况访问权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            HeroQuantity(
                parts = ByteFormatter.formatBytesParts(today.volume.total),
                maxValueSize = 28.sp,
                unitSize = 16.sp,
            )
            Text(
                text = "下载 ${ByteFormatter.formatBytes(today.volume.rxBytes)} ｜ 上传 ${ByteFormatter.formatBytes(today.volume.txBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun HotspotStatusCard(
    hotspot: HotspotStatus?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val on = hotspot?.isTethering == true
    val session = hotspot?.session
    val total = session?.let { it.rxBytes + it.txBytes }
    val trafficAvailable = on && session != null
    InfoCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = dashboardCardPadding(),
        verticalSpacing = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "热点监控",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (on) {
                StatusChip(
                    DashboardPresentation.compactConfidenceLabel(
                        confidence = hotspot?.confidence ?: DataConfidence.UNAVAILABLE,
                        scope = DataScope.HOTSPOT_SESSION,
                    ),
                )
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "查看热点详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (on) {
            Text(
                text = "已开启",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (trafficAvailable) {
                    "累计流量：${ByteFormatter.formatBytes(total)}"
                } else {
                    "累计流量：不可用"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "未开启",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "点击查看热点监控详情",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun RecentAlertsCard(
    alerts: List<AlertEvent>,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = remember(alerts) { DashboardPresentation.recentHomeAlerts(alerts, limit = 2) }
    InfoCard(
        modifier = modifier,
        onClick = onOpenAll,
        contentPadding = dashboardCardPadding(),
        verticalSpacing = 6.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "最近预警",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "查看全部",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "查看全部预警",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (visible.isEmpty()) {
            Text(
                text = "暂无异常",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            visible.forEach { alert ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = DashboardPresentation.alertTitle(alert),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = DashboardPresentation.alertDescription(alert),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = TimeUtils.formatRelative(alert.triggeredAtMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroQuantity(
    parts: FormattedQuantity,
    maxValueSize: TextUnit,
    unitSize: TextUnit,
    valueColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = parts.value,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = maxValueSize,
                color = if (valueColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    valueColor
                },
            ),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 14.sp,
                maxFontSize = maxValueSize,
                stepSize = 1.sp,
            ),
            modifier = Modifier.weight(1f, fill = false),
        )
        if (parts.unit.isNotEmpty()) {
            Text(
                text = parts.unit,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = unitSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

private fun isUnmeteredDisplayPath(path: com.netwatch.domain.model.InternetPath?): Boolean =
    path == InternetPath.WIFI || path == InternetPath.ETHERNET
