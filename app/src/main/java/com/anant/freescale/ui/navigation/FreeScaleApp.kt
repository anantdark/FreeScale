package com.anant.freescale.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.anant.freescale.BuildConfig
import com.anant.freescale.MeasureViewModel
import com.anant.freescale.ui.home.HomeScreen
import com.anant.freescale.ui.progress.ProgressScreen
import com.anant.freescale.ui.settings.SettingsScreen
import com.anant.freescale.ui.settings.UpdateAvailableDialog
import kotlinx.coroutines.delay

@Composable
fun FreeScaleApp(vm: MeasureViewModel) {
    val navController = rememberNavController()
    val state by vm.ui.collectAsStateWithLifecycle()
    val debugMode by vm.debugMode.collectAsStateWithLifecycle()
    val materialYou by vm.materialYou.collectAsStateWithLifecycle()
    val autoConnect by vm.autoConnect.collectAsStateWithLifecycle()
    val reduceAnimations by vm.reduceAnimations.collectAsStateWithLifecycle()
    val crashReportingEnabled by vm.crashReportingEnabled.collectAsStateWithLifecycle()
    val autoCheckUpdates by vm.autoCheckUpdates.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val developerModeUnlocked by vm.developerModeUnlocked.collectAsStateWithLifecycle()
    val readingAnimationChoice by vm.readingAnimationChoice.collectAsStateWithLifecycle()
    val forceShowLoadingAnimations by vm.forceShowLoadingAnimations.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val uriHandler = LocalUriHandler.current

    // One delayed silent update check per process when the toggle is on.
    var startupUpdateChecked by remember { mutableStateOf(false) }
    LaunchedEffect(autoCheckUpdates) {
        if (BuildConfig.IS_FDROID || !autoCheckUpdates || startupUpdateChecked) return@LaunchedEffect
        startupUpdateChecked = true
        delay(1_500)
        vm.checkForUpdates(silent = true)
    }

    updateState.updateInfo?.let { info ->
        UpdateAvailableDialog(
            info = info,
            onDismiss = vm::dismissUpdatePrompt,
            onDownload = { url ->
                uriHandler.openUri(url)
                vm.acknowledgeUpdateDownloadStarted()
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                FreeScaleDestination.bottomTabs.forEach { dest ->
                    val selected = currentRoute == dest.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(dest.icon, contentDescription = dest.label)
                        },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = FreeScaleDestination.Home.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                composable(FreeScaleDestination.Home.route) {
                    HomeScreen(
                        vm = vm,
                        debugMode = debugMode,
                        reduceAnimations = reduceAnimations,
                        readingAnimationChoice = readingAnimationChoice,
                        forceShowLoadingAnimations = forceShowLoadingAnimations,
                    )
                }
                composable(FreeScaleDestination.Progress.route) {
                    ProgressScreen()
                }
                composable(FreeScaleDestination.Settings.route) {
                    SettingsScreen(
                        heightCm = state.heightCm,
                        ageYears = state.ageYears,
                        male = state.male,
                        onHeightChange = vm::setHeight,
                        onAgeChange = vm::setAge,
                        onMaleChange = vm::setMale,
                        materialYou = materialYou,
                        onMaterialYouChange = vm::setMaterialYou,
                        autoConnect = autoConnect,
                        onAutoConnectChange = vm::setAutoConnect,
                        debugMode = debugMode,
                        onDebugModeChange = vm::setDebugMode,
                        reduceAnimations = reduceAnimations,
                        onReduceAnimationsChange = vm::setReduceAnimations,
                        crashReportingEnabled = crashReportingEnabled,
                        onCrashReportingChange = vm::setCrashReportingEnabled,
                        autoCheckUpdates = autoCheckUpdates,
                        onAutoCheckUpdatesChange = vm::setAutoCheckUpdates,
                        updateState = updateState,
                        onCheckForUpdates = { vm.checkForUpdates() },
                        developerModeUnlocked = developerModeUnlocked,
                        onDeveloperModeToggled = vm::setDeveloperModeUnlocked,
                        readingAnimationChoice = readingAnimationChoice,
                        onReadingAnimationChoiceChange = vm::setReadingAnimationChoice,
                        forceShowLoadingAnimations = forceShowLoadingAnimations,
                        onForceShowLoadingAnimationsChange = vm::setForceShowLoadingAnimations,
                        onHeartDoubleTapHeartbeat = vm::sendHeartbeatFromLoveTap,
                    )
                }
            }
        }
    }
}
