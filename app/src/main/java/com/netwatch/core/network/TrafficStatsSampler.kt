package com.netwatch.core.network

import android.net.TrafficStats
import android.os.SystemClock
import com.netwatch.domain.model.ByteVolume
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.SpeedSample

/**
 * Instantaneous rates from [TrafficStats] public counters.
 * Call [resetBaseline] whenever the default data SIM changes so a counter
 * discontinuity cannot appear as a fake speed spike.
 */
class TrafficStatsSampler {
    private var lastMobileRx = UNSUPPORTED
    private var lastMobileTx = UNSUPPORTED
    private var lastTotalRx = UNSUPPORTED
    private var lastTotalTx = UNSUPPORTED
    private var lastElapsed = 0L
    private var primed = false

    fun resetBaseline() {
        lastMobileRx = TrafficStats.getMobileRxBytes()
        lastMobileTx = TrafficStats.getMobileTxBytes()
        lastTotalRx = TrafficStats.getTotalRxBytes()
        lastTotalTx = TrafficStats.getTotalTxBytes()
        lastElapsed = SystemClock.elapsedRealtime()
        primed = true
    }

    fun sample(
        nowMs: Long,
        defaultDataSubscriptionId: Int?,
        defaultDataSlotIndex: Int?,
    ): SpeedSample {
        val elapsed = SystemClock.elapsedRealtime()
        val mobileRx = TrafficStats.getMobileRxBytes()
        val mobileTx = TrafficStats.getMobileTxBytes()
        val totalRx = TrafficStats.getTotalRxBytes()
        val totalTx = TrafficStats.getTotalTxBytes()
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

        val dtSec = (elapsed - lastElapsed).coerceAtLeast(1L) / 1000.0
        val mobileDelta = if (mobileSupported && lastMobileRx != UNSUPPORTED) {
            ByteVolume(
                rxBytes = delta(mobileRx, lastMobileRx),
                txBytes = delta(mobileTx, lastMobileTx),
            )
        } else {
            ByteVolume(0, 0)
        }
        val totalDelta = if (totalRx != UNSUPPORTED && lastTotalRx != UNSUPPORTED) {
            ByteVolume(
                rxBytes = delta(totalRx, lastTotalRx),
                txBytes = delta(totalTx, lastTotalTx),
            )
        } else {
            ByteVolume(0, 0)
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
            baselineReset = false,
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
