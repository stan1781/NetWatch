package com.netwatch.core.datastore

import com.netwatch.domain.model.PlanConfig
import com.netwatch.domain.model.SimRole

/**
 * First-install defaults only. Business logic must read live values from DataStore,
 * never these constants, so that user edits always take effect.
 */
object DefaultSettings {
    const val REALTIME_MONITORING = true
    const val DOWNLOAD_SPEED_MONITORING = true
    const val UPLOAD_SPEED_MONITORING = true
    const val SAMPLE_INTERVAL_MS = 1_000
    const val ROLLING_WINDOW_SECONDS = 60
    const val HOTSPOT_MONITORING = true

    const val DOWNLOAD_ALERT_BYTES_PER_SEC = 6L * 1024L * 1024L
    const val DOWNLOAD_ALERT_DURATION_SEC = 3
    const val ROLLING_ALERT_BYTES = 300L * 1024L * 1024L
    const val PLAN_USAGE_ALERT_PERCENT = 80

    const val NOTIFICATIONS_ENABLED = true
    const val NOTIFICATION_SOUND = true
    const val NOTIFICATION_VIBRATE = true
    const val NOTIFICATION_COOLDOWN_SEC = 60
    const val BACKGROUND_MONITORING = true

    val SIM1 = PlanConfig(
        slotIndex = 0,
        role = SimRole.BACKUP,
        monitoringEnabled = true,
        generalCapacityBytes = null,
        directedCapacityBytes = null,
        resetDayOfMonth = null,
    )

    val SIM2 = PlanConfig(
        slotIndex = 1,
        role = SimRole.PRIMARY,
        monitoringEnabled = true,
        generalCapacityBytes = 180L * 1024L * 1024L * 1024L,
        directedCapacityBytes = 30L * 1024L * 1024L * 1024L,
        resetDayOfMonth = null,
    )
}
