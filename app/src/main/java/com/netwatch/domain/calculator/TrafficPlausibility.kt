package com.netwatch.domain.calculator

/**
 * Shared limits for deciding whether a TrafficStats increment is physically
 * plausible. Used by the sampler (source of truth), the alert engine
 * (defense in depth), and hotspot session accumulation.
 */
object TrafficPlausibility {
    /**
     * Maximum believable TrafficStats increment per second of
     * [android.os.SystemClock.elapsedRealtime].
     *
     * 2 GiB/s is about 17.2 Gbps: higher than typical 5G sub-6 or hotspot
     * bursts (tens to a few hundred MB/s) and most mmWave peaks, but well
     * below the 6.7–18.2 GiB single-second jumps that caused false
     * "短时流量过大" alerts (2026-11-26 15:17, 22:38; 2026-11-27 09:38).
     */
    const val MAX_PLAUSIBLE_BYTES_PER_SEC: Long = 2L * 1024L * 1024L * 1024L

    fun allowedBytes(elapsedMs: Long): Long {
        val ms = elapsedMs.coerceIn(1L, 86_400_000L)
        val wholeSeconds = ms / 1000L
        val remainderMs = ms % 1000L
        return MAX_PLAUSIBLE_BYTES_PER_SEC * wholeSeconds +
            MAX_PLAUSIBLE_BYTES_PER_SEC * remainderMs / 1000L
    }

    fun isImplausibleDelta(deltaBytes: Long, elapsedMs: Long): Boolean {
        if (deltaBytes <= 0L) return false
        return deltaBytes > allowedBytes(elapsedMs)
    }
}
