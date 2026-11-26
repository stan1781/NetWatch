package com.netwatch.domain.model

/**
 * A physical SIM slot as observed through public Telephony / Subscription APIs.
 *
 * [slotIndex] is the hardware slot (0 = SIM 1, 1 = SIM 2 on typical devices).
 * [subscriptionId] is the Android subscription id and MUST NOT be treated as a slot index.
 */
data class SimCard(
    val slotIndex: Int,
    val subscriptionId: Int?,
    val displayName: String?,
    val carrierName: String?,
    val isEmbedded: Boolean,
    val isActive: Boolean,
    val isDefaultData: Boolean,
    val identificationStatus: SimIdentificationStatus,
) {
    val label: String get() = "SIM ${slotIndex + 1}"

    val identityKey: String
        get() = if (subscriptionId != null) "sub:$subscriptionId" else "slot:$slotIndex:unknown"
}

data class SimSnapshot(
    val cards: List<SimCard>,
    val defaultDataSubscriptionId: Int?,
    val defaultDataSlotIndex: Int?,
    val mobileConnected: Boolean,
    val permissionGranted: Boolean,
    val capturedAtMs: Long,
)
