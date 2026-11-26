package com.netwatch.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)
    private val dateTimeFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val timeFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss", Locale.CHINA)

    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDayExclusive(date: LocalDate): Long =
        startOfDay(date.plusDays(1))

    fun today(): LocalDate = LocalDate.now(zone)

    fun dateOf(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    fun formatDate(date: LocalDate): String = date.format(dateFmt)

    fun formatDate(epochMs: Long): String = formatDate(dateOf(epochMs))

    fun formatDateTime(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(dateTimeFmt)

    fun formatTime(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(timeFmt)

    fun monthRange(yearMonth: YearMonth = YearMonth.now(zone)): Pair<Long, Long> {
        val start = startOfDay(yearMonth.atDay(1))
        val end = startOfDay(yearMonth.plusMonths(1).atDay(1))
        return start to end
    }
}
