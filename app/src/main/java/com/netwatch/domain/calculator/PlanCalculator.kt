package com.netwatch.domain.calculator

import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope
import com.netwatch.domain.model.PlanConfig
import com.netwatch.domain.model.PlanUsage
import java.time.LocalDate
import java.time.ZoneId

object PlanCalculator {
    /**
     * Public APIs cannot split directed / zero-rated traffic from general traffic.
     * Remaining general capacity is therefore always an estimate whenever used
     * traffic is known: used may include directed bytes.
     */
    fun calculate(
        config: PlanConfig,
        usedBytes: Long?,
        usedConfidence: DataConfidence,
        usedScope: DataScope,
        subscriptionId: Int?,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): PlanUsage {
        val cycle = BillingCycleCalculator.currentCycle(today, config.resetDayOfMonth, zone)
        val notes = mutableListOf<String>()

        if (!cycle.resetDayConfigured) {
            notes += "未设置套餐重置日，当前按自然月统计，仅供参考。"
        }
        if (config.generalCapacityBytes == null) {
            notes += "未设置通用套餐容量，无法计算剩余流量和占用比例。"
        }
        if (config.directedCapacityBytes != null) {
            notes += "系统公开 API 无法区分定向/不计费流量与通用流量。已用流量为全部移动数据" +
                (if (usedScope == DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM) "（按默认数据卡归属的估算值）" else "") +
                "，剩余通用流量仅为估算。"
        }
        if (usedBytes == null) {
            notes += "本期已用流量不可用。"
        } else if (usedConfidence == DataConfidence.ESTIMATED) {
            notes += "该卡已用流量按“默认数据卡期间的移动数据增量”归属，不是系统按 SIM 卡出具的官方拆分；应用未运行期间的用量不会被计入。"
        }
        if (usedScope == DataScope.DEVICE_WIDE) {
            notes += "以下为全设备移动数据，不能拆分到某一张 SIM 卡。"
        }

        val remaining = if (usedBytes != null && config.generalCapacityBytes != null) {
            (config.generalCapacityBytes - usedBytes).coerceAtLeast(0L)
        } else {
            null
        }
        val percent = if (usedBytes != null && config.generalCapacityBytes != null && config.generalCapacityBytes > 0) {
            (usedBytes.toDouble() / config.generalCapacityBytes.toDouble()) * 100.0
        } else {
            null
        }

        val startMs = cycle.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = cycle.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

        return PlanUsage(
            slotIndex = config.slotIndex,
            subscriptionId = subscriptionId,
            usedBytes = usedBytes,
            generalCapacityBytes = config.generalCapacityBytes,
            directedCapacityBytes = config.directedCapacityBytes,
            remainingGeneralBytes = remaining,
            usagePercent = percent,
            cycleStartMs = startMs,
            cycleEndMs = endMs,
            usedConfidence = if (usedBytes == null) DataConfidence.UNAVAILABLE else usedConfidence,
            usedScope = usedScope,
            notes = notes,
        )
    }
}
