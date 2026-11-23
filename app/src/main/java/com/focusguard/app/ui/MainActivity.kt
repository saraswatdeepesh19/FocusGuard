package com.focusguard.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focusguard.app.appContainer
import com.focusguard.app.ui.focus.FocusScreen
import com.focusguard.app.ui.limits.LimitsScreen
import com.focusguard.app.ui.onboarding.OnboardingScreen
import com.focusguard.app.ui.settings.SettingsScreen
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.ui.today.TodayScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusGuardTheme { AppRoot() }
        }
    }
}

@Composable
private fun AppRoot() {
    val container = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val onboardingFlow = remember { container.settings.onboardingDone }
    val onboardingDone by onboardingFlow.collectAsStateWithLifecycle<Boolean?>(initialValue = null)

    when (onboardingDone) {
        null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        false -> OnboardingScreen(
            onFinished = { scope.launch { container.settings.setOnboardingDone(true) } }
        )
        true -> MainScaffold()
    }
}

internal enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.Home),
    LIMITS("limits", "Limits", Icons.Filled.Lock),
    FOCUS("focus", "Focus", Icons.Filled.PlayArrow),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            AppBottomBar(currentRoute = currentRoute, onSelect = { navController.navigateToTab(it.route) })
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.TODAY.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.TODAY.route) {
                TodayScreen(
                    onOpenLimits = { navController.navigateToTab(Destination.LIMITS.route) },
                    onOpenSettings = { navController.navigateToTab(Destination.SETTINGS.route) },
                )
            }
            composable(Destination.LIMITS.route) { LimitsScreen() }
            composable(Destination.FOCUS.route) {
                FocusScreen(
                    onOpenLimits = { navController.navigateToTab(Destination.LIMITS.route) },
                    onOpenSettings = { navController.navigateToTab(Destination.SETTINGS.route) },
                )
            }
            composable(Destination.SETTINGS.route) { SettingsScreen() }
        }
    }
}

@Composable
internal fun AppBottomBar(currentRoute: String?, onSelect: (Destination) -> Unit) {
    NavigationBar {
        Destination.entries.forEach { dest ->
            NavigationBarItem(
                selected = currentRoute == dest.route,
                onClick = { onSelect(dest) },
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(dest.label) },
            )
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
