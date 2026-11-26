package com.netwatch.domain.calculator

import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope
import com.netwatch.domain.model.PlanConfig
import com.netwatch.domain.model.PlanUsage
import java.time.LocalDate
import java.time.ZoneId

data class ResolvedCycleUsage(
    val usedBytes: Long?,
    val attributedAfterCalibrationBytes: Long,
    val generalCalibrated: Boolean,
    val calibrationExpired: Boolean,
)

object PlanCalculator {
    /**
     * Converts a user-entered general remaining amount into the equivalent
     * used bytes at calibration time: used = capacity - remaining.
     * Returns null when either value is missing, so an empty remaining field
     * stays estimate-only and remaining cannot be saved without a capacity.
     */
    fun usedFromRemaining(capacityBytes: Long?, remainingBytes: Long?): Long? {
        if (capacityBytes == null || remainingBytes == null) return null
        if (capacityBytes < 0L || remainingBytes < 0L) return null
        return (capacityBytes - remainingBytes).coerceAtLeast(0L)
    }

    /**
     * Combines a mid-cycle manual calibration with later attributed usage.
     *
     * Unset/expired calibration keeps estimate-only behavior (attributed bytes or null).
     * Active general calibration: used = calibratedUsed + max(0, attributedInCycle - baseline).
     * Directed calibration is ignored; public APIs cannot observe directed traffic,
     * and the UI no longer accepts a directed used/remaining value.
     */
    fun resolveCycleUsage(
        config: PlanConfig,
        attributedInCycleBytes: Long?,
        cycleStartMs: Long,
        cycleEndMs: Long,
    ): ResolvedCycleUsage {
        val hasData = config.hasCalibrationData()
        val at = config.calibrationAtMs
        val inCycle = at != null && at >= cycleStartMs && at < cycleEndMs
        // A used value without a timestamp cannot be tied to a cycle; treat it as expired.
        val expired = hasData && !inCycle
        val generalCalibrated = inCycle && config.calibratedUsedBytes != null
        val after = if (generalCalibrated) {
            val baseline = config.calibrationBaselineAttributedBytes ?: 0L
            val current = attributedInCycleBytes ?: 0L
            (current - baseline).coerceAtLeast(0L)
        } else {
            0L
        }
        val used = when {
            generalCalibrated -> config.calibratedUsedBytes!! + after
            else -> attributedInCycleBytes
        }
        return ResolvedCycleUsage(
            usedBytes = used,
            attributedAfterCalibrationBytes = after,
            generalCalibrated = generalCalibrated,
            calibrationExpired = expired,
        )
    }

    /**
     * Public APIs cannot split directed / zero-rated traffic from general traffic.
     * Remaining general capacity is therefore always an estimate whenever used
     * traffic is known: used may include directed bytes.
     *
     * [usedBytes] is the attributed cycle total (or null). Manual calibration,
     * if present and still in the current cycle, is applied on top.
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
        val startMs = cycle.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = cycle.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val resolved = resolveCycleUsage(config, usedBytes, startMs, endMs)
        val totalUsed = resolved.usedBytes
        val notes = mutableListOf<String>()

        if (!cycle.resetDayConfigured) {
            notes += "未设置套餐重置日，当前按自然月统计，仅供参考。"
        }
        if (config.generalCapacityBytes == null) {
            notes += "未设置通用套餐容量，无法计算剩余流量和占用比例。"
        }
        if (config.directedCapacityBytes != null) {
            notes += "系统公开 API 无法区分定向/不计费流量与通用流量，也无法显示定向剩余。已用流量为全部移动数据" +
                (if (usedScope == DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM) "（按默认数据卡归属的估算值）" else "") +
                "，剩余通用流量仅为估算。"
        }
        if (resolved.generalCalibrated) {
            notes += "通用剩余流量为手动校准值，已用由容量减去剩余得到" +
                (if (resolved.attributedAfterCalibrationBytes > 0L) {
                    "，并已加上校准之后按默认数据卡归属的监控增量"
                } else {
                    ""
                }) +
                "，不是运营商或系统官方统计。"
            notes += "校准之后应用未运行期间的用量不会被自动计入，如需再次对齐请重新校准。"
        } else if (totalUsed == null) {
            notes += "本期已用流量不可用。"
        } else if (usedConfidence == DataConfidence.ESTIMATED) {
            notes += "该卡已用流量按“默认数据卡期间的移动数据增量”归属，不是系统按 SIM 卡出具的官方拆分；应用未运行期间的用量不会被计入。"
        }
        if (usedScope == DataScope.DEVICE_WIDE) {
            notes += "以下为全设备移动数据，不能拆分到某一张 SIM 卡。"
        }
        if (resolved.calibrationExpired) {
            notes += "上一计费周期的手动校准已失效，本周期已用从零开始估算，如需校准请重新填写通用剩余流量。"
        }

        val remaining = if (totalUsed != null && config.generalCapacityBytes != null) {
            (config.generalCapacityBytes - totalUsed).coerceAtLeast(0L)
        } else {
            null
        }
        val percent = if (totalUsed != null && config.generalCapacityBytes != null && config.generalCapacityBytes > 0) {
            (totalUsed.toDouble() / config.generalCapacityBytes.toDouble()) * 100.0
        } else {
            null
        }

        val confidence = when {
            totalUsed == null -> DataConfidence.UNAVAILABLE
            resolved.generalCalibrated -> DataConfidence.ESTIMATED
            else -> usedConfidence
        }

        return PlanUsage(
            slotIndex = config.slotIndex,
            subscriptionId = subscriptionId,
            usedBytes = totalUsed,
            generalCapacityBytes = config.generalCapacityBytes,
            directedCapacityBytes = config.directedCapacityBytes,
            remainingGeneralBytes = remaining,
            usagePercent = percent,
            cycleStartMs = startMs,
            cycleEndMs = endMs,
            usedConfidence = confidence,
            usedScope = usedScope,
            notes = notes,
            userCalibrated = resolved.generalCalibrated,
            calibratedBaseUsedBytes = if (resolved.generalCalibrated) config.calibratedUsedBytes else null,
            attributedAfterCalibrationBytes = if (resolved.generalCalibrated) {
                resolved.attributedAfterCalibrationBytes
            } else {
                null
            },
            calibrationExpired = resolved.calibrationExpired,
        )
    }
}
