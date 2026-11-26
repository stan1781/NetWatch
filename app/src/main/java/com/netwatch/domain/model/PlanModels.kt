package com.netwatch.domain.model

data class PlanConfig(
    val slotIndex: Int,
    val role: SimRole,
    val monitoringEnabled: Boolean,
    val generalCapacityBytes: Long?,
    val directedCapacityBytes: Long?,
    val resetDayOfMonth: Int?,
)

data class PlanUsage(
    val slotIndex: Int,
    val subscriptionId: Int?,
    val usedBytes: Long?,
    val generalCapacityBytes: Long?,
    val directedCapacityBytes: Long?,
    val remainingGeneralBytes: Long?,
    val usagePercent: Double?,
    val cycleStartMs: Long?,
    val cycleEndMs: Long?,
    val usedConfidence: DataConfidence,
    val usedScope: DataScope,
    val notes: List<String>,
    val missingUsedReason: MissingDataReason? = null,
)
