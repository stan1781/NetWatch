package com.netwatch.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.netwatch.feature.dashboard.DashboardScreen
import com.netwatch.feature.diagnostics.DiagnosticsScreen
import com.netwatch.feature.realtime.RealtimeScreen
import com.netwatch.feature.settings.AboutScreen
import com.netwatch.feature.settings.SettingsScreen
import com.netwatch.feature.sim.SimPlanScreen
import com.netwatch.feature.statistics.StatisticsScreen

@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Routes.Dashboard,
        modifier = modifier,
    ) {
        composable(Routes.Dashboard) {
            DashboardScreen(
                onOpenSimPlan = { slot -> navController.navigate(Routes.simPlan(slot)) },
                onOpenRealtime = { navController.navigate(Routes.Realtime) },
                onOpenStatistics = { navController.navigateToTop(Routes.Statistics) },
                onOpenSettings = { navController.navigateToTop(Routes.Settings) },
            )
        }
        composable(Routes.Statistics) { StatisticsScreen() }
        composable(Routes.Settings) {
            SettingsScreen(
                onOpenSimPlan = { slot -> navController.navigate(Routes.simPlan(slot)) },
                onOpenDiagnostics = { navController.navigate(Routes.Diagnostics) },
                onOpenAbout = { navController.navigate(Routes.About) },
            )
        }
        composable(
            route = Routes.SimPlan,
            arguments = listOf(navArgument("slotIndex") { type = NavType.IntType }),
        ) { entry ->
            val slot = entry.arguments?.getInt("slotIndex") ?: 0
            SimPlanScreen(slotIndex = slot, onBack = { navController.popBackStack() })
        }
        composable(Routes.Realtime) {
            RealtimeScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.Diagnostics) {
            DiagnosticsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.About) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun NavHostController.navigateToTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
