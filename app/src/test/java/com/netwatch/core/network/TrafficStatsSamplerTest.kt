package com.netwatch.core.network

import com.netwatch.domain.calculator.AlertEngine
import com.netwatch.core.datastore.DefaultSettings
import com.netwatch.domain.model.AlertType
import com.netwatch.domain.model.InternetPathState
import com.netwatch.domain.model.MonitorSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level tests for TrafficStats discontinuities.
 *
 * Implausible jumps are anything faster than [TrafficPlausibility.MAX_PLAUSIBLE_BYTES_PER_SEC]
 * (2 GiB/s, about 17.2 Gbps). That is well above real 5G or hotspot bursts
 * (40 MB/s is 50x smaller) and below the 6–18 GiB single-second counter jumps
 * that produced false "短时流量过大" alerts.
 */
class TrafficStatsSamplerTest {
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
    fun oneSecond18GbJumpIsNotAttributedAndDoesNotFireRollingWindow() {
        val source = FakeTrafficStatsSource(
            mobileRx = 1_000_000L,
            mobileTx = 100_000L,
            totalRx = 2_000_000L,
            totalTx = 200_000L,
            elapsed = 10_000L,
        )
        val sampler = TrafficStatsSampler(source)
        val engine = AlertEngine()
        sampler.resetBaseline()

        // A prior real sample so the rolling window has at least two entries.
        source.advance(elapsed = 11_000L, mobileRxDelta = 1_024L, mobileTxDelta = 0L)
        val prior = sampler.sample(nowMs = 11_000L, defaultDataSubscriptionId = 1, defaultDataSlotIndex = 1)
            .copy(path = InternetPathState.Mobile)
        assertFalse(prior.baselineReset)
        assertEquals(1_024L, prior.mobileDelta.total)
        assertTrue(engine.onSample(prior, settings, emptyList(), 11_000L, 1).isEmpty())

        // Proven 2026-11-27 09:38 event: 18.16 GiB in ~1 second.
        val jump = 19_502_433_852L
        source.advance(elapsed = 12_000L, mobileRxDelta = jump, mobileTxDelta = 0L)
        val jumped = sampler.sample(nowMs = 12_000L, defaultDataSubscriptionId = 1, defaultDataSlotIndex = 1)
            .copy(path = InternetPathState.Mobile)
        assertTrue(jumped.baselineReset)
        assertEquals(0L, jumped.mobileDelta.total)
        assertEquals(0L, jumped.totalDelta.total)
        val jumpEvents = engine.onSample(jumped, settings, emptyList(), 12_000L, 1)
        assertTrue(jumpEvents.none { it.type == AlertType.ROLLING_WINDOW })

        // The next small real delta is counted normally and still does not trip 300 MB.
        source.advance(elapsed = 13_000L, mobileRxDelta = 2_000L, mobileTxDelta = 128L)
        val next = sampler.sample(nowMs = 13_000L, defaultDataSubscriptionId = 1, defaultDataSlotIndex = 1)
            .copy(path = InternetPathState.Mobile)
        assertFalse(next.baselineReset)
        assertEquals(2_000L, next.mobileDelta.rxBytes)
        assertEquals(128L, next.mobileDelta.txBytes)
        val nextEvents = engine.onSample(next, settings, emptyList(), 13_000L, 1)
        assertTrue(nextEvents.none { it.type == AlertType.ROLLING_WINDOW })
    }

    @Test
    fun largeJumpAfterDefaultDataSimSwitchIsDiscarded() {
        val source = FakeTrafficStatsSource(
            mobileRx = 8_000_000_000L,
            mobileTx = 900_000_000L,
            totalRx = 8_000_000_000L,
            totalTx = 900_000_000L,
            elapsed = 20_000L,
        )
        val sampler = TrafficStatsSampler(source)
        val engine = AlertEngine()

        // Service path on SIM switch: reset baseline and discard the switch sample.
        sampler.resetBaseline()
        engine.resetBaselines()
        source.advance(elapsed = 21_000L, mobileRxDelta = 40_000L, mobileTxDelta = 10_000L)
        val switchSample = sampler.sample(nowMs = 21_000L, defaultDataSubscriptionId = 1, defaultDataSlotIndex = 1)
            .copy(path = InternetPathState.Mobile, baselineReset = true)
        assertTrue(switchSample.baselineReset)
        assertTrue(engine.onSample(switchSample, settings, emptyList(), 21_000L, 1).isEmpty())

        // Proven 2026-11-26 22:38 event: 8.82 GiB on the sample after the switch.
        val jump = 9_474_149_363L
        source.advance(elapsed = 22_000L, mobileRxDelta = jump, mobileTxDelta = 0L)
        val afterSwitch = sampler.sample(nowMs = 22_000L, defaultDataSubscriptionId = 1, defaultDataSlotIndex = 1)
            .copy(path = InternetPathState.Mobile)
        assertTrue(afterSwitch.baselineReset)
        assertEquals(0L, afterSwitch.mobileDelta.total)
        val events = engine.onSample(afterSwitch, settings, emptyList(), 22_000L, 1)
        assertTrue(events.none { it.type == AlertType.ROLLING_WINDOW })

        source.advance(elapsed = 23_000L, mobileRxDelta = 4_000L, mobileTxDelta = 500L)
        val settled = sampler.sample(nowMs = 23_000L, defaultDataSubscriptionId = 1, defaultDataSlotIndex = 1)
            .copy(path = InternetPathState.Mobile)
        assertFalse(settled.baselineReset)
        assertEquals(4_500L, settled.mobileDelta.total)
    }

