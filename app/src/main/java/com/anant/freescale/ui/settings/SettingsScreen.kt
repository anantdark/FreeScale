package com.anant.freescale.ui.settings

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.anant.freescale.BuildConfig
import com.anant.freescale.R
import com.anant.freescale.UpdateUiState
import com.anant.freescale.data.remote.UpdateCheckResult
import com.anant.freescale.ui.components.ConfettiOverlay
import com.anant.freescale.ui.components.CraftedWithLoveCredit
import com.anant.freescale.ui.loading.LoadingAnimChoice
import com.anant.freescale.ui.loading.LoadingAnimationHost
import com.anant.freescale.ui.loading.LoadingAnimationRegistry
import com.anant.freescale.ui.loading.LoadingAnimationSlot
import com.anant.freescale.ui.loading.LoadingHoldBoostMultiplier
import com.anant.freescale.ui.loading.loadingHoldToBoost
import kotlinx.coroutines.delay

private const val DEVELOPER_UNLOCK_TAPS = 31
private const val DEVELOPER_HINT_START = DEVELOPER_UNLOCK_TAPS - 5

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
    crashReportingEnabled: Boolean,
    onCrashReportingChange: (Boolean) -> Unit,
    autoCheckUpdates: Boolean = false,
    onAutoCheckUpdatesChange: (Boolean) -> Unit = {},
    updateState: UpdateUiState = UpdateUiState(),
    onCheckForUpdates: () -> Unit = {},
    developerModeUnlocked: Boolean = false,
    onDeveloperModeToggled: (Boolean) -> Unit = {},
    readingAnimationChoice: String = LoadingAnimChoice.RANDOM,
    onReadingAnimationChoiceChange: (String) -> Unit = {},
    forceShowLoadingAnimations: Boolean = false,
    onForceShowLoadingAnimationsChange: (Boolean) -> Unit = {},
    onHeartDoubleTapHeartbeat: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    var packageTapCount by remember { mutableIntStateOf(0) }
    var developerUnlockHint by remember { mutableStateOf<String?>(null) }
    var confettiKey by remember { mutableIntStateOf(0) }
    var showConfetti by remember { mutableStateOf(false) }
    var showAnimationPreview by remember { mutableStateOf(false) }

    LaunchedEffect(confettiKey) {
        if (confettiKey == 0) return@LaunchedEffect
        showConfetti = true
        delay(4_200)
        showConfetti = false
    }

    LaunchedEffect(packageTapCount) {
        if (packageTapCount in 1 until DEVELOPER_UNLOCK_TAPS) {
            delay(2_500)
            packageTapCount = 0
            developerUnlockHint = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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

        SettingsSection(
            title = "Updates & support",
            description = "GitHub releases and optional anonymous crash reports.",
        ) {
            Text(
                text = "Installed ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (BuildConfig.IS_FDROID) {
                Text(
                    text = "Updates are handled by F-Droid.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "To get the latest updates, install the GitHub Releases version",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://github.com/anantdark/FreeScale/releases")
                    },
                )
            } else {
                SettingsToggleRow(
                    title = "Check for updates",
                    subtitle = "Looks for a newer GitHub release shortly after startup.",
                    checked = autoCheckUpdates,
                    onCheckedChange = onAutoCheckUpdatesChange,
                )
                if (!autoCheckUpdates) {
                    OutlinedButton(
                        onClick = onCheckForUpdates,
                        enabled = !updateState.isChecking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (updateState.isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("Checking…")
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Check for updates")
                        }
                    }
                } else if (updateState.isChecking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Checking for updates…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                updateState.statusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (updateState.statusIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            SettingsToggleRow(
                title = "Send crash reports",
                subtitle = if (BuildConfig.IS_FDROID) {
                    "Off by default on F-Droid. Anonymous stack traces only — no scale " +
                        "readings or personal data. When on, may send one daily heartbeat " +
                        "(Cron / Metrics / Logs — not Issues)."
                } else {
                    "On by default for GitHub builds. Anonymous stack traces help fix bugs. " +
                        "No scale readings or personal data. When on, may send one daily " +
                        "heartbeat (Cron / Metrics / Logs — not Issues)."
                },
                checked = crashReportingEnabled,
                onCheckedChange = onCrashReportingChange,
            )
        }

        SettingsSection(
            title = "About",
            collapsible = false,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutRow("App", "FreeScale")
                AboutRow(
                    "Version",
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
                AboutRow(
                    label = "Package",
                    value = BuildConfig.APPLICATION_ID,
                    onValueClick = {
                        packageTapCount++
                        when {
                            packageTapCount >= DEVELOPER_UNLOCK_TAPS -> {
                                packageTapCount = 0
                                developerUnlockHint = null
                                onDeveloperModeToggled(!developerModeUnlocked)
                            }
                            packageTapCount >= DEVELOPER_HINT_START -> {
                                developerUnlockHint =
                                    "${DEVELOPER_UNLOCK_TAPS - packageTapCount} more taps…"
                            }
                            else -> developerUnlockHint = null
                        }
                    },
                )
                developerUnlockHint?.let { hint ->
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (BuildConfig.IS_FDROID) {
                    AboutRow("Channel", "F-Droid")
                } else {
                    AboutRow("Channel", "GitHub")
                }
                AboutRow(
                    label = "Created by",
                    valueContent = {
                        RainbowCreditBadge(
                            name = "Anant",
                            onClick = { uriHandler.openUri("https://github.com/anantdark") },
                        )
                    },
                )
                AboutLinkRow(
                    label = "GitHub",
                    value = "github.com/anantdark",
                    url = "https://github.com/anantdark",
                )
                AboutLinkRow(
                    label = "Source",
                    value = "github.com/anantdark/FreeScale",
                    url = "https://github.com/anantdark/FreeScale",
                )
                AboutLinkRow(
                    label = "Big thanks to",
                    value = "oliexdev/openScale",
                    url = "https://github.com/oliexdev/openScale",
                )
            }
        }

        if (developerModeUnlocked) {
            SettingsSection(
                title = "Developer",
                description = "Debug tools. Tap Package 31 times again to hide this section.",
            ) {
                Text(
                    text = "Measuring banner animations",
                    style = MaterialTheme.typography.titleMedium,
                )
                LoadingAnimationChoiceDropdown(
                    label = "Reading animation",
                    selected = readingAnimationChoice,
                    slot = LoadingAnimationSlot.READING,
                    onSelect = onReadingAnimationChoiceChange,
                )
                SettingsToggleRow(
                    title = "Keep animation visible on Home",
                    subtitle = "Force-show the measuring banner without weighing.",
                    checked = forceShowLoadingAnimations,
                    onCheckedChange = onForceShowLoadingAnimationsChange,
                )
                OutlinedButton(
                    onClick = { showAnimationPreview = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Preview measuring animation")
                }
                OutlinedButton(
                    onClick = { throw RuntimeException("FreeScale Sentry test crash") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Force test crash")
                }
            }
        }

        CraftedWithLoveCredit(
            onHeartDoubleTap = {
                confettiKey += 1
                onHeartDoubleTapHeartbeat()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 24.dp),
        )
    }

        if (showConfetti) {
            key(confettiKey) {
                ConfettiOverlay(
                    modifier = Modifier.fillMaxSize(),
                    durationMillis = 4_000,
                    grand = true,
                )
            }
        }
    }

    if (showAnimationPreview) {
        AnimationPreviewDialog(
            animationChoice = readingAnimationChoice,
            onDismiss = { showAnimationPreview = false },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String? = null,
    collapsible: Boolean = true,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded || !collapsible) }
    val sectionVisible = expanded || !collapsible
    // Keep expand/shrink specs matched; avoid fade — it finishes before height and feels abrupt.
    val expandSpec = remember {
        expandVertically(
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top,
        )
    }
    val shrinkSpec = remember {
        shrinkVertically(
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top,
        )
    }

    // No spacedBy on this Column: spacing under the header lives inside AnimatedVisibility
    // so it animates away with the body instead of snapping off at exit end.
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (collapsible) {
                            Modifier.clickable { expanded = !expanded }
                        } else {
                            Modifier
                        },
                    ),
            )
            if (collapsible) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
        }
        AnimatedVisibility(
            visible = sectionVisible,
            enter = expandSpec,
            exit = shrinkSpec,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!description.isNullOrBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content()
            }
        }
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

