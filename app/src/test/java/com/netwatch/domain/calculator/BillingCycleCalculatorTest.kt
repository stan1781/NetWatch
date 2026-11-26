package com.netwatch.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BillingCycleCalculatorTest {
    @Test
    fun unsetResetDayUsesCalendarMonthAndMarksUnconfigured() {
        val cycle = BillingCycleCalculator.currentCycle(LocalDate.of(2026, 11, 23), null)
        assertEquals(LocalDate.of(2026, 11, 1), cycle.start)
        assertEquals(LocalDate.of(2026, 12, 1), cycle.endExclusive)
        assertFalse(cycle.resetDayConfigured)
    }

    @Test
    fun resetDayAlreadyPassedUsesThisMonth() {
        val cycle = BillingCycleCalculator.currentCycle(LocalDate.of(2026, 11, 23), 1)
        assertEquals(LocalDate.of(2026, 11, 1), cycle.start)
        assertEquals(LocalDate.of(2026, 12, 1), cycle.endExclusive)
        assertTrue(cycle.resetDayConfigured)
    }

    @Test
    fun resetDayStillAheadUsesPreviousMonth() {
        val cycle = BillingCycleCalculator.currentCycle(LocalDate.of(2026, 11, 5), 20)
        assertEquals(LocalDate.of(2026, 10, 20), cycle.start)
        assertEquals(LocalDate.of(2026, 11, 20), cycle.endExclusive)
    }

    @Test
    fun resetDayClampsToEndOfShortMonth() {
        val cycle = BillingCycleCalculator.currentCycle(LocalDate.of(2026, 3, 1), 31)
        assertEquals(LocalDate.of(2026, 2, 28), cycle.start)
        assertEquals(LocalDate.of(2026, 3, 31), cycle.endExclusive)
    }
}
