package com.netwatch.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.netwatch.domain.model.MonitorSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "netwatch_settings",
)

class SettingsDataStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.settingsDataStore)

    val settings: Flow<MonitorSettings> = dataStore.data.map { prefs ->
        SettingsPreferences.toSettings(prefs)
    }

    suspend fun update(transform: (MonitorSettings) -> MonitorSettings) {
        dataStore.edit { prefs ->
            val current = SettingsPreferences.toSettings(prefs)
            SettingsPreferences.write(prefs, transform(current))
        }
    }

    companion object {
        const val UNSET = SettingsPreferences.UNSET
    }
}
