package com.netwatch.core.datastore

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.netwatch.domain.model.MonitorSettings
import com.netwatch.domain.model.PlanConfig
import com.netwatch.domain.model.SimRole

/**
 * Pure DataStore mapping. Plan keys always follow the map key, never a possibly
 * stale [PlanConfig.slotIndex], so two SIMs cannot overwrite each other.
 */
object SettingsPreferences {
    const val UNSET = -1L

    fun toSettings(prefs: Preferences): MonitorSettings = MonitorSettings(
        realtimeMonitoringEnabled = prefs[Keys.REALTIME] ?: DefaultSettings.REALTIME_MONITORING,
        downloadSpeedMonitoringEnabled = prefs[Keys.DL_MON] ?: DefaultSettings.DOWNLOAD_SPEED_MONITORING,
        uploadSpeedMonitoringEnabled = prefs[Keys.UL_MON] ?: DefaultSettings.UPLOAD_SPEED_MONITORING,
        sampleIntervalMs = prefs[Keys.SAMPLE_MS] ?: DefaultSettings.SAMPLE_INTERVAL_MS,
        rollingWindowSeconds = prefs[Keys.ROLLING_SEC] ?: DefaultSettings.ROLLING_WINDOW_SECONDS,
        hotspotMonitoringEnabled = prefs[Keys.HOTSPOT] ?: DefaultSettings.HOTSPOT_MONITORING,
        downloadAlertBytesPerSec = prefs[Keys.DL_ALERT_BPS] ?: DefaultSettings.DOWNLOAD_ALERT_BYTES_PER_SEC,
        downloadAlertDurationSec = prefs[Keys.DL_ALERT_DUR] ?: DefaultSettings.DOWNLOAD_ALERT_DURATION_SEC,
        rollingAlertBytes = prefs[Keys.ROLLING_BYTES] ?: DefaultSettings.ROLLING_ALERT_BYTES,
        planUsageAlertPercent = prefs[Keys.PLAN_ALERT_PCT] ?: DefaultSettings.PLAN_USAGE_ALERT_PERCENT,
        notificationsEnabled = prefs[Keys.NOTIF] ?: DefaultSettings.NOTIFICATIONS_ENABLED,
        notificationSound = prefs[Keys.NOTIF_SOUND] ?: DefaultSettings.NOTIFICATION_SOUND,
        notificationVibrate = prefs[Keys.NOTIF_VIBRATE] ?: DefaultSettings.NOTIFICATION_VIBRATE,
        notificationCooldownSec = prefs[Keys.NOTIF_COOLDOWN] ?: DefaultSettings.NOTIFICATION_COOLDOWN_SEC,
        backgroundMonitoringEnabled = prefs[Keys.BACKGROUND] ?: DefaultSettings.BACKGROUND_MONITORING,
        simPlans = readPlans(prefs),
    )

    fun write(prefs: MutablePreferences, value: MonitorSettings) {
        prefs[Keys.REALTIME] = value.realtimeMonitoringEnabled
        prefs[Keys.DL_MON] = value.downloadSpeedMonitoringEnabled
        prefs[Keys.UL_MON] = value.uploadSpeedMonitoringEnabled
        prefs[Keys.SAMPLE_MS] = value.sampleIntervalMs
        prefs[Keys.ROLLING_SEC] = value.rollingWindowSeconds
        prefs[Keys.HOTSPOT] = value.hotspotMonitoringEnabled
        prefs[Keys.DL_ALERT_BPS] = value.downloadAlertBytesPerSec
        prefs[Keys.DL_ALERT_DUR] = value.downloadAlertDurationSec
        prefs[Keys.ROLLING_BYTES] = value.rollingAlertBytes
        prefs[Keys.PLAN_ALERT_PCT] = value.planUsageAlertPercent
        prefs[Keys.NOTIF] = value.notificationsEnabled
        prefs[Keys.NOTIF_SOUND] = value.notificationSound
        prefs[Keys.NOTIF_VIBRATE] = value.notificationVibrate
        prefs[Keys.NOTIF_COOLDOWN] = value.notificationCooldownSec
        prefs[Keys.BACKGROUND] = value.backgroundMonitoringEnabled
        writePlans(prefs, value.simPlans)
    }

    fun readPlans(prefs: Preferences): Map<Int, PlanConfig> = mapOf(
        0 to readPlan(prefs, 0, DefaultSettings.SIM1),
        1 to readPlan(prefs, 1, DefaultSettings.SIM2),
    )

