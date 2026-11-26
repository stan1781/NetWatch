package com.netwatch.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.netwatch.NetWatchApplication
import com.netwatch.core.util.TimeUtils
import com.netwatch.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

class DailyUsageSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? NetWatchApplication ?: return Result.retry()
        return try {
            val repo = app.container.networkStatsRepository
            repo.syncOfficialDaily(TimeUtils.today())
            repo.syncOfficialDaily(TimeUtils.today().minusDays(1))
            WidgetUpdater.update(applicationContext)
            val settings = app.container.settingsRepository.current()
            if (settings.backgroundMonitoringEnabled &&
                settings.realtimeMonitoringEnabled &&
                !TrafficMonitorService.running.value
            ) {
                TrafficMonitorService.start(applicationContext)
            }
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "netwatch-daily-sync"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyUsageSyncWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
