package com.netwatch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.netwatch.NetWatchApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as? NetWatchApplication
        if (app == null) {
            pending.finish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = app.container.settingsRepository.current()
                if (settings.backgroundMonitoringEnabled && settings.realtimeMonitoringEnabled) {
                    TrafficMonitorService.start(context.applicationContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