    fun readPlan(prefs: Preferences, slot: Int, fallback: PlanConfig): PlanConfig {
        val prefix = prefix(slot)
        return PlanConfig(
            slotIndex = slot,
            role = when (prefs[stringPreferencesKey(prefix + "role")]) {
                SimRole.PRIMARY.name -> SimRole.PRIMARY
                SimRole.BACKUP.name -> SimRole.BACKUP
                else -> fallback.role
            },
            monitoringEnabled = prefs[booleanPreferencesKey(prefix + "monitoring")] ?: fallback.monitoringEnabled,
            generalCapacityBytes = unsetToNull(prefs[longPreferencesKey(prefix + "general")])
                ?: fallback.generalCapacityBytes.takeIf { prefs[longPreferencesKey(prefix + "general")] == null },
            directedCapacityBytes = unsetToNull(prefs[longPreferencesKey(prefix + "directed")])
                ?: fallback.directedCapacityBytes.takeIf { prefs[longPreferencesKey(prefix + "directed")] == null },
            resetDayOfMonth = unsetToNull(prefs[longPreferencesKey(prefix + "reset")])
                ?.toInt()
                ?: fallback.resetDayOfMonth.takeIf { prefs[longPreferencesKey(prefix + "reset")] == null },
            calibratedUsedBytes = unsetToNull(prefs[longPreferencesKey(prefix + "calibrated_used")]),
            calibratedDirectedUsedBytes = unsetToNull(prefs[longPreferencesKey(prefix + "calibrated_directed_used")]),
            calibrationAtMs = unsetToNull(prefs[longPreferencesKey(prefix + "calibration_at")]),
            calibrationBaselineAttributedBytes = unsetToNull(prefs[longPreferencesKey(prefix + "calibration_baseline")]),
        )
    }

    /**
     * Writes each plan under the map key. [PlanConfig.slotIndex] is forced to
     * that key so a mismatched slotIndex cannot clobber the other SIM.
     */
    fun writePlans(prefs: MutablePreferences, simPlans: Map<Int, PlanConfig>) {
        simPlans.forEach { (slot, plan) ->
            val aligned = plan.copy(slotIndex = slot)
            val prefix = prefix(slot)
            prefs[stringPreferencesKey(prefix + "role")] = aligned.role.name
            prefs[booleanPreferencesKey(prefix + "monitoring")] = aligned.monitoringEnabled
            prefs[longPreferencesKey(prefix + "general")] = aligned.generalCapacityBytes ?: UNSET
            prefs[longPreferencesKey(prefix + "directed")] = aligned.directedCapacityBytes ?: UNSET
            prefs[longPreferencesKey(prefix + "reset")] = aligned.resetDayOfMonth?.toLong() ?: UNSET
            prefs[longPreferencesKey(prefix + "calibrated_used")] = aligned.calibratedUsedBytes ?: UNSET
            prefs[longPreferencesKey(prefix + "calibrated_directed_used")] = aligned.calibratedDirectedUsedBytes ?: UNSET
            prefs[longPreferencesKey(prefix + "calibration_at")] = aligned.calibrationAtMs ?: UNSET
            prefs[longPreferencesKey(prefix + "calibration_baseline")] = aligned.calibrationBaselineAttributedBytes ?: UNSET
        }
    }

    fun applyPlanUpdate(
        settings: MonitorSettings,
        slotIndex: Int,
        transform: (PlanConfig) -> PlanConfig,
    ): MonitorSettings {
        val current = settings.simPlans[slotIndex] ?: return settings
        val updated = transform(current).copy(slotIndex = slotIndex)
        return settings.copy(simPlans = settings.simPlans + (slotIndex to updated))
    }

    fun prefix(slot: Int): String = "sim${slot}_"

    fun unsetToNull(value: Long?): Long? =
        if (value == null || value == UNSET) null else value

    object Keys {
        val REALTIME = booleanPreferencesKey("realtime")
        val DL_MON = booleanPreferencesKey("dl_mon")
        val UL_MON = booleanPreferencesKey("ul_mon")
        val SAMPLE_MS = intPreferencesKey("sample_ms")
        val ROLLING_SEC = intPreferencesKey("rolling_sec")
        val HOTSPOT = booleanPreferencesKey("hotspot")
        val DL_ALERT_BPS = longPreferencesKey("dl_alert_bps")
        val DL_ALERT_DUR = intPreferencesKey("dl_alert_dur")
        val ROLLING_BYTES = longPreferencesKey("rolling_bytes")
        val PLAN_ALERT_PCT = intPreferencesKey("plan_alert_pct")
        val NOTIF = booleanPreferencesKey("notif")
        val NOTIF_SOUND = booleanPreferencesKey("notif_sound")
        val NOTIF_VIBRATE = booleanPreferencesKey("notif_vibrate")
        val NOTIF_COOLDOWN = intPreferencesKey("notif_cooldown")
        val BACKGROUND = booleanPreferencesKey("background")
    }
}
