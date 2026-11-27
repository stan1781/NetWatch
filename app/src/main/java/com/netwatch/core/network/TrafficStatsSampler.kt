package com.netwatch.core.network

import android.net.TrafficStats
import android.os.SystemClock
import com.netwatch.domain.calculator.TrafficPlausibility
import com.netwatch.domain.model.ByteVolume
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.SpeedSample

/**
 * Public [TrafficStats] counters used by [TrafficStatsSampler].
 * Production uses [SystemTrafficStatsSource]; tests inject a fake.
 */
interface TrafficStatsSource {
    fun mobileRxBytes(): Long
    fun mobileTxBytes(): Long
    fun totalRxBytes(): Long
    fun totalTxBytes(): Long
    fun elapsedRealtime(): Long
}

object SystemTrafficStatsSource : TrafficStatsSource {
    override fun mobileRxBytes(): Long = TrafficStats.getMobileRxBytes()
    override fun mobileTxBytes(): Long = TrafficStats.getMobileTxBytes()
    override fun totalRxBytes(): Long = TrafficStats.getTotalRxBytes()
    override fun totalTxBytes(): Long = TrafficStats.getTotalTxBytes()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}

/**
 * Instantaneous rates from [TrafficStats] public counters.
 * Call [resetBaseline] whenever the default data SIM changes so a counter
 * discontinuity cannot appear as a fake speed spike.
 */
class TrafficStatsSampler(
    private val source: TrafficStatsSource = SystemTrafficStatsSource,
) {
    private var lastMobileRx = UNSUPPORTED
    private var lastMobileTx = UNSUPPORTED
    private var lastTotalRx = UNSUPPORTED
    private var lastTotalTx = UNSUPPORTED
    private var lastElapsed = 0L
    private var primed = false

    fun resetBaseline() {
        lastMobileRx = source.mobileRxBytes()
        lastMobileTx = source.mobileTxBytes()
        lastTotalRx = source.totalRxBytes()
        lastTotalTx = source.totalTxBytes()
        lastElapsed = source.elapsedRealtime()
        primed = true
    }

    fun sample(
        nowMs: Long,
        defaultDataSubscriptionId: Int?,
        defaultDataSlotIndex: Int?,
    ): SpeedSample {
        val elapsed = source.elapsedRealtime()
        val mobileRx = source.mobileRxBytes()
        val mobileTx = source.mobileTxBytes()
        val totalRx = source.totalRxBytes()
        val totalTx = source.totalTxBytes()
        val mobileSupported = mobileRx != UNSUPPORTED && mobileTx != UNSUPPORTED

        if (!primed || lastElapsed <= 0L || elapsed <= lastElapsed) {
            lastMobileRx = mobileRx
            lastMobileTx = mobileTx
            lastTotalRx = totalRx
            lastTotalTx = totalTx
            lastElapsed = elapsed
            primed = true
            return SpeedSample(
                timestampMs = nowMs,
                elapsedRealtimeMs = elapsed,
                downloadBytesPerSec = 0,
                uploadBytesPerSec = 0,
                totalDownloadBytesPerSec = 0,
                totalUploadBytesPerSec = 0,
                mobileDelta = ByteVolume(0, 0),
                totalDelta = ByteVolume(0, 0),
                defaultDataSubscriptionId = defaultDataSubscriptionId,
                defaultDataSlotIndex = defaultDataSlotIndex,
                mobileCountersSupported = mobileSupported,
                baselineReset = true,
                confidence = DataConfidence.REAL,
            )
        }

        val dtMs = elapsed - lastElapsed
        val dtSec = dtMs.coerceAtLeast(1L) / 1000.0
        var mobileDelta = if (mobileSupported && lastMobileRx != UNSUPPORTED) {
            ByteVolume(
                rxBytes = delta(mobileRx, lastMobileRx),
                txBytes = delta(mobileTx, lastMobileTx),
            )
        } else {
            ByteVolume(0, 0)
        }
        var totalDelta = if (totalRx != UNSUPPORTED && lastTotalRx != UNSUPPORTED) {
            ByteVolume(
                rxBytes = delta(totalRx, lastTotalRx),
                txBytes = delta(totalTx, lastTotalTx),
            )
        } else {
            ByteVolume(0, 0)
        }

        var discontinuity = false
        if (TrafficPlausibility.isImplausibleDelta(mobileDelta.total, dtMs)) {
            mobileDelta = ByteVolume(0, 0)
            discontinuity = true
        }
        if (TrafficPlausibility.isImplausibleDelta(totalDelta.total, dtMs)) {
            totalDelta = ByteVolume(0, 0)
        }

        lastMobileRx = mobileRx
        lastMobileTx = mobileTx
        lastTotalRx = totalRx
        lastTotalTx = totalTx
        lastElapsed = elapsed

        return SpeedSample(
            timestampMs = nowMs,
            elapsedRealtimeMs = elapsed,
            downloadBytesPerSec = (mobileDelta.rxBytes / dtSec).toLong(),
            uploadBytesPerSec = (mobileDelta.txBytes / dtSec).toLong(),
            totalDownloadBytesPerSec = (totalDelta.rxBytes / dtSec).toLong(),
            totalUploadBytesPerSec = (totalDelta.txBytes / dtSec).toLong(),
            mobileDelta = mobileDelta,
            totalDelta = totalDelta,
            defaultDataSubscriptionId = defaultDataSubscriptionId,
            defaultDataSlotIndex = defaultDataSlotIndex,
            mobileCountersSupported = mobileSupported,
            baselineReset = discontinuity,
            confidence = if (mobileSupported) DataConfidence.REAL else DataConfidence.ESTIMATED,
        )
    }

    private fun delta(current: Long, previous: Long): Long {
        if (current < 0 || previous < 0) return 0
        val d = current - previous
        return if (d < 0) 0 else d
    }

    companion object {
        private const val UNSUPPORTED = TrafficStats.UNSUPPORTED.toLong()
    }
}