@Composable
private fun AboutRow(
    label: String,
    value: String,
    onValueClick: (() -> Unit)? = null,
) {
    AboutRow(
        label = label,
        onRowClick = onValueClick,
        valueContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        },
    )
}

@Composable
private fun AboutRow(
    label: String,
    onRowClick: (() -> Unit)? = null,
    valueContent: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onRowClick != null) {
                    Modifier.clickable(onClick = onRowClick)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        valueContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadingAnimationChoiceDropdown(
    label: String,
    selected: String,
    slot: LoadingAnimationSlot,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(slot) {
        listOf(
            LoadingAnimChoice.RANDOM to "Random",
            LoadingAnimChoice.OFF to "Off (spinner)",
        ) + LoadingAnimationRegistry.forSlot(slot).map { it.id to it.displayName }
    }
    val displayLabel = options.firstOrNull { it.first == selected }?.second
        ?: selected.ifBlank { "Random" }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun AnimationPreviewDialog(
    animationChoice: String,
    onDismiss: () -> Unit,
) {
    var touchSpeedMultiplier by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        delay(8_000)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Animation preview") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                LoadingAnimationHost(
                    slot = LoadingAnimationSlot.READING,
                    animationChoice = animationChoice,
                    modifier = Modifier.fillMaxSize(),
                    label = "PREVIEW",
                    captions = listOf(
                        "developer preview…",
                        "hold to speed up…",
                        "tap dismiss anytime…",
                    ),
                    speedMultiplier = touchSpeedMultiplier,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .loadingHoldToBoost { boosting ->
                            touchSpeedMultiplier = if (boosting) {
                                LoadingHoldBoostMultiplier
                            } else {
                                1f
                            }
                        },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        },
    )
}

