package com.netwatch.domain.calculator

import com.netwatch.domain.model.AlertEvent
import com.netwatch.domain.model.AlertType
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.MonitorSettings
import com.netwatch.domain.model.PlanUsage
import com.netwatch.domain.model.SpeedSample

/**
 * Pure alert logic. Thresholds always come from [MonitorSettings], never from hardcoded values.
 */
class AlertEngine {
    private val recent: ArrayDeque<SpeedSample> = ArrayDeque()
    private var highSpeedStreakMs: Long = 0L
    private var lastSampleElapsed: Long? = null
    private val lastFiredAt = mutableMapOf<String, Long>()
    private val crossedPlanKeys = mutableSetOf<String>()

    fun resetBaselines() {
        recent.clear()
        highSpeedStreakMs = 0L
        lastSampleElapsed = null
    }

    /**
     * Returns zero or more alerts for this sample. Cooldown is per alert type
     * (and per SIM for plan alerts). Baseline-reset samples never contribute a spike.
     */
    fun onSample(
        sample: SpeedSample,
        settings: MonitorSettings,
        planUsages: List<PlanUsage>,
        nowMs: Long,
        nextId: Long,
    ): List<AlertEvent> {
        if (sample.baselineReset) {
            resetBaselines()
            lastSampleElapsed = sample.elapsedRealtimeMs
            return emptyList()
        }

        val windowMs = settings.rollingWindowSeconds.coerceAtLeast(1) * 1000L
        recent.addLast(sample)
        while (recent.isNotEmpty() && sample.elapsedRealtimeMs - recent.first().elapsedRealtimeMs > windowMs) {
            recent.removeFirst()
        }

        val events = mutableListOf<AlertEvent>()
        var id = nextId

        val dt = lastSampleElapsed?.let { sample.elapsedRealtimeMs - it }?.coerceAtLeast(0L) ?: settings.sampleIntervalMs.toLong()
        lastSampleElapsed = sample.elapsedRealtimeMs

        // Only billable mobile traffic can drive plan/speed/rolling alerts.
        // Wi-Fi, Ethernet, and VPN-over-Wi-Fi samples must not contribute,
        // even if TrafficStats mobile counters still increase.
        if (settings.downloadSpeedMonitoringEnabled &&
            sample.path.billableMobile &&
            sample.billableDownloadBytesPerSec >= settings.downloadAlertBytesPerSec
        ) {
            highSpeedStreakMs += dt
        } else {
            highSpeedStreakMs = 0L
        }

        val durationMs = settings.downloadAlertDurationSec.coerceAtLeast(1) * 1000L
        if (settings.downloadSpeedMonitoringEnabled && highSpeedStreakMs >= durationMs) {
            if (canFire(key(AlertType.HIGH_DOWNLOAD_SPEED), nowMs, settings.notificationCooldownSec)) {
                events += AlertEvent(
                    id = id++,
                    type = AlertType.HIGH_DOWNLOAD_SPEED,
                    triggeredAtMs = nowMs,
                    triggerSpeedBytesPerSec = sample.billableDownloadBytesPerSec,
                    triggerVolumeBytes = null,
                    planPercent = null,
                    slotIndex = sample.defaultDataSlotIndex,
                    subscriptionId = sample.defaultDataSubscriptionId,
                    message = "下载速度持续偏高",
                    confidence = sample.confidence,
                )
                markFired(key(AlertType.HIGH_DOWNLOAD_SPEED), nowMs)
                highSpeedStreakMs = 0L
            }
        }

        val rollingBytes = recent.sumOf { it.billableMobileDelta.total }
        if (sample.path.billableMobile &&
            rollingBytes >= settings.rollingAlertBytes &&
            recent.size >= 2
        ) {
            if (canFire(key(AlertType.ROLLING_WINDOW), nowMs, settings.notificationCooldownSec)) {
                events += AlertEvent(
                    id = id++,
                    type = AlertType.ROLLING_WINDOW,
                    triggeredAtMs = nowMs,
                    triggerSpeedBytesPerSec = null,
                    triggerVolumeBytes = rollingBytes,
                    planPercent = null,
                    slotIndex = sample.defaultDataSlotIndex,
                    subscriptionId = sample.defaultDataSubscriptionId,
                    message = "滚动时间窗内流量过大",
                    confidence = sample.confidence,
                )
                markFired(key(AlertType.ROLLING_WINDOW), nowMs)
            }
        }

        if (!sample.path.billableMobile) {
            return events
        }

        for (plan in planUsages) {
            if (plan.usagePercent == null) continue
            val threshold = settings.planUsageAlertPercent.toDouble()
            val planKey = key(AlertType.PLAN_USAGE) + ":slot=${plan.slotIndex}"
            if (plan.usagePercent >= threshold) {
                if (planKey !in crossedPlanKeys &&
                    canFire(planKey, nowMs, settings.notificationCooldownSec)
                ) {
                    events += AlertEvent(
                        id = id++,
                        type = AlertType.PLAN_USAGE,
                        triggeredAtMs = nowMs,
                        triggerSpeedBytesPerSec = null,
                        triggerVolumeBytes = plan.usedBytes,
                        planPercent = plan.usagePercent,
                        slotIndex = plan.slotIndex,
                        subscriptionId = plan.subscriptionId,
                        message = "SIM ${plan.slotIndex + 1} 套餐使用率过高",
                        confidence = plan.usedConfidence,
                    )
                    markFired(planKey, nowMs)
                    crossedPlanKeys += planKey
                }
            } else if (plan.usagePercent < threshold - 1.0) {
                // Allow a new alert only after usage drops back below the threshold.
                crossedPlanKeys.remove(planKey)
            }
        }

        return events
    }

    private fun key(type: AlertType) = type.name

    private fun canFire(key: String, nowMs: Long, cooldownSec: Int): Boolean {
        val last = lastFiredAt[key] ?: return true
        return nowMs - last >= cooldownSec.coerceAtLeast(0) * 1000L
    }

    private fun markFired(key: String, nowMs: Long) {
        lastFiredAt[key] = nowMs
    }
}
