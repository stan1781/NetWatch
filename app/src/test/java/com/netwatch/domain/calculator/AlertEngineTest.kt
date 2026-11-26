package com.netwatch.domain.calculator

import com.netwatch.core.datastore.DefaultSettings
import com.netwatch.domain.model.AlertType
import com.netwatch.domain.model.ByteVolume
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope
import com.netwatch.domain.model.MonitorSettings
import com.netwatch.domain.model.PlanConfig
import com.netwatch.domain.model.PlanUsage
import com.netwatch.domain.model.SimRole
import com.netwatch.domain.model.SpeedSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEngineTest {
    private val settings = MonitorSettings(
        realtimeMonitoringEnabled = true,
        downloadSpeedMonitoringEnabled = true,
        uploadSpeedMonitoringEnabled = true,
        sampleIntervalMs = 1000,
        rollingWindowSeconds = 60,
        hotspotMonitoringEnabled = true,
        downloadAlertBytesPerSec = 6L * 1024 * 1024,
        downloadAlertDurationSec = 3,
        rollingAlertBytes = 300L * 1024 * 1024,
        planUsageAlertPercent = 80,
        notificationsEnabled = true,
        notificationSound = true,
        notificationVibrate = true,
        notificationCooldownSec = 60,
        backgroundMonitoringEnabled = true,
        simPlans = mapOf(
            0 to DefaultSettings.SIM1,
            1 to DefaultSettings.SIM2,
        ),
    )

    @Test
    fun highSpeedMustBeSustained() {
        val engine = AlertEngine()
        val first = sample(elapsed = 1000, down = 10_000_000, rx = 10_000_000)
        assertTrue(engine.onSample(first, settings, emptyList(), 1_000L, 1).isEmpty())
        val second = sample(elapsed = 2000, down = 10_000_000, rx = 10_000_000)
        assertTrue(engine.onSample(second, settings, emptyList(), 2_000L, 1).isEmpty())
        val third = sample(elapsed = 3000, down = 10_000_000, rx = 10_000_000)
        val fired = engine.onSample(third, settings, emptyList(), 3_000L, 1)
        assertEquals(1, fired.size)
        assertEquals(AlertType.HIGH_DOWNLOAD_SPEED, fired[0].type)
    }

    @Test
    fun briefSpikeDoesNotFire() {
        val engine = AlertEngine()
        engine.onSample(sample(1000, 10_000_000, 10_000_000), settings, emptyList(), 1_000, 1)
        val afterDrop = engine.onSample(sample(2000, 1000, 1000), settings, emptyList(), 2_000, 1)
        assertTrue(afterDrop.none { it.type == AlertType.HIGH_DOWNLOAD_SPEED })
    }

    @Test
    fun baselineResetDoesNotCreateSpikeAndClearsStreak() {
        val engine = AlertEngine()
        engine.onSample(sample(1000, 10_000_000, 10_000_000), settings, emptyList(), 1_000, 1)
        val reset = sample(2000, 50_000_000, 50_000_000).copy(baselineReset = true)
        assertTrue(engine.onSample(reset, settings, emptyList(), 2_000, 1).isEmpty())
        val next = engine.onSample(sample(3000, 10_000_000, 10_000_000), settings, emptyList(), 3_000, 1)
        assertTrue(next.none { it.type == AlertType.HIGH_DOWNLOAD_SPEED })
    }

    @Test
    fun rollingWindowFiresWhenThresholdExceeded() {
        val engine = AlertEngine()
        val tight = settings.copy(rollingAlertBytes = 20L * 1024 * 1024, rollingWindowSeconds = 5)
        var id = 1L
        val all = mutableListOf<com.netwatch.domain.model.AlertEvent>()
        repeat(5) { i ->
            val events = engine.onSample(
                sample(elapsed = (i + 1) * 1000L, down = 1, rx = 5L * 1024 * 1024),
                tight,
                emptyList(),
                (i + 1) * 1000L,
                id,
            )
            all += events
            if (events.isNotEmpty()) id = events.maxOf { it.id } + 1
        }
        assertTrue(all.any { it.type == AlertType.ROLLING_WINDOW })
    }

    @Test
    fun cooldownSuppressesDuplicateHighSpeed() {
        val engine = AlertEngine()
        val s = settings.copy(notificationCooldownSec = 60, downloadAlertDurationSec = 1)
        val first = engine.onSample(sample(1000, 10_000_000, 1), s, emptyList(), 1_000, 1)
        assertTrue(first.any { it.type == AlertType.HIGH_DOWNLOAD_SPEED })
        val second = engine.onSample(sample(2000, 10_000_000, 1), s, emptyList(), 2_000, 2)
        assertTrue(second.none { it.type == AlertType.HIGH_DOWNLOAD_SPEED })
    }

    @Test
    fun planUsageFiresOnceWhenCrossingThreshold() {
        val engine = AlertEngine()
        val plan = planUsage(85.0)
        val first = engine.onSample(sample(1000, 0, 0), settings, listOf(plan), 1_000, 1)
        assertEquals(1, first.count { it.type == AlertType.PLAN_USAGE })
        val second = engine.onSample(sample(2000, 0, 0), settings, listOf(planUsage(90.0)), 2_000, 2)
        assertTrue(second.none { it.type == AlertType.PLAN_USAGE })
    }

    private fun sample(elapsed: Long, down: Long, rx: Long) = SpeedSample(
        timestampMs = elapsed,
        elapsedRealtimeMs = elapsed,
        downloadBytesPerSec = down,
        uploadBytesPerSec = 0,
        totalDownloadBytesPerSec = down,
        totalUploadBytesPerSec = 0,
        mobileDelta = ByteVolume(rx, 0),
        totalDelta = ByteVolume(rx, 0),
        defaultDataSubscriptionId = 12,
        defaultDataSlotIndex = 1,
        mobileCountersSupported = true,
        baselineReset = false,
        confidence = DataConfidence.REAL,
    )

    private fun planUsage(percent: Double) = PlanUsage(
        slotIndex = 1,
        subscriptionId = 12,
        usedBytes = 100,
        generalCapacityBytes = 1000,
        directedCapacityBytes = null,
        remainingGeneralBytes = 900,
        usagePercent = percent,
        cycleStartMs = 0,
        cycleEndMs = 1,
        usedConfidence = DataConfidence.ESTIMATED,
        usedScope = DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM,
        notes = emptyList(),
    )
}
