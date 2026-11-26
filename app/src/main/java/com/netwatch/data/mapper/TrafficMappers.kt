package com.netwatch.data.mapper

import com.netwatch.core.database.DailyUsageEntity
import com.netwatch.domain.model.ByteVolume
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope
import com.netwatch.domain.model.NetworkKind
import com.netwatch.domain.model.TrafficPeriod
import java.time.LocalDate

object TrafficMappers {
    fun DailyUsageEntity.toPeriod(): TrafficPeriod {
        val start = LocalDate.ofEpochDay(dateEpochDay)
        val startMs = start.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMs = start.plusDays(1).atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return TrafficPeriod(
            startMs = startMs,
            endMs = endMs,
            volume = ByteVolume(rxBytes, txBytes),
            kind = runCatching { NetworkKind.valueOf(kind) }.getOrDefault(NetworkKind.TOTAL),
            scope = runCatching { DataScope.valueOf(scope) }.getOrDefault(DataScope.DEVICE_WIDE),
            confidence = runCatching { DataConfidence.valueOf(confidence) }.getOrDefault(DataConfidence.ESTIMATED),
            subscriptionId = subscriptionId,
            slotIndex = slotIndex,
            identityKey = identityKey,
            notes = if (notes.isBlank()) emptyList() else notes.split("；"),
        )
    }
}
