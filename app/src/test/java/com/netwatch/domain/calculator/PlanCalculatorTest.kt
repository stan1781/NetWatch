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

    companion object {
        private const val GB = 1024L * 1024L * 1024L
    }
}
