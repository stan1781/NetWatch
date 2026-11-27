package com.netwatch.domain.calculator

/**
 * After the default-data SIM changes, the switch sample is already discarded
 * as a baseline reset. Counters can still jump on the following sample
 * (2026-11-26 22:38), so this holds back attribution and alerts for
 * [extraSamples] additional readings while the baseline is allowed to settle.
 */
class PostSwitchSettler(private val extraSamples: Int = 1) {
    private var remaining = 0

    fun onDefaultDataSwitch() {
        remaining = extraSamples.coerceAtLeast(0)
    }

    fun consumePending(): Boolean {
        if (remaining <= 0) return false
        remaining--
        return true
    }
}
