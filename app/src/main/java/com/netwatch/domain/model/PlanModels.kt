package com.netwatch.domain.model

data class PlanConfig(
    val slotIndex: Int,
    val role: SimRole,
    val monitoringEnabled: Boolean,
    val generalCapacityBytes: Long?,
    val directedCapacityBytes: Long?,
    val resetDayOfMonth: Int?,
    /** Stored general used at [calibrationAtMs], derived as capacity - user-entered remaining. Null means estimate-only. */
    val calibratedUsedBytes: Long? = null,
    /** Unused leftover. Directed calibration is no longer offered or applied. */
    val calibratedDirectedUsedBytes: Long? = null,
    /** Wall-clock time of the last saved calibration; also used to expire it on a new cycle. */
    val calibrationAtMs: Long? = null,
    /** Attributed cycle total observed when the calibration was saved, so later deltas can be added. */
    val calibrationBaselineAttributedBytes: Long? = null,
) {
    fun clearCalibration(): PlanConfig = copy(
        calibratedUsedBytes = null,
        calibratedDirectedUsedBytes = null,
        calibrationAtMs = null,
        calibrationBaselineAttributedBytes = null,
    )

    fun hasCalibrationData(): Boolean =
        calibratedUsedBytes != null ||
            calibrationAtMs != null ||
            calibrationBaselineAttributedBytes != null
}

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
    val usedDirectedBytes: Long? = null,
    val remainingDirectedBytes: Long? = null,
    /** True when general used includes a user-entered mid-cycle calibration. */
    val userCalibrated: Boolean = false,
    val directedUserCalibrated: Boolean = false,
    val calibratedBaseUsedBytes: Long? = null,
    val attributedAfterCalibrationBytes: Long? = null,
    val calibrationExpired: Boolean = false,
)
