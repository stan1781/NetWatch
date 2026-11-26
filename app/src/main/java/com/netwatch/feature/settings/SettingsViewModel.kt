package com.netwatch.feature.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netwatch.di.AppContainer
import com.netwatch.domain.model.MonitorSettings
import com.netwatch.service.TrafficMonitorService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<MonitorSettings?> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val serviceRunning = TrafficMonitorService.running

    fun update(transform: (MonitorSettings) -> MonitorSettings) {
        viewModelScope.launch { container.settingsRepository.update(transform) }
    }

    fun setMonitoring(enabled: Boolean, context: Context) {
        update { it.copy(realtimeMonitoringEnabled = enabled) }
        if (enabled) TrafficMonitorService.start(context) else TrafficMonitorService.stop(context)
    }

    fun setBackground(enabled: Boolean, context: Context) {
        update { it.copy(backgroundMonitoringEnabled = enabled) }
        if (enabled && (settings.value?.realtimeMonitoringEnabled != false)) {
            TrafficMonitorService.start(context)
        }
    }

    suspend fun export(context: Context): Boolean {
        val file = container.exportRepository.exportJson()
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出 NetWatch 数据"))
        return true
    }

    fun clearData() {
        viewModelScope.launch { container.exportRepository.clearAll() }
    }
}
