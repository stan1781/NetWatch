package com.netwatch

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.netwatch.di.LocalAppContainer
import com.netwatch.navigation.AppNavigation
import com.netwatch.navigation.Routes
import com.netwatch.ui.theme.NetWatchTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val phonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        phonePermission.launch(Manifest.permission.READ_PHONE_STATE)
        val container = (application as NetWatchApplication).container
        setContent {
            NetWatchTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    NetWatchApp()
                }
            }
        }
    }
}

private data class TopDest(val route: String, val labelRes: Int, val icon: ImageVector)

@Composable
fun NetWatchApp() {
    val navController = rememberNavController()
    val destinations = listOf(
        TopDest(Routes.Dashboard, R.string.nav_dashboard, Icons.Outlined.Home),
        TopDest(Routes.Statistics, R.string.nav_statistics, Icons.Outlined.Insights),
        TopDest(Routes.Settings, R.string.nav_settings, Icons.Outlined.Settings),
    )
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = current in destinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    destinations.forEach { dest ->
                        NavigationBarItem(
                            selected = current == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        AppNavigation(navController = navController, modifier = Modifier.padding(padding))
    }
}
