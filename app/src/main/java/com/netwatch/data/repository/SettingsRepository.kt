package com.netwatch.data.repository

import com.netwatch.core.datastore.SettingsDataStore
import com.netwatch.core.datastore.SettingsPreferences
import com.netwatch.domain.model.MonitorSettings
import com.netwatch.domain.model.PlanConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SettingsRepository(private val dataStore: SettingsDataStore) {
    val settings: Flow<MonitorSettings> = dataStore.settings

    suspend fun current(): MonitorSettings = dataStore.settings.first()

    suspend fun update(transform: (MonitorSettings) -> MonitorSettings) = dataStore.update(transform)

    suspend fun updatePlan(slotIndex: Int, transform: (PlanConfig) -> PlanConfig) {
        dataStore.update { settings ->
            SettingsPreferences.applyPlanUpdate(settings, slotIndex, transform)
        }
    }
}
