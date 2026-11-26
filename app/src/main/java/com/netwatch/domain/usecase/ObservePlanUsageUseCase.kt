package com.netwatch.domain.usecase

import com.netwatch.core.util.TimeUtils
import com.netwatch.data.repository.NetworkStatsRepository
import com.netwatch.data.repository.SettingsRepository
import com.netwatch.domain.calculator.PlanCalculator
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope
import com.netwatch.domain.model.PlanUsage
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
                config = com.netwatch.domain.model.PlanConfig(
                    slotIndex = slot,
                    role = if (slot == 1) com.netwatch.domain.model.SimRole.PRIMARY else com.netwatch.domain.model.SimRole.BACKUP,
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
            val cycle = com.netwatch.domain.calculator.BillingCycleCalculator.currentCycle(
                today,
                config.resetDayOfMonth,
                zone,
            )
            val startMs = cycle.start.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = cycle.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
            val identity = card?.identityKey
            val attributed = if (identity != null && card.identificationStatus == com.netwatch.domain.model.SimIdentificationStatus.IDENTIFIED) {
                networkStatsRepository.attributedUsage(identity, startMs, endMs)
            } else {
                null
            }
            val used = attributed?.total
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
