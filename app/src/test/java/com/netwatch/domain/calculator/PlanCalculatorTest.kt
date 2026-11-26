package com.netwatch.domain.calculator

import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope
import com.netwatch.domain.model.PlanConfig
import com.netwatch.domain.model.SimRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PlanCalculatorTest {
    private val config = PlanConfig(
        slotIndex = 1,
        role = SimRole.PRIMARY,
        monitoringEnabled = true,
        generalCapacityBytes = 180L * GB,
        directedCapacityBytes = 30L * GB,
        resetDayOfMonth = 1,
    )

    @Test
    fun remainingAndPercentWhenCapacityAndUsageKnown() {
        val used = 20L * GB
        val usage = PlanCalculator.calculate(
            config, used, DataConfidence.ESTIMATED,
            DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM, 12,
            LocalDate.of(2026, 11, 23), ZoneOffset.UTC,
        )
        assertEquals(160L * GB, usage.remainingGeneralBytes)
        assertEquals(20.0 * 100 / 180.0, usage.usagePercent!!, 0.0001)
        assertTrue(usage.notes.any { it.contains("定向") })
        assertTrue(usage.notes.any { it.contains("估算") || it.contains("归属") })
    }

    @Test
    fun unsetCapacityLeavesRemainingAndPercentNull() {
        val usage = PlanCalculator.calculate(
            config.copy(generalCapacityBytes = null, directedCapacityBytes = null),
            10L * GB, DataConfidence.ESTIMATED,
            DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM, 1,
            LocalDate.of(2026, 11, 23), ZoneOffset.UTC,
        )
        assertNull(usage.remainingGeneralBytes)
        assertNull(usage.usagePercent)
        assertTrue(usage.notes.any { it.contains("未设置通用套餐容量") })
    }

    @Test
    fun unsetResetDayIsLabeledAsReferenceMonth() {
        val usage = PlanCalculator.calculate(
            config.copy(resetDayOfMonth = null),
            1L, DataConfidence.ESTIMATED,
            DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM, 1,
            LocalDate.of(2026, 11, 23), ZoneOffset.UTC,
        )
        assertTrue(usage.notes.any { it.contains("未设置套餐重置日") })
        assertNotNull(usage.cycleStartMs)
    }

    @Test
    fun missingUsageIsUnavailable() {
        val usage = PlanCalculator.calculate(
            config, null, DataConfidence.UNAVAILABLE,
            DataScope.UNKNOWN, null,
            LocalDate.of(2026, 11, 23), ZoneOffset.UTC,
        )
        assertEquals(DataConfidence.UNAVAILABLE, usage.usedConfidence)
        assertNull(usage.remainingGeneralBytes)
    }

    @Test
    fun deviceWideScopeIsCalledOut() {
        val usage = PlanCalculator.calculate(
            config, 5L * GB, DataConfidence.REAL, DataScope.DEVICE_WIDE, null,
            LocalDate.of(2026, 11, 23), ZoneOffset.UTC,
        )
        assertTrue(usage.notes.any { it.contains("全设备") })
    }

    @Test
    fun unsetCalibrationStaysEstimateOnly() {
        val attributed = 5L * GB
        val usage = PlanCalculator.calculate(
            config, attributed, DataConfidence.ESTIMATED,
            DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM, 12,
            LocalDate.of(2026, 11, 26), ZoneOffset.UTC,
        )
        assertEquals(attributed, usage.usedBytes)
        assertEquals(175L * GB, usage.remainingGeneralBytes)
        assertEquals(false, usage.userCalibrated)
        assertNull(usage.calibratedBaseUsedBytes)
        assertNull(usage.attributedAfterCalibrationBytes)
        assertEquals(false, usage.calibrationExpired)
        assertTrue(usage.notes.any { it.contains("默认数据卡") })
        assertTrue(usage.notes.none { it.contains("手动校准") })
    }

    @Test
    fun unsetCalibrationWithNoAttributedStaysUnavailable() {
        val usage = PlanCalculator.calculate(
            config, null, DataConfidence.UNAVAILABLE,
            DataScope.UNKNOWN, 12,
            LocalDate.of(2026, 11, 26), ZoneOffset.UTC,
        )
        assertNull(usage.usedBytes)
        assertEquals(DataConfidence.UNAVAILABLE, usage.usedConfidence)
        assertEquals(false, usage.userCalibrated)
        assertNull(usage.remainingGeneralBytes)
    }

    @Test
    fun calibratedUsedPlusLaterAttributedDeltas() {
        val cycleStart = LocalDate.of(2026, 11, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val calibrationAt = LocalDate.of(2026, 11, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val baseline = 2L * GB
        val attributedNow = 5L * GB
        val manual = 40L * GB
        val calibrated = config.copy(
            calibratedUsedBytes = manual,
            calibrationAtMs = calibrationAt,
            calibrationBaselineAttributedBytes = baseline,
        )
        val usage = PlanCalculator.calculate(
            calibrated, attributedNow, DataConfidence.ESTIMATED,
            DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM, 12,
            LocalDate.of(2026, 11, 26), ZoneOffset.UTC,
        )
        assertEquals(manual + (attributedNow - baseline), usage.usedBytes)
        assertEquals(43L * GB, usage.usedBytes)
        assertEquals(137L * GB, usage.remainingGeneralBytes)
        assertEquals(true, usage.userCalibrated)
        assertEquals(manual, usage.calibratedBaseUsedBytes)
        assertEquals(3L * GB, usage.attributedAfterCalibrationBytes)
        assertEquals(DataConfidence.ESTIMATED, usage.usedConfidence)
        assertTrue(usage.notes.any { it.contains("手动校准") })
        assertTrue(usage.notes.any { it.contains("不是运营商或系统官方") })
        assertTrue(usage.cycleStartMs == cycleStart || usage.cycleStartMs != null)
        val percent = usage.usagePercent!!
        assertEquals(43.0 * 100 / 180.0, percent, 0.0001)
    }

    @Test
    fun calibratedUsedWithoutLaterDeltasUsesManualValue() {
        val calibrationAt = LocalDate.of(2026, 11, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val calibrated = config.copy(
            calibratedUsedBytes = 40L * GB,
            calibrationAtMs = calibrationAt,
            calibrationBaselineAttributedBytes = 2L * GB,
        )
        val usage = PlanCalculator.calculate(
            calibrated, null, DataConfidence.UNAVAILABLE,
            DataScope.UNKNOWN, 12,
            LocalDate.of(2026, 11, 26), ZoneOffset.UTC,
        )
        assertEquals(40L * GB, usage.usedBytes)
        assertEquals(0L, usage.attributedAfterCalibrationBytes)
        assertEquals(true, usage.userCalibrated)
        assertEquals(DataConfidence.ESTIMATED, usage.usedConfidence)
        assertEquals(140L * GB, usage.remainingGeneralBytes)
    }

    @Test
    fun attributedBelowBaselineDoesNotReduceManualUsed() {
        val calibrationAt = LocalDate.of(2026, 11, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val calibrated = config.copy(
            calibratedUsedBytes = 40L * GB,
            calibrationAtMs = calibrationAt,
            calibrationBaselineAttributedBytes = 8L * GB,
        )
        val resolved = PlanCalculator.resolveCycleUsage(
            calibrated,
            attributedInCycleBytes = 5L * GB,
            cycleStartMs = LocalDate.of(2026, 11, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            cycleEndMs = LocalDate.of(2026, 12, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        assertEquals(40L * GB, resolved.usedBytes)
        assertEquals(0L, resolved.attributedAfterCalibrationBytes)
        assertEquals(true, resolved.generalCalibrated)
    }

    @Test
    fun newCycleClearsCalibrationAndUsesEstimateOnly() {
        val previousCycleCalibration = LocalDate.of(2026, 10, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val attributedInNewCycle = 1L * GB
        val calibrated = config.copy(
            calibratedUsedBytes = 40L * GB,
            calibratedDirectedUsedBytes = 10L * GB,
            calibrationAtMs = previousCycleCalibration,
            calibrationBaselineAttributedBytes = 8L * GB,
        )
        val usage = PlanCalculator.calculate(
            calibrated, attributedInNewCycle, DataConfidence.ESTIMATED,
            DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM, 12,
            LocalDate.of(2026, 11, 26), ZoneOffset.UTC,
        )
        assertEquals(attributedInNewCycle, usage.usedBytes)
        assertEquals(false, usage.userCalibrated)
        assertEquals(false, usage.directedUserCalibrated)
        assertEquals(true, usage.calibrationExpired)
        assertNull(usage.usedDirectedBytes)
        assertNull(usage.calibratedBaseUsedBytes)
        assertTrue(usage.notes.any { it.contains("上一计费周期的手动校准已失效") })
        assertTrue(usage.notes.none { it.contains("手动校准值") })
        assertTrue(usage.notes.none { it.contains("定向/不计费已用为手动校准") })
    }

    @Test
    fun newCycleWithNoAttributedDoesNotKeepOldManualUsed() {
        val previousCycleCalibration = LocalDate.of(2026, 10, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val calibrated = config.copy(
            calibratedUsedBytes = 40L * GB,
            calibrationAtMs = previousCycleCalibration,
            calibrationBaselineAttributedBytes = 8L * GB,
        )
        val usage = PlanCalculator.calculate(
            calibrated, null, DataConfidence.UNAVAILABLE,
            DataScope.UNKNOWN, 12,
            LocalDate.of(2026, 11, 1), ZoneOffset.UTC,
        )
        assertNull(usage.usedBytes)
        assertEquals(false, usage.userCalibrated)
        assertEquals(true, usage.calibrationExpired)
        assertEquals(DataConfidence.UNAVAILABLE, usage.usedConfidence)
    }

    @Test
    fun calibrationWithoutTimestampIsExpired() {
        val calibrated = config.copy(
            calibratedUsedBytes = 40L * GB,
            calibrationAtMs = null,
            calibrationBaselineAttributedBytes = 0L,
        )
        val resolved = PlanCalculator.resolveCycleUsage(
            calibrated,
            attributedInCycleBytes = 3L * GB,
            cycleStartMs = LocalDate.of(2026, 11, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            cycleEndMs = LocalDate.of(2026, 12, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        assertEquals(3L * GB, resolved.usedBytes)
        assertEquals(false, resolved.generalCalibrated)
        assertEquals(true, resolved.calibrationExpired)
    }

    @Test
    fun leftoverDirectedCalibrationIsIgnored() {
        val calibrationAt = LocalDate.of(2026, 11, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val leftoverDirectedOnly = config.copy(
            calibratedDirectedUsedBytes = 10L * GB,
            calibrationAtMs = calibrationAt,
        )
        val usage = PlanCalculator.calculate(
            leftoverDirectedOnly, 5L * GB, DataConfidence.ESTIMATED,
            DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM, 12,
            LocalDate.of(2026, 11, 26), ZoneOffset.UTC,
        )
        assertEquals(5L * GB, usage.usedBytes)
        assertEquals(false, usage.userCalibrated)
        assertEquals(false, usage.directedUserCalibrated)
        assertNull(usage.usedDirectedBytes)
        assertNull(usage.remainingDirectedBytes)
        assertTrue(usage.notes.none { it.contains("定向/不计费已用为手动校准") })
        assertTrue(usage.notes.none { it.contains("通用剩余流量为手动校准值") })

        val leftoverWithGeneral = config.copy(
            calibratedUsedBytes = 40L * GB,
            calibratedDirectedUsedBytes = 10L * GB,
            calibrationAtMs = calibrationAt,
            calibrationBaselineAttributedBytes = 0L,
        )
        val combined = PlanCalculator.calculate(
            leftoverWithGeneral, 0L, DataConfidence.ESTIMATED,
            DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM, 12,
            LocalDate.of(2026, 11, 26), ZoneOffset.UTC,
        )
        assertEquals(40L * GB, combined.usedBytes)
        assertEquals(true, combined.userCalibrated)
        assertEquals(false, combined.directedUserCalibrated)
        assertNull(combined.usedDirectedBytes)
        assertNull(combined.remainingDirectedBytes)
        assertTrue(combined.notes.none { it.contains("定向/不计费已用为手动校准") })
    }

    @Test
    fun remainingInputConvertsToUsedAndThenAddsLaterDeltas() {
        assertEquals(40L * GB, PlanCalculator.usedFromRemaining(180L * GB, 140L * GB))
        assertEquals(180L * GB, PlanCalculator.usedFromRemaining(180L * GB, 0L))
        assertEquals(0L, PlanCalculator.usedFromRemaining(180L * GB, 200L * GB))
        assertNull(PlanCalculator.usedFromRemaining(180L * GB, null))
        assertNull(PlanCalculator.usedFromRemaining(null, 140L * GB))
        assertNull(PlanCalculator.usedFromRemaining(null, null))

        val remainingEntered = 140L * GB
        val usedAtCalibration = PlanCalculator.usedFromRemaining(config.generalCapacityBytes, remainingEntered)
        val calibrationAt = LocalDate.of(2026, 11, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val calibrated = config.copy(
            calibratedUsedBytes = usedAtCalibration,
            calibrationAtMs = calibrationAt,
            calibrationBaselineAttributedBytes = 2L * GB,
        )
        val usage = PlanCalculator.calculate(
            calibrated, 5L * GB, DataConfidence.ESTIMATED,
            DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM, 12,
            LocalDate.of(2026, 11, 26), ZoneOffset.UTC,
        )
        assertEquals(43L * GB, usage.usedBytes)
        assertEquals(137L * GB, usage.remainingGeneralBytes)
        assertEquals(true, usage.userCalibrated)
        assertEquals(140L * GB, (usage.generalCapacityBytes!! - usage.calibratedBaseUsedBytes!!))
        assertEquals(3L * GB, usage.attributedAfterCalibrationBytes)
        assertTrue(usage.notes.any { it.contains("通用剩余流量为手动校准值") })
        assertTrue(usage.notes.any { it.contains("容量减去剩余") })
    }

    @Test
    fun emptyRemainingInputDoesNotCalibrate() {
        assertNull(PlanCalculator.usedFromRemaining(config.generalCapacityBytes, null))
        val usage = PlanCalculator.calculate(
            config, 5L * GB, DataConfidence.ESTIMATED,
            DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM, 12,
            LocalDate.of(2026, 11, 26), ZoneOffset.UTC,
        )
        assertEquals(5L * GB, usage.usedBytes)
        assertEquals(false, usage.userCalibrated)
        assertEquals(175L * GB, usage.remainingGeneralBytes)
    }

    @Test
    fun clearCalibrationRemovesAllManualFields() {
        val cleared = config.copy(
            calibratedUsedBytes = 40L * GB,
            calibratedDirectedUsedBytes = 10L * GB,
            calibrationAtMs = 1L,
            calibrationBaselineAttributedBytes = 2L,
        ).clearCalibration()
        assertNull(cleared.calibratedUsedBytes)
        assertNull(cleared.calibratedDirectedUsedBytes)
        assertNull(cleared.calibrationAtMs)
        assertNull(cleared.calibrationBaselineAttributedBytes)
        assertEquals(false, cleared.hasCalibrationData())
    }

    companion object {
        private const val GB = 1024L * 1024L * 1024L
    }
}
