package com.netwatch.domain.model

/**
 * User-editable settings. Defaults live in [com.netwatch.core.datastore.DefaultSettings]
 * and are applied only when DataStore is first created.
 */
data class MonitorSettings(
    val realtimeMonitoringEnabled: Boolean,
    val downloadSpeedMonitoringEnabled: Boolean,
    val uploadSpeedMonitoringEnabled: Boolean,
    val sampleIntervalMs: Int,
    val rollingWindowSeconds: Int,
    val hotspotMonitoringEnabled: Boolean,
    val downloadAlertBytesPerSec: Long,
    val downloadAlertDurationSec: Int,
    val rollingAlertBytes: Long,
    val planUsageAlertPercent: Int,
    val notificationsEnabled: Boolean,
    val notificationSound: Boolean,
    val notificationVibrate: Boolean,
    val notificationCooldownSec: Int,
    val backgroundMonitoringEnabled: Boolean,
    val simPlans: Map<Int, PlanConfig>,
)
