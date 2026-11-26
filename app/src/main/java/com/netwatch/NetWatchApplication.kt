package com.netwatch

import android.app.Application
import com.netwatch.di.AppContainer
import com.netwatch.service.DailyUsageSyncWorker
import com.netwatch.service.TrafficMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NetWatchApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DailyUsageSyncWorker.enqueue(this)
        scope.launch {
            val settings = container.settingsRepository.current()
            if (settings.backgroundMonitoringEnabled && settings.realtimeMonitoringEnabled) {
                TrafficMonitorService.start(this@NetWatchApplication)
            }
        }
    }
}
