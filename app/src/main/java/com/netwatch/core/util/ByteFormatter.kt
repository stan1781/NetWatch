package com.netwatch.core.util

import java.util.Locale
import kotlin.math.abs

object ByteFormatter {
    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024
    private const val TB = GB * 1024

    fun formatBytes(bytes: Long?, placeholder: String = "—"): String {
        if (bytes == null) return placeholder
        val value = abs(bytes.toDouble())
        val sign = if (bytes < 0) "-" else ""
        val (scaled, unit) = when {
            value >= TB -> value / TB to "TB"
            value >= GB -> value / GB to "GB"
            value >= MB -> value / MB to "MB"
            value >= KB -> value / KB to "KB"
            else -> value to "B"
        }
        val pattern = if (unit == "B" || unit == "KB") "%.0f %s" else "%.2f %s"
        return sign + String.format(Locale.US, pattern, scaled, unit)
    }

    fun formatSpeed(bytesPerSec: Long?, placeholder: String = "—"): String {
        if (bytesPerSec == null) return placeholder
        return formatBytes(bytesPerSec) + "/s"
    }

    fun parseGigabytes(input: String): Long? {
        val trimmed = input.trim().replace(",", "")
        if (trimmed.isEmpty()) return null
        val value = trimmed.toDoubleOrNull() ?: return null
        if (value < 0) return null
        return (value * GB).toLong()
    }

    fun toGigabytesString(bytes: Long?): String {
        if (bytes == null) return ""
        val gb = bytes / GB
        return if (gb == kotlin.math.floor(gb)) {
            String.format(Locale.US, "%.0f", gb)
        } else {
            String.format(Locale.US, "%.2f", gb)
        }
    }

    fun formatPercent(percent: Double?, placeholder: String = "—"): String {
        if (percent == null) return placeholder
        return String.format(Locale.US, "%.1f%%", percent)
    }
}
