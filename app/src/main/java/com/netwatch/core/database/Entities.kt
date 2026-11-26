package com.netwatch.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_usage",
    indices = [Index(value = ["dateEpochDay", "kind", "identityKey"], unique = true)],
)
data class DailyUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val kind: String,
    val scope: String,
    val confidence: String,
    val identityKey: String,
    val subscriptionId: Int?,
    val slotIndex: Int?,
    val rxBytes: Long,
    val txBytes: Long,
    val updatedAtMs: Long,
    val notes: String,
)

@Entity(tableName = "attributed_deltas")
data class AttributedDeltaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val dateEpochDay: Long,
    val subscriptionId: Int?,
    val slotIndex: Int?,
    val identityKey: String,
    val rxBytes: Long,
    val txBytes: Long,
    val confidence: String,
)

@Entity(tableName = "alert_events")
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val triggeredAtMs: Long,
    val triggerSpeedBytesPerSec: Long?,
    val triggerVolumeBytes: Long?,
    val planPercent: Double?,
    val slotIndex: Int?,
    val subscriptionId: Int?,
    val message: String,
    val confidence: String,
)

@Entity(tableName = "hotspot_sessions")
data class HotspotSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val rxBytes: Long,
    val txBytes: Long,
    val defaultDataSubscriptionId: Int?,
    val defaultDataSlotIndex: Int?,
    val interfaces: String,
    val confidence: String,
    val notes: String,
)

@Entity(tableName = "sim_identities")
data class SimIdentityEntity(
    @PrimaryKey val identityKey: String,
    val slotIndex: Int,
    val subscriptionId: Int?,
    val carrierName: String?,
    val displayName: String?,
    val firstSeenAtMs: Long,
    val lastSeenAtMs: Long,
    val replaced: Boolean,
)

@Entity(tableName = "sim_change_events")
data class SimChangeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val type: String,
    val slotIndex: Int?,
    val oldSubscriptionId: Int?,
    val newSubscriptionId: Int?,
    val detail: String,
)
