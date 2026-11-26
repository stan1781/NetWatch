package com.netwatch.data.repository

import com.netwatch.core.database.AlertEventDao
import com.netwatch.core.database.AlertEventEntity
import com.netwatch.domain.model.AlertEvent
import com.netwatch.domain.model.AlertType
import com.netwatch.domain.model.DataConfidence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlertRepository(private val dao: AlertEventDao) {
    fun recent(limit: Int = 50): Flow<List<AlertEvent>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun all(): List<AlertEvent> = dao.getAll().map { it.toDomain() }

    suspend fun insert(event: AlertEvent): Long = dao.insert(event.toEntity())

    suspend fun clear() = dao.clear()

    private fun AlertEventEntity.toDomain() = AlertEvent(
        id = id,
        type = runCatching { AlertType.valueOf(type) }.getOrDefault(AlertType.ROLLING_WINDOW),
        triggeredAtMs = triggeredAtMs,
        triggerSpeedBytesPerSec = triggerSpeedBytesPerSec,
        triggerVolumeBytes = triggerVolumeBytes,
        planPercent = planPercent,
        slotIndex = slotIndex,
        subscriptionId = subscriptionId,
        message = message,
        confidence = runCatching { DataConfidence.valueOf(confidence) }.getOrDefault(DataConfidence.ESTIMATED),
    )

    private fun AlertEvent.toEntity() = AlertEventEntity(
        type = type.name,
        triggeredAtMs = triggeredAtMs,
        triggerSpeedBytesPerSec = triggerSpeedBytesPerSec,
        triggerVolumeBytes = triggerVolumeBytes,
        planPercent = planPercent,
        slotIndex = slotIndex,
        subscriptionId = subscriptionId,
        message = message,
        confidence = confidence.name,
    )
}
