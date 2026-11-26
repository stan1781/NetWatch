package com.netwatch.domain.model

data class ByteVolume(
    val rxBytes: Long,
    val txBytes: Long,
) {
    val total: Long get() = rxBytes + txBytes

    operator fun plus(other: ByteVolume) =
        ByteVolume(rxBytes + other.rxBytes, txBytes + other.txBytes)

    operator fun minus(other: ByteVolume) =
        ByteVolume(
            (rxBytes - other.rxBytes).coerceAtLeast(0L),
            (txBytes - other.txBytes).coerceAtLeast(0L),
        )
}

data class SpeedSample(
    val timestampMs: Long,
    val elapsedRealtimeMs: Long,
    val downloadBytesPerSec: Long,
    val uploadBytesPerSec: Long,
    val totalDownloadBytesPerSec: Long,
    val totalUploadBytesPerSec: Long,
    val mobileDelta: ByteVolume,
    val totalDelta: ByteVolume,
    val defaultDataSubscriptionId: Int?,
    val defaultDataSlotIndex: Int?,
    val mobileCountersSupported: Boolean,
    val baselineReset: Boolean,
    val confidence: DataConfidence,
)

data class TrafficPeriod(
    val startMs: Long,
    val endMs: Long,
    val volume: ByteVolume,
    val kind: NetworkKind,
    val scope: DataScope,
    val confidence: DataConfidence,
    val subscriptionId: Int? = null,
    val slotIndex: Int? = null,
    val identityKey: String? = null,
    val notes: List<String> = emptyList(),
)

data class AppUsageItem(
    val uid: Int,
    val packageName: String?,
    val label: String,
    val rxBytes: Long,
    val txBytes: Long,
    val kind: NetworkKind,
    val confidence: DataConfidence,
    val scope: DataScope = DataScope.PER_APP,
) {
    val total: Long get() = rxBytes + txBytes
}

data class HotspotStatus(
    val isTethering: Boolean,
    val tetheredInterfaces: List<String>,
    val session: HotspotSession?,
    val confidence: DataConfidence,
    val sourceLabel: String,
    val notes: List<String>,
)

data class HotspotSession(
    val id: Long,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val rxBytes: Long,
    val txBytes: Long,
    val defaultDataSubscriptionId: Int?,
    val defaultDataSlotIndex: Int?,
    val interfaces: List<String>,
    val confidence: DataConfidence,
    val notes: List<String>,
)

data class AlertEvent(
    val id: Long,
    val type: AlertType,
    val triggeredAtMs: Long,
    val triggerSpeedBytesPerSec: Long?,
    val triggerVolumeBytes: Long?,
    val planPercent: Double?,
    val slotIndex: Int?,
    val subscriptionId: Int?,
    val message: String,
    val confidence: DataConfidence,
)
