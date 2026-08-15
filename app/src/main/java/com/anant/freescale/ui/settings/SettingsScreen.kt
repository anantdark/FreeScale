package com.anant.freescale.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    heightCm: String,
    ageYears: String,
    male: Boolean,
    onHeightChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onMaleChange: (Boolean) -> Unit,
    materialYou: Boolean,
    onMaterialYouChange: (Boolean) -> Unit,
    autoConnect: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    debugMode: Boolean,
    onDebugModeChange: (Boolean) -> Unit,
    reduceAnimations: Boolean,
    onReduceAnimationsChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)

        SettingsSection(
            title = "Profile",
            description = "Used for body composition. Saved on this phone across restarts.",
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = heightCm,
                    onValueChange = onHeightChange,
                    label = { Text("Height cm") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    value = ageYears,
                    onValueChange = onAgeChange,
                    label = { Text("Age") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = male,
                    onClick = { onMaleChange(true) },
                    label = { Text("Male") },
                )
                FilterChip(
                    selected = !male,
                    onClick = { onMaleChange(false) },
                    label = { Text("Female") },
                )
            }
        }

        SettingsSection(
            title = "Connection",
            description = "How FreeScale finds your scale.",
        ) {
            SettingsToggleRow(
                title = "Auto-connect",
                subtitle = "When the app opens, scan and link to SSW532 automatically.",
                checked = autoConnect,
                onCheckedChange = onAutoConnectChange,
            )
        }

        SettingsSection(
            title = "Appearance",
            description = "Light and dark follow the system theme.",
        ) {
            SettingsToggleRow(
                title = "Material You",
                subtitle = "Use wallpaper colors (Android 12+). Off uses a generic Material palette.",
                checked = materialYou,
                onCheckedChange = onMaterialYouChange,
            )
        }

        SettingsSection(
            title = "Units",
            description = "Metric (kg / cm). Imperial toggle arrives later.",
        ) {
            // Placeholder until imperial lands.
        }

        SettingsSection(
            title = "Experimental",
            description = "Optional tools for development and troubleshooting.",
        ) {
            SettingsToggleRow(
                title = "Reduce animations",
                subtitle = "Skip decorative motion on Home. Keep essential weigh-in feedback.",
                checked = reduceAnimations,
                onCheckedChange = onReduceAnimationsChange,
            )
            SettingsToggleRow(
                title = "Debug mode",
                subtitle = "Show raw impedance, BLE frames, WLA25 inputs, and logs on Home.",
                checked = debugMode,
                onCheckedChange = onDebugModeChange,
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
