package com.netwatch.domain.calculator

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class BillingCycle(
    val start: LocalDate,
    val endExclusive: LocalDate,
    val resetDayConfigured: Boolean,
)

object BillingCycleCalculator {
    /**
     * If [resetDayOfMonth] is null, the cycle is the current calendar month and
     * [BillingCycle.resetDayConfigured] is false so the UI can label it as a reference only.
     * Reset days greater than the length of a month clamp to that month's last day.
     */
    fun currentCycle(
        today: LocalDate,
        resetDayOfMonth: Int?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BillingCycle {
        if (resetDayOfMonth == null || resetDayOfMonth !in 1..31) {
            val month = YearMonth.from(today)
            return BillingCycle(
                start = month.atDay(1),
                endExclusive = month.plusMonths(1).atDay(1),
                resetDayConfigured = false,
            )
        }
        val thisMonthReset = clampDay(YearMonth.from(today), resetDayOfMonth)
        val start = if (today >= thisMonthReset) {
            thisMonthReset
        } else {
            clampDay(YearMonth.from(today).minusMonths(1), resetDayOfMonth)
        }
        val endExclusive = clampDay(YearMonth.from(start).plusMonths(1), resetDayOfMonth)
        return BillingCycle(start, endExclusive, resetDayConfigured = true)
    }

    private fun clampDay(month: YearMonth, day: Int): LocalDate =
        month.atDay(day.coerceAtMost(month.lengthOfMonth()))
}
