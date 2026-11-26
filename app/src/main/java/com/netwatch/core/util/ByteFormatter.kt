package com.netwatch.core.util

import java.util.Locale
import kotlin.math.abs

data class FormattedQuantity(
    val value: String,
    val unit: String,
) {
    fun formatted(): String = if (unit.isBlank()) value else "$value $unit"
}

object ByteFormatter {
    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024
    private const val TB = GB * 1024

    fun formatBytesParts(bytes: Long?, placeholder: String = "—"): FormattedQuantity {
        if (bytes == null) return FormattedQuantity(placeholder, "")
        val magnitude = abs(bytes.toDouble())
        val sign = if (bytes < 0) "-" else ""
        val (scaled, unit) = when {
            magnitude >= TB -> magnitude / TB to "TB"
            magnitude >= GB -> magnitude / GB to "GB"
            magnitude >= MB -> magnitude / MB to "MB"
            magnitude >= KB -> magnitude / KB to "KB"
            else -> magnitude to "B"
        }
        val pattern = if (unit == "B" || unit == "KB") "%.0f" else "%.2f"
        return FormattedQuantity(sign + String.format(Locale.US, pattern, scaled), unit)
    }

    fun formatBytes(bytes: Long?, placeholder: String = "—"): String =
        formatBytesParts(bytes, placeholder).formatted()

    fun formatSpeedParts(bytesPerSec: Long?, placeholder: String = "—"): FormattedQuantity {
        if (bytesPerSec == null) return FormattedQuantity(placeholder, "")
        val parts = formatBytesParts(bytesPerSec, placeholder)
        return if (parts.unit.isEmpty()) parts else parts.copy(unit = parts.unit + "/s")
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
