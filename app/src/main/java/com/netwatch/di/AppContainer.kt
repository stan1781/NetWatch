package com.netwatch.di

import android.content.Context
import com.netwatch.core.database.NetWatchDatabase
import com.netwatch.core.datastore.SettingsDataStore
import com.netwatch.core.network.NetworkStatsDataSource
import com.netwatch.core.permissions.PermissionChecker
import com.netwatch.core.system.HotspotMonitor
import com.netwatch.core.system.NetworkStateMonitor
import com.netwatch.core.system.SimManager
import com.netwatch.data.repository.AlertRepository
import com.netwatch.data.repository.ExportRepository
import com.netwatch.data.repository.HotspotRepository
import com.netwatch.data.repository.NetworkStatsRepository
import com.netwatch.data.repository.SettingsRepository
import com.netwatch.domain.usecase.ObservePlanUsageUseCase
import com.netwatch.service.MonitoringNotification

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: NetWatchDatabase = NetWatchDatabase.create(appContext)
    val settingsDataStore = SettingsDataStore(appContext)
    val permissionChecker = PermissionChecker(appContext)
    val settingsRepository = SettingsRepository(settingsDataStore)
    val networkStatsDataSource = NetworkStatsDataSource(appContext, permissionChecker)
    val networkStatsRepository = NetworkStatsRepository(
        dataSource = networkStatsDataSource,
        dailyUsageDao = database.dailyUsageDao(),
        attributedDeltaDao = database.attributedDeltaDao(),
    )
    val alertRepository = AlertRepository(database.alertEventDao())
    val hotspotRepository = HotspotRepository(database.hotspotSessionDao())
    val exportRepository = ExportRepository(
        context = appContext,
        dailyUsageDao = database.dailyUsageDao(),
        alertEventDao = database.alertEventDao(),
        hotspotSessionDao = database.hotspotSessionDao(),
        database = database,
        simChangeEventDao = database.simChangeEventDao(),
    )
    val simManager = SimManager(
        context = appContext,
        permissionChecker = permissionChecker,
        identityDao = database.simIdentityDao(),
        changeDao = database.simChangeEventDao(),
    )
    val hotspotMonitor = HotspotMonitor(appContext)
    val networkStateMonitor = NetworkStateMonitor(appContext)
    val monitoringNotification = MonitoringNotification(appContext)
    val observePlanUsageUseCase = ObservePlanUsageUseCase(
        settingsRepository = settingsRepository,
        networkStatsRepository = networkStatsRepository,
    )
}
