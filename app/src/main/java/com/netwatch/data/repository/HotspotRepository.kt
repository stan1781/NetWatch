package com.netwatch.data.repository

import com.netwatch.core.database.HotspotSessionDao
import com.netwatch.core.database.HotspotSessionEntity
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.HotspotSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HotspotRepository(private val dao: HotspotSessionDao) {
    fun history(): Flow<List<HotspotSession>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun all(): List<HotspotSession> = dao.getAll().map { it.toDomain() }

    suspend fun open(): HotspotSessionEntity? = dao.findOpen()

    suspend fun insert(session: HotspotSession): Long = dao.insert(session.toEntity())

    suspend fun update(entity: HotspotSessionEntity) = dao.update(entity)

    suspend fun clear() = dao.clear()

    fun HotspotSessionEntity.toDomain() = HotspotSession(
        id = id,
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        rxBytes = rxBytes,
        txBytes = txBytes,
        defaultDataSubscriptionId = defaultDataSubscriptionId,
        defaultDataSlotIndex = defaultDataSlotIndex,
        interfaces = if (interfaces.isBlank()) emptyList() else interfaces.split(","),
        confidence = runCatching { DataConfidence.valueOf(confidence) }.getOrDefault(DataConfidence.ESTIMATED),
        notes = if (notes.isBlank()) emptyList() else notes.split("；"),
    )

    private fun HotspotSession.toEntity() = HotspotSessionEntity(
        id = id,
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        rxBytes = rxBytes,
        txBytes = txBytes,
        defaultDataSubscriptionId = defaultDataSubscriptionId,
        defaultDataSlotIndex = defaultDataSlotIndex,
        interfaces = interfaces.joinToString(","),
        confidence = confidence.name,
        notes = notes.joinToString("；"),
    )
}
