package com.netwatch.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netwatch.core.system.NetworkState
import com.netwatch.core.util.TimeUtils
import com.netwatch.di.AppContainer
import com.netwatch.domain.model.AlertEvent
import com.netwatch.domain.model.HotspotStatus
import com.netwatch.domain.model.MonitorSettings
import com.netwatch.domain.model.NetworkKind
import com.netwatch.domain.model.PlanUsage
import com.netwatch.domain.model.SimSnapshot
import com.netwatch.domain.model.SpeedSample
import com.netwatch.domain.model.TrafficPeriod
import com.netwatch.service.TrafficMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DashboardUiState(
    val settings: MonitorSettings? = null,
    val sim: SimSnapshot? = null,
    val network: NetworkState? = null,
    val sample: SpeedSample? = null,
    val samples: List<SpeedSample> = emptyList(),
    val plans: List<PlanUsage> = emptyList(),
    val todayMobile: TrafficPeriod? = null,
    val hotspot: HotspotStatus? = null,
    val recentAlerts: List<AlertEvent> = emptyList(),
    val rollingWindowBytes: Long = 0,
    val serviceRunning: Boolean = false,
)

class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val left = combine(
                container.settingsRepository.settings,
                container.simManager.snapshots(),
                container.networkStateMonitor.observe(),
                container.alertRepository.recent(5),
            ) { settings, sim, network, alerts ->
                Quad(settings, sim, network, alerts)
            }
            val right = combine(
                TrafficMonitorService.latestSample,
                TrafficMonitorService.samples,
                TrafficMonitorService.running,
            ) { sample, samples, running ->
                Triple(sample, samples, running)
            }
            combine(left, right) { a, b -> a to b }.collectLatest { (a, b) ->
                val (settings, sim, network, alerts) = a
                val (sample, samples, running) = b
                val cutoff = (sample?.elapsedRealtimeMs ?: 0) - settings.rollingWindowSeconds * 1000L
                val rolling = samples.filter { it.elapsedRealtimeMs >= cutoff }.sumOf { it.mobileDelta.total }
                val plans = container.observePlanUsageUseCase.compute(sim)
                val today = container.networkStatsRepository.devicePeriod(
                    NetworkKind.MOBILE,
                    TimeUtils.startOfDay(TimeUtils.today()),
                    TimeUtils.endOfDayExclusive(TimeUtils.today()),
                )
                val hotspot = container.hotspotMonitor.currentStatus().let { status ->
                    val open = container.hotspotRepository.open()
                    if (open != null && status.isTethering) {
                        status.copy(session = with(container.hotspotRepository) { open.toDomain() })
                    } else {
                        status
                    }
                }
                _state.value = DashboardUiState(
                    settings = settings,
                    sim = sim,
                    network = network,
                    sample = sample,
                    samples = samples,
                    plans = plans,
                    todayMobile = today,
                    hotspot = hotspot,
                    recentAlerts = alerts,
                    rollingWindowBytes = rolling,
                    serviceRunning = running,
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
