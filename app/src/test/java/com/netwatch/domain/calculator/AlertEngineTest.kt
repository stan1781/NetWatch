package com.netwatch.domain.calculator

import com.netwatch.core.datastore.DefaultSettings
import com.netwatch.domain.model.AlertType
import com.netwatch.domain.model.ByteVolume
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.InternetPathState
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

    @Test
    fun wifiPathWithLargeCountersDoesNotFireSpeedRollingOrPlan() {
        val engine = AlertEngine()
        val tight = settings.copy(
            downloadAlertDurationSec = 1,
            rollingAlertBytes = 1_000,
            rollingWindowSeconds = 5,
        )
        val plan = planUsage(95.0)
        val events = mutableListOf<com.netwatch.domain.model.AlertEvent>()
        repeat(4) { i ->
            events += engine.onSample(
                sample(
                    elapsed = (i + 1) * 1000L,
                    down = 20_000_000,
                    rx = 50L * 1024 * 1024,
                    path = InternetPathState.Wifi,
                    totalDown = 20_000_000,
                    totalRx = 50L * 1024 * 1024,
                ),
                tight,
                listOf(plan),
                (i + 1) * 1000L,
                1,
            )
        }
        assertTrue(events.isEmpty())
    }

    @Test
    fun vpnOverWifiIsTreatedAsWifiAndDoesNotFire() {
        val engine = AlertEngine()
        val tight = settings.copy(downloadAlertDurationSec = 1, rollingAlertBytes = 1_000)
        val events = engine.onSample(
            sample(1000, 20_000_000, 40L * 1024 * 1024, path = InternetPathState.WifiVpn),
            tight,
            listOf(planUsage(90.0)),
            1_000,
            1,
        ) + engine.onSample(
            sample(2000, 20_000_000, 40L * 1024 * 1024, path = InternetPathState.WifiVpn),
            tight,
            listOf(planUsage(90.0)),
            2_000,
            1,
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun tetheringStillFiresHighSpeedAndRolling() {
        val engine = AlertEngine()
        val tight = settings.copy(
            downloadAlertDurationSec = 1,
            rollingAlertBytes = 8L * 1024 * 1024,
            rollingWindowSeconds = 5,
        )
        val first = engine.onSample(
            sample(1000, 10_000_000, 5L * 1024 * 1024, path = InternetPathState.Tethering),
            tight,
            emptyList(),
            1_000,
            1,
        )
        val second = engine.onSample(
            sample(2000, 10_000_000, 5L * 1024 * 1024, path = InternetPathState.Tethering),
            tight,
            emptyList(),
            2_000,
            2,
        )
        val all = first + second
        assertTrue(all.any { it.type == AlertType.HIGH_DOWNLOAD_SPEED })
        assertTrue(all.any { it.type == AlertType.ROLLING_WINDOW })
    }

    @Test
    fun concurrentCellularOnWifiStillFiresHighSpeed() {
        val engine = AlertEngine()
        val dual = InternetPathState.Wifi.copy(
            billableMobile = true,
            hasConcurrentBillableCellular = true,
            transportLabel = "Wi-Fi + 移动数据",
        )
        val s = settings.copy(downloadAlertDurationSec = 1)
        val all = engine.onSample(sample(1000, 10_000_000, 1, path = dual), s, emptyList(), 1_000, 1) +
            engine.onSample(sample(2000, 10_000_000, 1, path = dual), s, emptyList(), 2_000, 2)
        assertTrue(all.any { it.type == AlertType.HIGH_DOWNLOAD_SPEED })
    }

    @Test
    fun mobilePathStillFiresPlanUsage() {
        val engine = AlertEngine()
        val first = engine.onSample(
            sample(1000, 0, 0, path = InternetPathState.Mobile),
            settings,
            listOf(planUsage(85.0)),
            1_000,
            1,
        )
        assertEquals(1, first.count { it.type == AlertType.PLAN_USAGE })
    }

    @Test
    fun wifiBillableDeltaDoesNotContributeToRollingWindow() {
        val engine = AlertEngine()
        val tight = settings.copy(rollingAlertBytes = 8L * 1024 * 1024, rollingWindowSeconds = 5)
        engine.onSample(sample(1000, 1, 5L * 1024 * 1024, path = InternetPathState.Wifi), tight, emptyList(), 1_000, 1)
        engine.onSample(sample(2000, 1, 5L * 1024 * 1024, path = InternetPathState.Wifi), tight, emptyList(), 2_000, 1)
        val later = engine.onSample(sample(3000, 1, 1, path = InternetPathState.Mobile), tight, emptyList(), 3_000, 1)
        assertTrue(later.none { it.type == AlertType.ROLLING_WINDOW })
    }

    private fun sample(
        elapsed: Long,
        down: Long,
        rx: Long,
        path: InternetPathState = InternetPathState.Mobile,
        totalDown: Long = down,
        totalRx: Long = rx,
    ) = SpeedSample(
        timestampMs = elapsed,
        elapsedRealtimeMs = elapsed,
        downloadBytesPerSec = down,
        uploadBytesPerSec = 0,
        totalDownloadBytesPerSec = totalDown,
        totalUploadBytesPerSec = 0,
        mobileDelta = ByteVolume(rx, 0),
        totalDelta = ByteVolume(totalRx, 0),
        defaultDataSubscriptionId = 12,
        defaultDataSlotIndex = 1,
        mobileCountersSupported = true,
        baselineReset = false,
        confidence = DataConfidence.REAL,
        path = path,
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
