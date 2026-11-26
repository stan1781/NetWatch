package com.netwatch.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netwatch.di.LocalAppContainer
import com.netwatch.ui.appViewModel
import com.netwatch.ui.components.StatusChip

@Composable
fun DashboardScreen(
    onOpenSimPlan: (Int) -> Unit,
    onOpenRealtime: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = appViewModel { DashboardViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val network = state.network

    Column(Modifier.fillMaxSize()) {
        DashboardHeader(
            hasInternet = network?.hasInternet == true,
            isValidated = network?.isValidated == true,
            onOpenSettings = onOpenSettings,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                DualSimOverview(
                    plans = state.plans,
                    cards = state.sim?.cards.orEmpty(),
                    roles = state.settings?.simPlans?.mapValues { it.value.role }.orEmpty(),
                    onOpenSimPlan = onOpenSimPlan,
                )
            }
            item {
                CurrentDataSimCard(
                    sim = state.sim,
                    network = network,
                    roles = state.settings?.simPlans?.mapValues { it.value.role }.orEmpty(),
                )
            }
            item {
                RealtimeSpeedCard(
                    sample = state.sample,
                    samples = state.samples,
                    onClick = onOpenRealtime,
                )
            }
            item {
                TodayTrafficCard(
                    today = state.todayMobile,
                    onClick = onOpenStatistics,
                )
            }
            item {
                HotspotStatusCard(
                    hotspot = state.hotspot,
                    onClick = onOpenStatistics,
                )
            }
            item {
                RecentAlertsCard(
                    alerts = state.recentAlerts,
                    onOpenAll = onOpenStatistics,
                )
            }
        }
    }
}

@Composable
fun DashboardHeader(
    hasInternet: Boolean,
    isValidated: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "NetWatch",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        StatusChip(DashboardPresentation.headerNetworkStatus(hasInternet, isValidated))
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "设置")
        }
    }
}
