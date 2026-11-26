package com.netwatch.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netwatch.core.util.TimeUtils
import com.netwatch.di.AppContainer
import com.netwatch.domain.model.AlertEvent
import com.netwatch.domain.model.AppUsageItem
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.HotspotSession
import com.netwatch.domain.model.NetworkKind
import com.netwatch.domain.model.TrafficPeriod
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class StatsRange { TODAY, YESTERDAY, LAST_7, THIS_MONTH, CUSTOM }

data class DailyPoint(
    val date: LocalDate,
    val rx: Long,
    val tx: Long,
    val confidence: DataConfidence,
)

data class AttributedRow(
    val identityKey: String,
    val slotIndex: Int?,
    val subscriptionId: Int?,
    val rx: Long,
    val tx: Long,
)

data class StatisticsUiState(
    val range: StatsRange = StatsRange.TODAY,
    val customStart: LocalDate = TimeUtils.today().minusDays(7),
    val customEnd: LocalDate = TimeUtils.today(),
    val kind: NetworkKind = NetworkKind.MOBILE,
    val overview: TrafficPeriod? = null,
    val daily: List<DailyPoint> = emptyList(),
    val attributed: List<AttributedRow> = emptyList(),
    val apps: List<AppUsageItem> = emptyList(),
    val sessions: List<HotspotSession> = emptyList(),
    val alerts: List<AlertEvent> = emptyList(),
    val loading: Boolean = true,
    val usageAccessGranted: Boolean = false,
)

class StatisticsViewModel(private val container: AppContainer) : ViewModel() {
    private val range = MutableStateFlow(StatsRange.TODAY)
    private val customStart = MutableStateFlow(TimeUtils.today().minusDays(7))
    private val customEnd = MutableStateFlow(TimeUtils.today())
    private val kind = MutableStateFlow(NetworkKind.MOBILE)
    private val _state = MutableStateFlow(StatisticsUiState())
    val state: StateFlow<StatisticsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(range, customStart, customEnd, kind) { r, s, e, k -> Quad(r, s, e, k) }
                .collectLatest { (r, s, e, k) ->
                    refresh(r, s, e, k)
                }
        }
        viewModelScope.launch {
            combine(container.hotspotRepository.history(), container.alertRepository.recent(100)) { sessions, alerts ->
                sessions to alerts
            }.collectLatest { (sessions, alerts) ->
                _state.value = _state.value.copy(sessions = sessions, alerts = alerts)
            }
        }
    }

    fun setRange(value: StatsRange) {
        range.value = value
    }

    fun setKind(value: NetworkKind) {
        kind.value = value
    }

    fun setCustom(start: LocalDate, end: LocalDate) {
        customStart.value = start
        customEnd.value = end
        range.value = StatsRange.CUSTOM
    }

    private suspend fun refresh(r: StatsRange, startCustom: LocalDate, endCustom: LocalDate, k: NetworkKind) {
        _state.value = _state.value.copy(loading = true, range = r, kind = k, customStart = startCustom, customEnd = endCustom)
        val (start, endExclusive) = bounds(r, startCustom, endCustom)
        val startDate = TimeUtils.dateOf(start)
        val endDate = TimeUtils.dateOf(endExclusive - 1)
        val granted = container.permissionChecker.hasUsageAccess()
        if (granted) {
            var d = startDate
            while (!d.isAfter(endDate)) {
                container.networkStatsRepository.syncOfficialDaily(d)
                d = d.plusDays(1)
            }
        }
        val overview = container.networkStatsRepository.devicePeriod(k, start, endExclusive)
        val dailyRows = container.networkStatsRepository.officialDaily(startDate, endDate)
            .filter { it.kind == k.name && it.identityKey == com.netwatch.data.repository.NetworkStatsRepository.DEVICE_WIDE_KEY }
        val points = dailyRows.map {
            DailyPoint(
                date = LocalDate.ofEpochDay(it.dateEpochDay),
                rx = it.rxBytes,
                tx = it.txBytes,
                confidence = runCatching { DataConfidence.valueOf(it.confidence) }.getOrDefault(DataConfidence.REAL),
            )
        }
        val attributed = container.networkStatsRepository.attributedUsageByIdentity(start, endExclusive).map {
            AttributedRow(it.identityKey, it.slotIndex, it.subscriptionId, it.rxBytes, it.txBytes)
        }
        val apps = if (granted) container.networkStatsRepository.appUsage(k, start, endExclusive) else emptyList()
        _state.value = _state.value.copy(
            overview = overview,
            daily = points,
            attributed = attributed,
            apps = apps,
            loading = false,
            usageAccessGranted = granted,
        )
    }

    private fun bounds(range: StatsRange, startCustom: LocalDate, endCustom: LocalDate): Pair<Long, Long> {
        val today = TimeUtils.today()
        return when (range) {
            StatsRange.TODAY -> TimeUtils.startOfDay(today) to TimeUtils.endOfDayExclusive(today)
            StatsRange.YESTERDAY -> TimeUtils.startOfDay(today.minusDays(1)) to TimeUtils.endOfDayExclusive(today.minusDays(1))
            StatsRange.LAST_7 -> TimeUtils.startOfDay(today.minusDays(6)) to TimeUtils.endOfDayExclusive(today)
            StatsRange.THIS_MONTH -> TimeUtils.monthRange()
            StatsRange.CUSTOM -> TimeUtils.startOfDay(startCustom) to TimeUtils.endOfDayExclusive(endCustom)
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
