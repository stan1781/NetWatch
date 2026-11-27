package com.netwatch.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.netwatch.NetWatchApplication
import com.netwatch.core.network.TrafficStatsSampler
import com.netwatch.core.util.ByteFormatter
import com.netwatch.core.util.TimeUtils
import com.netwatch.domain.calculator.AlertEngine
import com.netwatch.domain.calculator.PostSwitchSettler
import com.netwatch.domain.model.ByteVolume
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.NetworkKind
import com.netwatch.domain.model.SpeedSample
import com.netwatch.widget.WidgetUpdater
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrafficMonitorService : LifecycleService() {
    private val sampler = TrafficStatsSampler()
    private val alertEngine = AlertEngine()
    private val postSwitchSettler = PostSwitchSettler()
    private var loop: Job? = null
    private var lastDefaultSub: Int? = null
    private var lastTethering: Boolean = false
    private var lastDailySyncDay: Long? = null
    private var nextAlertId = 1L
    private val started = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        val container = (application as NetWatchApplication).container
        val notification = container.monitoringNotification
        lifecycleScope.launch {
            val settings = container.settingsRepository.current()
            notification.ensureChannels(settings)
            startForeground(
                MonitoringNotification.MONITOR_ID,
                notification.monitorNotification(null, "—"),
            )
        }
        running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (started.compareAndSet(false, true)) {
            startLoop()
        }
        return Service.START_STICKY
    }

    private fun startLoop() {
        val container = (application as NetWatchApplication).container
        loop = lifecycleScope.launch(Dispatchers.Default) {
            var settings = container.settingsRepository.current()
            launch {
                container.settingsRepository.settings.collectLatest { settings = it }
            }
            launch {
                container.hotspotMonitor.events().collectLatest { interfaces ->
                    val tethering = interfaces.isNotEmpty()
                    if (tethering && !lastTethering) {
                        val sim = container.simManager.current()
                        val session = container.hotspotMonitor.snapshotSession(
                            id = 0,
                            slot = sim.defaultDataSlotIndex,
                            sub = sim.defaultDataSubscriptionId,
                            ended = false,
                        )
                        if (session != null) {
                            container.hotspotRepository.insert(session)
                        }
                    } else if (!tethering && lastTethering) {
                        val open = container.hotspotRepository.open()
                        if (open != null) {
                            container.hotspotRepository.update(
                                open.copy(endedAtMs = System.currentTimeMillis()),
                            )
                        }
                    }
                    lastTethering = tethering
                }
            }
            sampler.resetBaseline()
            alertEngine.resetBaselines()
            lastDefaultSub = container.simManager.current().defaultDataSubscriptionId

            while (isActive) {
                if (!settings.realtimeMonitoringEnabled && !settings.backgroundMonitoringEnabled) {
                    stopSelf()
                    break
                }
                val now = System.currentTimeMillis()
                val sim = container.simManager.current()
                val defaultSub = sim.defaultDataSubscriptionId
                val resetNeeded = defaultSub != lastDefaultSub
                if (resetNeeded) {
                    sampler.resetBaseline()
                    alertEngine.resetBaselines()
                    lastDefaultSub = defaultSub
                    postSwitchSettler.onDefaultDataSwitch()
                    container.simManager.persistIdentityChanges(sim)
                }
                val tethering = lastTethering || container.hotspotMonitor.currentStatus().isTethering
                val network = container.networkStateMonitor.current(isTethering = tethering)
                val raw = sampler.sample(now, sim.defaultDataSubscriptionId, sim.defaultDataSlotIndex)
                val settle = resetNeeded || postSwitchSettler.consumePending()
                val sample = if (settle || raw.baselineReset) {
                    raw.asBaselineReset().copy(path = network.path)
                } else {
                    raw.copy(path = network.path)
                }

                samples.value = (samples.value + sample).takeLast(MAX_SAMPLES)
                latestSample.value = sample

                if (!sample.baselineReset) {
                    if (sample.path.billableMobile) {
                        container.networkStatsRepository.recordAttributedDelta(sample)
                    }
                    if (settings.hotspotMonitoringEnabled) {
                        container.hotspotMonitor.addDelta(
                            sample.mobileDelta.rxBytes,
                            sample.mobileDelta.txBytes,
                        )
                        val open = container.hotspotRepository.open()
                        val live = container.hotspotMonitor.snapshotSession(
                            id = open?.id ?: 0,
                            slot = sim.defaultDataSlotIndex,
                            sub = sim.defaultDataSubscriptionId,
                            ended = false,
                        )
                        if (open != null && live != null) {
                            container.hotspotRepository.update(
                                open.copy(rxBytes = live.rxBytes, txBytes = live.txBytes),
                            )
                        }
                    }
                }

                val plans = container.observePlanUsageUseCase.compute(sim)
                val alerts = alertEngine.onSample(sample, settings, plans, now, nextAlertId)
                if (alerts.isNotEmpty()) {
                    nextAlertId = alerts.maxOf { it.id } + 1
                    container.monitoringNotification.ensureChannels(settings)
                    alerts.forEach { event ->
                        container.alertRepository.insert(event)
                        container.monitoringNotification.notifyAlert(event, settings)
                    }
                    alertEvents.tryEmit(alerts)
                }

                val today = container.networkStatsRepository.devicePeriod(
                    NetworkKind.MOBILE,
                    TimeUtils.startOfDay(TimeUtils.today()),
                    TimeUtils.endOfDayExclusive(TimeUtils.today()),
                )
                val todayLabel = if (today.confidence != DataConfidence.UNAVAILABLE) {
                    ByteFormatter.formatBytes(today.volume.total)
                } else {
                    "—"
                }
                container.monitoringNotification.ensureChannels(settings)
                startForeground(
                    MonitoringNotification.MONITOR_ID,
                    container.monitoringNotification.monitorNotification(sample, todayLabel),
                )

                val day = TimeUtils.today().toEpochDay()
                if (lastDailySyncDay != day) {
                    container.networkStatsRepository.syncOfficialDaily(TimeUtils.today())
                    container.networkStatsRepository.syncOfficialDaily(TimeUtils.today().minusDays(1))
                    lastDailySyncDay = day
                }

                WidgetUpdater.update(this@TrafficMonitorService)
                delay(settings.sampleIntervalMs.coerceIn(500, 10_000).toLong())
            }
        }
    }

    override fun onDestroy() {
        running.value = false
        loop?.cancel()
        started.set(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    companion object {
        private const val MAX_SAMPLES = 120
        val running = MutableStateFlow(false)
        val latestSample = MutableStateFlow<SpeedSample?>(null)
        val samples = MutableStateFlow<List<SpeedSample>>(emptyList())
        private val alertEvents = MutableSharedFlow<List<com.netwatch.domain.model.AlertEvent>>(extraBufferCapacity = 8)
        val alerts: SharedFlow<List<com.netwatch.domain.model.AlertEvent>> = alertEvents.asSharedFlow()

        fun start(context: Context) {
            val intent = Intent(context, TrafficMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrafficMonitorService::class.java))
        }
    }
}
