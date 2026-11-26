package com.netwatch.domain.usecase

import com.netwatch.core.util.TimeUtils
import com.netwatch.data.repository.NetworkStatsRepository
import com.netwatch.data.repository.SettingsRepository
import com.netwatch.domain.calculator.BillingCycleCalculator
import com.netwatch.domain.calculator.PlanCalculator
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope
import com.netwatch.domain.model.PlanConfig
import com.netwatch.domain.model.PlanUsage
import com.netwatch.domain.model.SimIdentificationStatus
import com.netwatch.domain.model.SimRole
import com.netwatch.domain.model.SimSnapshot
import java.time.ZoneId

class ObservePlanUsageUseCase(
    private val settingsRepository: SettingsRepository,
    private val networkStatsRepository: NetworkStatsRepository,
) {
    suspend fun compute(snapshot: SimSnapshot, zone: ZoneId = ZoneId.systemDefault()): List<PlanUsage> {
        val settings = settingsRepository.current()
        val today = TimeUtils.today()
        return listOf(0, 1).map { slot ->
            val config = settings.simPlans[slot] ?: return@map PlanCalculator.calculate(
                config = PlanConfig(
                    slotIndex = slot,
                    role = if (slot == 1) SimRole.PRIMARY else SimRole.BACKUP,
                    monitoringEnabled = true,
                    generalCapacityBytes = null,
                    directedCapacityBytes = null,
                    resetDayOfMonth = null,
                ),
                usedBytes = null,
                usedConfidence = DataConfidence.UNAVAILABLE,
                usedScope = DataScope.UNKNOWN,
                subscriptionId = null,
                today = today,
                zone = zone,
            )
            val card = snapshot.cards.firstOrNull { it.slotIndex == slot }
            val cycle = BillingCycleCalculator.currentCycle(today, config.resetDayOfMonth, zone)
            val startMs = cycle.start.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = cycle.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
            val identity = card?.identityKey
            val attributed = if (identity != null && card.identificationStatus == SimIdentificationStatus.IDENTIFIED) {
                networkStatsRepository.attributedUsage(identity, startMs, endMs)
            } else {
                null
            }
            val used = attributed?.total
            val resolved = PlanCalculator.resolveCycleUsage(config, used, startMs, endMs)
            if (resolved.calibrationExpired && config.hasCalibrationData()) {
                // Re-check inside the transform so a concurrent in-cycle save is not wiped.
                settingsRepository.updatePlan(slot) { current ->
                    val currentCycle = BillingCycleCalculator.currentCycle(today, current.resetDayOfMonth, zone)
                    val currentStart = currentCycle.start.atStartOfDay(zone).toInstant().toEpochMilli()
                    val currentEnd = currentCycle.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
                    val latest = PlanCalculator.resolveCycleUsage(current, used, currentStart, currentEnd)
                    if (latest.calibrationExpired) current.clearCalibration() else current
                }
            }
            val confidence = if (used != null) DataConfidence.ESTIMATED else DataConfidence.UNAVAILABLE
            val scope = if (used != null) DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM else DataScope.UNKNOWN
            PlanCalculator.calculate(
                config = config,
                usedBytes = used,
                usedConfidence = confidence,
                usedScope = scope,
                subscriptionId = card?.subscriptionId,
                today = today,
                zone = zone,
            )
        }
    }
}