    @Test
    fun downwardCounterResetYieldsZeroThenResumesFromNewBaseline() {
        val source = FakeTrafficStatsSource(
            mobileRx = 5_000_000L,
            mobileTx = 2_000_000L,
            totalRx = 5_000_000L,
            totalTx = 2_000_000L,
            elapsed = 30_000L,
        )
        val sampler = TrafficStatsSampler(source)
        sampler.resetBaseline()

        source.advance(elapsed = 31_000L, mobileRxDelta = 100_000L, mobileTxDelta = 20_000L)
        val before = sampler.sample(nowMs = 31_000L, defaultDataSubscriptionId = 1, defaultDataSlotIndex = 1)
        assertEquals(120_000L, before.mobileDelta.total)
        assertFalse(before.baselineReset)

        source.mobileRx = 80_000L
        source.mobileTx = 10_000L
        source.totalRx = 80_000L
        source.totalTx = 10_000L
        source.elapsed = 32_000L
        val reset = sampler.sample(nowMs = 32_000L, defaultDataSubscriptionId = 1, defaultDataSlotIndex = 1)
        assertEquals(0L, reset.mobileDelta.total)
        assertEquals(0L, reset.totalDelta.total)

        source.advance(elapsed = 33_000L, mobileRxDelta = 500L, mobileTxDelta = 40L)
        val after = sampler.sample(nowMs = 33_000L, defaultDataSubscriptionId = 1, defaultDataSlotIndex = 1)
        assertFalse(after.baselineReset)
        assertEquals(500L, after.mobileDelta.rxBytes)
        assertEquals(40L, after.mobileDelta.txBytes)
    }

    @Test
    fun realisticFortyMbBurstIsCountedAndCanFireRollingWindow() {
        val source = FakeTrafficStatsSource(
            mobileRx = 1_000L,
            mobileTx = 0L,
            totalRx = 1_000L,
            totalTx = 0L,
            elapsed = 40_000L,
        )
        val sampler = TrafficStatsSampler(source)
        val engine = AlertEngine()
        val tight = settings.copy(rollingAlertBytes = 20L * 1024 * 1024, rollingWindowSeconds = 60)
        sampler.resetBaseline()

        source.advance(elapsed = 41_000L, mobileRxDelta = 1_000L, mobileTxDelta = 0L)
        val prior = sampler.sample(nowMs = 41_000L, defaultDataSubscriptionId = 1, defaultDataSlotIndex = 1)
            .copy(path = InternetPathState.Mobile)
        engine.onSample(prior, tight, emptyList(), 41_000L, 1)

        val burst = 40L * 1024 * 1024
        source.advance(elapsed = 42_000L, mobileRxDelta = burst, mobileTxDelta = 0L)
        val sample = sampler.sample(nowMs = 42_000L, defaultDataSubscriptionId = 1, defaultDataSlotIndex = 1)
            .copy(path = InternetPathState.Mobile)
        assertFalse(sample.baselineReset)
        assertEquals(burst, sample.mobileDelta.total)
        val events = engine.onSample(sample, tight, emptyList(), 42_000L, 1)
        assertTrue(events.any { it.type == AlertType.ROLLING_WINDOW })
    }

    @Test
    fun realisticHotspotBurstsOverSixtySecondsCanFireRollingWindow() {
        val source = FakeTrafficStatsSource(
            mobileRx = 1_000L,
            mobileTx = 0L,
            totalRx = 1_000L,
            totalTx = 0L,
            elapsed = 50_000L,
        )
        val sampler = TrafficStatsSampler(source)
        val engine = AlertEngine()
        sampler.resetBaseline()

        val bursts = listOf(
            32L * 1024 * 1024,
            45L * 1024 * 1024,
            52L * 1024 * 1024,
            33L * 1024 * 1024,
            54L * 1024 * 1024,
            25L * 1024 * 1024,
            24L * 1024 * 1024,
            50L * 1024 * 1024,
        )
        val all = mutableListOf<com.netwatch.domain.model.AlertEvent>()
        bursts.forEachIndexed { i, rx ->
            source.advance(elapsed = 51_000L + i * 1000L, mobileRxDelta = rx, mobileTxDelta = 0L)
            val sample = sampler.sample(
                nowMs = 51_000L + i * 1000L,
                defaultDataSubscriptionId = 1,
                defaultDataSlotIndex = 1,
            ).copy(path = InternetPathState.Mobile)
            assertFalse(sample.baselineReset)
            assertEquals(rx, sample.mobileDelta.total)
            all += engine.onSample(sample, settings, emptyList(), 51_000L + i * 1000L, 1)
        }
        assertTrue(bursts.sum() > settings.rollingAlertBytes)
        assertTrue(all.any { it.type == AlertType.ROLLING_WINDOW })
    }

    private class FakeTrafficStatsSource(
        var mobileRx: Long,
        var mobileTx: Long,
        var totalRx: Long,
        var totalTx: Long,
        var elapsed: Long,
    ) : TrafficStatsSource {
        override fun mobileRxBytes(): Long = mobileRx
        override fun mobileTxBytes(): Long = mobileTx
        override fun totalRxBytes(): Long = totalRx
        override fun totalTxBytes(): Long = totalTx
        override fun elapsedRealtime(): Long = elapsed

        fun advance(elapsed: Long, mobileRxDelta: Long, mobileTxDelta: Long) {
            this.elapsed = elapsed
            mobileRx += mobileRxDelta
            mobileTx += mobileTxDelta
            totalRx += mobileRxDelta
            totalTx += mobileTxDelta
        }
    }
}
