package com.anant.freescale.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

sealed class FreeScaleDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Home : FreeScaleDestination("home", "Home", Icons.Outlined.Home)
    data object Progress : FreeScaleDestination("progress", "Progress", Icons.Outlined.Timeline)
    data object Settings : FreeScaleDestination("settings", "Settings", Icons.Outlined.Settings)

    companion object {
        val bottomTabs = listOf(Home, Progress, Settings)
    }
}