@Composable
private fun AboutLinkRow(label: String, value: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri(url) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.Code,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun UpdateAvailableDialog(
    info: UpdateCheckResult.Available,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val highlights = remember(info.releaseNotes) { releaseNoteHighlights(info.releaseNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = info.versionName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Build ${info.versionCode}  ·  you have ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (highlights.isNotEmpty()) {
                    Text(
                        text = "What's new",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        highlights.forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•", color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                if (info.htmlUrl.isNotBlank()) {
                    TextButton(onClick = { uriHandler.openUri(info.htmlUrl) }) {
                        Text("View on GitHub")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDownload(info.downloadUrl) }) {
                Text("Download APK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        },
    )
}

/** Pull a few bullet-ish lines from GitHub release notes for the update dialog. */
private fun releaseNoteHighlights(body: String, maxLines: Int = 8): List<String> {
    return body.lineSequence()
        .map { it.trim().removePrefix("-").removePrefix("*").trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("**Version") }
        .take(maxLines)
        .toList()
}

private val rainbowAnimationSpec = infiniteRepeatable<Float>(
    animation = tween(durationMillis = 2800, easing = LinearEasing),
    repeatMode = RepeatMode.Restart,
)

@Composable
private fun rememberCyclingHue(): Float {
    val transition = rememberInfiniteTransition(label = "createdByRainbow")
    val hue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = rainbowAnimationSpec,
        label = "hue",
    )
    return hue
}

private fun hsvRainbow(hue: Float): Color =
    Color.hsv(hue = ((hue % 360f) + 360f) % 360f, saturation = 0.85f, value = 0.95f)

private fun cyclingRainbowText(text: String, hue: Float): AnnotatedString {
    val stepDegrees = 360f / text.length.coerceAtLeast(1)
    return buildAnnotatedString {
        text.forEachIndexed { index, char ->
            withStyle(SpanStyle(color = hsvRainbow(hue + index * stepDegrees))) {
                append(char)
            }
        }
    }
}

private fun rainbowBorderBrush(hue: Float): Brush {
    val stops = 8
    val colors = List(stops + 1) { i ->
        hsvRainbow(hue + i * (360f / stops))
    }
    return Brush.sweepGradient(colors = colors)
}

/** Name + party parrot inside a hue-cycling rainbow border. */
@Composable
private fun RainbowCreditBadge(name: String, onClick: () -> Unit) {
    val hue = rememberCyclingHue()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(
                width = 1.5.dp,
                brush = rainbowBorderBrush(hue),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = cyclingRainbowText(name, hue),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(4.dp))
        PartyParrot()
    }
}

@Composable
private fun PartyParrot(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                contentDescription = "Party parrot"
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                val drawable = ImageDecoder.decodeDrawable(
                    ImageDecoder.createSource(context.resources, R.raw.party_parrot),
                )
                setImageDrawable(drawable)
                if (drawable is Animatable) drawable.start()
            }
        },
        modifier = modifier.size(20.dp),
    )
}
