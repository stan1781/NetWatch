package com.netwatch.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ByteFormatterTest {
    @Test
    fun formatsCommonUnits() {
        assertEquals("512 B", ByteFormatter.formatBytes(512))
        assertEquals("1.00 MB", ByteFormatter.formatBytes(1024L * 1024))
        assertEquals("180.00 GB", ByteFormatter.formatBytes(180L * 1024 * 1024 * 1024))
        assertEquals("—", ByteFormatter.formatBytes(null))
        assertEquals("6.00 MB/s", ByteFormatter.formatSpeed(6L * 1024 * 1024))
    }

    @Test
    fun parseGigabytes() {
        assertEquals(180L * 1024 * 1024 * 1024, ByteFormatter.parseGigabytes("180"))
        assertNull(ByteFormatter.parseGigabytes(""))
        assertNull(ByteFormatter.parseGigabytes("abc"))
        assertEquals("180", ByteFormatter.toGigabytesString(180L * 1024 * 1024 * 1024))
    }
}
