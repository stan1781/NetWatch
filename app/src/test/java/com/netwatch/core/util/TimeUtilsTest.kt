package com.netwatch.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimeUtilsTest {
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute, second).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun formatRelativeSameDay() {
        val now = at(2026, 11, 26, 17, 30, 0)
        assertEquals("刚刚", TimeUtils.formatRelative(at(2026, 11, 26, 17, 29, 30), now))
        assertEquals("17:07", TimeUtils.formatRelative(at(2026, 11, 26, 17, 7, 0), now))
        assertEquals("昨天 23:50", TimeUtils.formatRelative(at(2026, 11, 25, 23, 50, 0), now))
        val older = TimeUtils.formatRelative(at(2026, 1, 2, 8, 0, 0), now)
        assertTrue(older.contains("01-02"))
    }
}
