package com.anant.freescale.ui.home

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anant.freescale.MeasureViewModel
import com.anant.freescale.UiState
import com.anant.freescale.ble.ScannedScale
import com.anant.freescale.data.MeasurePhase
import com.anant.freescale.ui.MeasurementDetail
import com.anant.freescale.ui.loading.LoadingAnimChoice
import com.anant.freescale.ui.loading.LoadingAnimationHost
import com.anant.freescale.ui.loading.LoadingAnimationRegistry
import com.anant.freescale.ui.loading.LoadingAnimationSlot
import com.anant.freescale.ui.loading.LoadingHoldBoostMultiplier
import com.anant.freescale.ui.loading.loadingHoldToBoost
import com.anant.freescale.ui.loading.animations.TirangaChakraNavy
import com.anant.freescale.ui.theme.MetricValueStyle
import com.anant.freescale.ui.theme.PlexMonoFamily
import com.anant.freescale.ui.theme.ReadoutStyle
import com.anant.freescale.ui.theme.ReadoutUnitStyle
import com.anant.freescale.util.BleLogger
import java.util.Locale

@Composable
private fun WeightOnlyConfirmDialog(
    weightKg: Float,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDiscard,
        title = { Text("Log weight-only reading?") },
        text = {
            Text(
                "No handlebar / BIA data this time. " +
                    "Save ${"%.2f".format(Locale.US, weightKg)} kg, or discard it?",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Log reading")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard")
            }
        },
    )
}

@Composable
fun HomeScreen(
    vm: MeasureViewModel,
    debugMode: Boolean,
    reduceAnimations: Boolean,
    readingAnimationChoice: String = LoadingAnimChoice.RANDOM,
    forceShowLoadingAnimations: Boolean = false,
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val autoConnect by vm.autoConnect.collectAsStateWithLifecycle()
    val log by BleLogger.lines.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val scroll = rememberScrollState()
    val context = LocalContext.current

    fun bluetoothPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }.toTypedArray()

    fun missingBluetoothPermissions(): Array<String> =
        bluetoothPermissions()
            .filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()

    fun isBluetoothOn(): Boolean {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        return adapter?.state == BluetoothAdapter.STATE_ON
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || isBluetoothOn()) {
            BleLogger.i("Bluetooth enabled; starting scan")
            vm.startScan()
        } else {
            BleLogger.w("Bluetooth enable cancelled")
            vm.setStatus("Bluetooth required to connect")
        }
    }

    fun ensureBluetoothThenScan() {
        if (isBluetoothOn()) {
            vm.startScan()
            return
        }
        BleLogger.i("Bluetooth off; requesting enable")
        vm.setStatus("Turning on Bluetooth…")
        try {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (t: Throwable) {
            BleLogger.e("Cannot request Bluetooth enable", t)
            vm.setStatus("Enable Bluetooth in system settings")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val ok = result.isNotEmpty() && result.values.all { it }
        BleLogger.i("Permissions granted=$ok $result")
        if (ok) {
            ensureBluetoothThenScan()
        } else {
            vm.setStatus("Bluetooth permission denied")
        }
    }

    fun ensureScan() {
        val missing = missingBluetoothPermissions()
        if (missing.isEmpty()) {
            ensureBluetoothThenScan()
        } else {
            BleLogger.i("Requesting BT permissions: ${missing.joinToString()}")
            vm.setStatus("Allow Bluetooth access…")
            permissionLauncher.launch(missing)
        }
    }

    LaunchedEffect(autoConnect) {
        if (autoConnect && vm.takeAutoConnectSlot()) {
            BleLogger.i("Auto-connect on launch")
            ensureScan()
        }
    }

    val displayMeasurement = state.measurement ?: state.lastMeasurement
    val sessionHealth = remember(state.sessionBodyComp) { healthSummary(state.sessionBodyComp) }
    val bg = Brush.verticalGradient(
        colors = listOf(
            scheme.background,
            scheme.surfaceVariant.copy(alpha = 0.45f),
            scheme.background,
        ),
    )

    state.pendingWeightOnly?.let { pending ->
        WeightOnlyConfirmDialog(
            weightKg = pending.weight,
            onConfirm = vm::confirmWeightOnly,
            onDiscard = vm::discardWeightOnly,
        )
    }

    val softEnter = if (reduceAnimations) {
        EnterTransition.None
    } else {
        fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 3 }
    }
    val softExit = if (reduceAnimations) {
        ExitTransition.None
    } else {
        fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { it / 3 }
    }
    val detailEnter = if (reduceAnimations) {
        EnterTransition.None
    } else {
        fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.slowSpatialSpec()) { it / 4 }
    }
    val detailExit = if (reduceAnimations) ExitTransition.None else fadeOut(motion.fastEffectsSpec())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "FreeScale",
            style = MaterialTheme.typography.displayMedium,
            color = scheme.onBackground,
        )

        InstrumentReadout(
            state = state,
            scroll = scroll,
            reduceAnimations = reduceAnimations,
            readingAnimationChoice = readingAnimationChoice,
            forceShowLoadingAnimations = forceShowLoadingAnimations,
        )

        AnimatedVisibility(
            visible = sessionHealth != null,
            enter = softEnter,
            exit = softExit,
        ) {
            HealthStatusBar(health = sessionHealth, reduceAnimations = reduceAnimations)
        }

        AnimatedVisibility(
            visible = state.sessionBodyComp != null,
            enter = softEnter,
            exit = softExit,
        ) {
            state.sessionBodyComp?.let { body ->
                MetricRail(
                    fat = body.fat,
                    muscle = body.muscle,
                    bmr = body.bmr,
                )
            }
        }

        if (state.connected) {
            OutlinedButton(
                onClick = { vm.disconnect() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Disconnect", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Button(
                onClick = { if (state.scanning) vm.stopScan() else ensureScan() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.scanning) "Stop" else "Connect",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (debugMode) {
            TextButton(onClick = { vm.openDeveloperSettings() }) {
                Text("Open Dev Settings (HCI snoop)")
            }
        }

        AnimatedVisibility(
            visible = displayMeasurement != null,
            enter = detailEnter,
            exit = detailExit,
        ) {
            displayMeasurement?.let { MeasurementDetail(it, debugMode) }
        }

        val connectedDevice = state.connectedDevice
        if (state.connected && connectedDevice != null) {
            HorizontalDivider()
            Text("Devices", style = MaterialTheme.typography.titleMedium)
            ConnectedDeviceRow(
                device = connectedDevice,
                weighing = state.measurePhase == MeasurePhase.Armed ||
                    state.measurePhase == MeasurePhase.Weighing ||
                    state.measurePhase == MeasurePhase.WeightStable ||
                    state.measurePhase == MeasurePhase.MeasuringBia,
                reduceAnimations = reduceAnimations,
            )
        }

        if (debugMode) {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "BLE log",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = { vm.clearLog() }) { Text("Clear") }
            }
            Text(
                text = log.takeLast(60).joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth(),
                softWrap = true,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

private val ConnectedGreen = Color(0xFF22C55E)

@Composable
private fun ConnectedDeviceRow(
    device: ScannedScale,
    weighing: Boolean,
    reduceAnimations: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val infinite = rememberInfiniteTransition(label = "linkBlink")
    val pulse by infinite.animateFloat(
        initialValue = 0.22f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "linkAlpha",
    )
    val blinkAlpha = if (weighing && !reduceAnimations) pulse else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(ConnectedGreen.copy(alpha = blinkAlpha)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                device.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                device.address,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HealthStatusBar(health: HealthSummary?, reduceAnimations: Boolean) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = health,
        transitionSpec = {
            if (reduceAnimations) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(motion.defaultEffectsSpec()) +
                    slideInVertically(motion.defaultSpatialSpec()) { -it / 3 }) togetherWith
                    (fadeOut(motion.fastEffectsSpec()) +
                        slideOutVertically(motion.fastSpatialSpec()) { it / 3 })
            }
        },
        label = "healthBar",
    ) { summary ->
        if (summary == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.medium,
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline),
                )
                Column {
                    Text(
                        "No health snapshot yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Complete a BIA weigh-in for body score",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val accent = summary.tone.indicatorColor()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = summary.tone.softContainer(),
                        shape = MaterialTheme.shapes.medium,
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        summary.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        summary.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        summary.scoreLabel,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = PlexMonoFamily,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = accent,
                    )
                    Text(
                        summary.metricName,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun InstrumentReadout(
    state: UiState,
    scroll: ScrollState,
    reduceAnimations: Boolean,
    readingAnimationChoice: String,
    forceShowLoadingAnimations: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val live = state.liveWeightKg
    val saved = state.lastMeasurement
    val weightKg = live ?: saved?.takeIf { it.weight > 0f }?.weight
    val hasWeight = weightKg != null
    val phase = state.measurePhase

    val animatedWeight by animateFloatAsState(
        targetValue = weightKg ?: 0f,
        animationSpec = if (reduceAnimations) snap() else motion.defaultSpatialSpec(),
        label = "weightReadout",
    )

    val panelScale by animateFloatAsState(
        targetValue = if (reduceAnimations) {
            1f
        } else {
            when (phase) {
                MeasurePhase.Weighing -> 1.02f
                MeasurePhase.WeightStable -> 1.03f
                MeasurePhase.MeasuringBia -> 1.04f
                else -> 1f
            }
        },
        animationSpec = if (reduceAnimations) snap() else motion.slowSpatialSpec(),
        label = "panelScale",
    )

    // Scroll-driven parallax only (no continuous sensors; those were janking the UI).
    val scrollPx = scroll.value.toFloat()
    val parallaxBgY = if (reduceAnimations) 0f else scrollPx * 0.28f
    val parallaxFgY = if (reduceAnimations) 0f else scrollPx * 0.10f

    val headline = phaseHeadline(phase, live, saved)
    val status = state.status
    val measuringActive = phase == MeasurePhase.Weighing ||
        phase == MeasurePhase.WeightStable ||
        phase == MeasurePhase.MeasuringBia
    val showMeasuringBanner =
        (measuringActive || forceShowLoadingAnimations) && !reduceAnimations
    val contentSwap = if (reduceAnimations) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        (fadeIn(motion.defaultEffectsSpec()) +
            scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f)) togetherWith
            (fadeOut(motion.fastEffectsSpec()) +
                scaleOut(motion.fastSpatialSpec(), targetScale = 0.92f))
    }
    val captionSwap = if (reduceAnimations) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        (fadeIn(motion.defaultEffectsSpec()) +
            slideInVertically(motion.defaultSpatialSpec()) { it / 2 }) togetherWith
            (fadeOut(motion.fastEffectsSpec()) +
                slideOutVertically(motion.fastSpatialSpec()) { -it / 2 })
    }
    val barEnter = if (reduceAnimations) EnterTransition.None else fadeIn(motion.defaultEffectsSpec())
    val barExit = if (reduceAnimations) ExitTransition.None else fadeOut(motion.fastEffectsSpec())
    val bannerEnter = if (reduceAnimations) EnterTransition.None else {
        fadeIn(motion.defaultEffectsSpec())
    }
    val bannerExit = if (reduceAnimations) ExitTransition.None else {
        fadeOut(motion.fastEffectsSpec())
    }

    val activeAnimationChoice = remember(showMeasuringBanner, readingAnimationChoice) {
        if (!showMeasuringBanner) {
            readingAnimationChoice
        } else {
            LoadingAnimationRegistry.resolve(
                LoadingAnimationSlot.READING,
                readingAnimationChoice,
            )?.id ?: LoadingAnimChoice.OFF
        }
    }
    val lightAnimContent = remember(activeAnimationChoice, showMeasuringBanner) {
        if (!showMeasuringBanner) {
            true
        } else when (activeAnimationChoice) {
            LoadingAnimChoice.OFF -> false // theme surface under the spinner
            else -> LoadingAnimationRegistry.byId(activeAnimationChoice)?.lightContent != false
        }
    }
    val usingThemeSurface = !showMeasuringBanner || activeAnimationChoice == LoadingAnimChoice.OFF
    var touchSpeedMultiplier by remember { mutableFloatStateOf(1f) }
    // Reset boost when the measuring card dismisses.
    LaunchedEffect(showMeasuringBanner) {
        if (!showMeasuringBanner) touchSpeedMultiplier = 1f
    }
    val readoutInk = when {
        usingThemeSurface -> scheme.onPrimaryContainer
        lightAnimContent -> Color.White
        else -> TirangaChakraNavy
    }
    val headlineAccent = when {
        usingThemeSurface -> when (phase) {
            MeasurePhase.MeasuringBia, MeasurePhase.WeightStable -> scheme.tertiary
            else -> scheme.primary
        }
        lightAnimContent -> when (phase) {
            MeasurePhase.MeasuringBia, MeasurePhase.WeightStable -> Color(0xFF80CBC4)
            else -> Color(0xFFFFCC80)
        }
        else -> TirangaChakraNavy
    }
    val tickAccent = when {
        usingThemeSurface -> when (phase) {
            MeasurePhase.WeightStable, MeasurePhase.MeasuringBia -> scheme.tertiary
            MeasurePhase.Weighing -> scheme.primary.copy(alpha = 0.75f)
            else -> scheme.primary.copy(alpha = 0.55f)
        }
        lightAnimContent -> when (phase) {
            MeasurePhase.WeightStable, MeasurePhase.MeasuringBia -> Color(0xFF80CBC4)
            else -> Color.White.copy(alpha = 0.55f)
        }
        else -> TirangaChakraNavy.copy(alpha = 0.55f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .graphicsLayer {
                scaleX = panelScale
                scaleY = panelScale
            }
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                brush = Brush.verticalGradient(
                    listOf(scheme.primaryContainer, scheme.surfaceContainerHighest),
                ),
            ),
    ) {
        AnimatedVisibility(
            visible = showMeasuringBanner,
            enter = bannerEnter,
            exit = bannerExit,
            modifier = Modifier.fillMaxSize(),
        ) {
            LoadingAnimationHost(
                slot = LoadingAnimationSlot.READING,
                animationChoice = activeAnimationChoice,
                modifier = Modifier.fillMaxSize(),
                label = if (forceShowLoadingAnimations && !measuringActive) {
                    "PREVIEW"
                } else {
                    headline
                },
                captions = if (forceShowLoadingAnimations && !measuringActive) {
                    listOf(
                        "developer preview…",
                        "force-show enabled…",
                        "weigh in to see live…",
                    )
                } else {
                    measuringBannerCaptions(phase)
                },
                speedMultiplier = touchSpeedMultiplier,
            )
        }

        if (!showMeasuringBanner) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = -parallaxBgY
                        scaleX = 1.08f
                        scaleY = 1.08f
                    },
            ) {
                PhaseWaveform(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 40.dp),
                    phase = phase,
                    color = when (phase) {
                        MeasurePhase.MeasuringBia -> scheme.tertiary
                        else -> scheme.primary
                    },
                    reduceAnimations = reduceAnimations,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -parallaxFgY }
                .padding(24.dp)
                .padding(top = 8.dp),
            verticalArrangement = if (showMeasuringBanner) {
                Arrangement.Top
            } else {
                Arrangement.SpaceBetween
            },
        ) {
            AnimatedContent(
                targetState = if (showMeasuringBanner) "CRAFTED IN INDIA" else headline,
                transitionSpec = { contentSwap },
                label = "phaseHeadline",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = headlineAccent,
                    letterSpacing = 2.sp,
                )
            }

            if (showMeasuringBanner) {
                Spacer(modifier = Modifier.height(14.dp))
            }

            MeasurementModeStrip(
                phase = phase,
                liveWeightKg = live,
                reduceAnimations = reduceAnimations,
                contentInk = when {
                    usingThemeSurface -> null
                    lightAnimContent -> Color.White
                    else -> TirangaChakraNavy
                },
            )

            if (showMeasuringBanner) {
                Spacer(modifier = Modifier.height(28.dp))
            }

            Column {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (hasWeight) {
                            "%.1f".format(Locale.US, animatedWeight)
                        } else {
                            "- / -"
                        },
                        style = ReadoutStyle.copy(
                            color = readoutInk,
                            fontSize = if (hasWeight) 64.sp else 56.sp,
                        ),
                    )
                    Text(
                        text = "kg",
                        style = ReadoutUnitStyle.copy(
                            color = readoutInk.copy(alpha = 0.7f),
                        ),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                TickMarks(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    accent = tickAccent,
                )

                AnimatedVisibility(
                    visible = !reduceAnimations &&
                        (phase == MeasurePhase.MeasuringBia || phase == MeasurePhase.WeightStable),
                    enter = barEnter,
                    exit = barExit,
                ) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(10.dp),
                        amplitude = if (phase == MeasurePhase.MeasuringBia) 1f else 0.35f,
                        wavelength = if (phase == MeasurePhase.MeasuringBia) 28.dp else 40.dp,
                        color = if (usingThemeSurface) {
                            if (phase == MeasurePhase.MeasuringBia) {
                                scheme.tertiary
                            } else {
                                scheme.primary
                            }
                        } else if (lightAnimContent) {
                            if (phase == MeasurePhase.MeasuringBia) {
                                Color(0xFF80CBC4)
                            } else {
                                Color(0xFFFFCC80)
                            }
                        } else if (phase == MeasurePhase.MeasuringBia) {
                            TirangaChakraNavy
                        } else {
                            TirangaChakraNavy.copy(alpha = 0.75f)
                        },
                    )
                }
            }

            if (showMeasuringBanner) {
                // Reserve the bottom caption shelf drawn by the animation.
                Spacer(modifier = Modifier.weight(1f))
            } else {
                AnimatedContent(
                    targetState = status,
                    transitionSpec = { captionSwap },
                    label = "statusCaption",
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Press-and-hold only: sits above chrome so the animation receives the hold.
        if (showMeasuringBanner && activeAnimationChoice != LoadingAnimChoice.OFF) {
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
    }
}

private fun measuringBannerCaptions(phase: MeasurePhase): List<String> = when (phase) {
    MeasurePhase.Weighing -> listOf(
        "sampling the load cells…",
        "steadying the weight…",
        "locking the readout…",
    )
    MeasurePhase.WeightStable -> listOf(
        "weight locked…",
        "waiting for handlebars…",
        "ready for BIA…",
    )
    MeasurePhase.MeasuringBia -> listOf(
        "reading impedance…",
        "mapping body comp…",
        "crunching the numbers…",
    )
    else -> emptyList()
}

/** Chip container: yellow standby vs green engaged. */
private enum class ModeBoxState { Standby, Active }

/**
 * Status dot independent of the box:
 * - [Idle] yellow solid (not started)
 * - [Pending] yellow blinking (in progress)
 * - [Done] green solid (locked / data received)
 */
private enum class ModeDotState { Idle, Pending, Done }

private val ModeStandbyYellow = Color(0xFFEAB308)
private val ModeActiveGreen = Color(0xFF22C55E)

@Composable
private fun MeasurementModeStrip(
    phase: MeasurePhase,
    liveWeightKg: Float?,
    reduceAnimations: Boolean,
    contentInk: Color? = null,
) {
    val connectedReady = phase == MeasurePhase.Ready ||
        phase == MeasurePhase.Armed ||
        phase == MeasurePhase.Weighing ||
        phase == MeasurePhase.WeightStable ||
        phase == MeasurePhase.MeasuringBia
    val steppedOn = (liveWeightKg ?: 0f) > 10f
    val weightLocked = phase == MeasurePhase.WeightStable ||
        phase == MeasurePhase.MeasuringBia

    AnimatedVisibility(
        visible = connectedReady,
        enter = if (reduceAnimations) EnterTransition.None else fadeIn() + slideInVertically { -it / 2 },
        exit = if (reduceAnimations) ExitTransition.None else fadeOut() + slideOutVertically { -it / 2 },
    ) {
        val weightBox = if (steppedOn) ModeBoxState.Active else ModeBoxState.Standby
        val weightDot = when {
            weightLocked -> ModeDotState.Done
            steppedOn -> ModeDotState.Pending
            else -> ModeDotState.Idle
        }
        // No realtime grip bit — box goes green at weight lock; dot waits for BIA packets.
        val biaBox = if (weightLocked) ModeBoxState.Active else ModeBoxState.Standby
        val biaDot = when (phase) {
            MeasurePhase.MeasuringBia -> ModeDotState.Done
            MeasurePhase.WeightStable -> ModeDotState.Pending
            else -> ModeDotState.Idle
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeChip(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.MonitorWeight,
                label = "Weight",
                detail = when {
                    !steppedOn -> "Step on"
                    weightLocked -> "Locked"
                    else -> "Measuring"
                },
                boxState = weightBox,
                dotState = weightDot,
                reduceAnimations = reduceAnimations,
                contentInk = contentInk,
            )
            ModeChip(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PanTool,
                label = "Handlebars",
                detail = when {
                    phase == MeasurePhase.MeasuringBia -> "BIA reading"
                    phase == MeasurePhase.WeightStable -> "Grab bars"
                    steppedOn -> "Hold for BIA"
                    else -> "Stand by"
                },
                boxState = biaBox,
                dotState = biaDot,
                reduceAnimations = reduceAnimations,
                contentInk = contentInk,
            )
        }
    }
}

@Composable
private fun ModeChip(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    detail: String,
    boxState: ModeBoxState,
    dotState: ModeDotState,
    reduceAnimations: Boolean,
    contentInk: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val boxAccent = when (boxState) {
        ModeBoxState.Active -> ModeActiveGreen
        ModeBoxState.Standby -> ModeStandbyYellow
    }
    val dotColor = when (dotState) {
        ModeDotState.Done -> ModeActiveGreen
        ModeDotState.Idle, ModeDotState.Pending -> ModeStandbyYellow
    }
    val infinite = rememberInfiniteTransition(label = "modeDotPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "modeDotAlpha",
    )
    val glow = if (dotState == ModeDotState.Pending && !reduceAnimations) pulse else 1f

    val container = boxAccent.copy(alpha = if (boxState == ModeBoxState.Active) 0.28f else 0.18f)
    val content = (contentInk ?: scheme.onPrimaryContainer).copy(
        alpha = if (boxState == ModeBoxState.Active) 1f else 0.88f,
    )

    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = glow)),
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun phaseHeadline(
    phase: MeasurePhase,
    live: Float?,
    saved: com.anant.freescale.data.ScaleMeasurement?,
): String = when (phase) {
    MeasurePhase.Armed -> "ARMED"
    MeasurePhase.Weighing -> "WEIGH-IN"
    MeasurePhase.WeightStable -> "WEIGHT LOCKED"
    MeasurePhase.MeasuringBia -> "BIA ACTIVE"
    MeasurePhase.Complete -> "COMPLETE"
    MeasurePhase.Ready -> "READY"
    MeasurePhase.Idle -> when {
        live != null -> "LIVE"
        saved != null && saved.weight > 0f -> "LAST READING"
        else -> "READY"
    }
}

@Composable
private fun MetricRail(fat: Float, muscle: Float, bmr: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetricCell("Fat", "%.1f%%".format(Locale.US, fat))
        RailDivider()
        MetricCell("Muscle", "%.1f%%".format(Locale.US, muscle))
        RailDivider()
        MetricCell("BMR", "%.0f kcal".format(Locale.US, bmr))
    }
}

@Composable
private fun MetricCell(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MetricValueStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RailDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun TickMarks(modifier: Modifier = Modifier, accent: Color) {
    Canvas(modifier = modifier.height(12.dp)) {
        val count = 25
        val step = size.width / (count - 1)
        for (i in 0 until count) {
            val x = i * step
            val tall = i % 5 == 0
            val h = if (tall) size.height else size.height * 0.45f
            drawLine(
                color = if (tall) accent else accent.copy(alpha = 0.35f),
                start = Offset(x, size.height - h),
                end = Offset(x, size.height),
                strokeWidth = if (tall) 2f else 1.2f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun PhaseWaveform(
    modifier: Modifier = Modifier,
    phase: MeasurePhase,
    color: Color,
    reduceAnimations: Boolean,
) {
    val active = phase == MeasurePhase.Weighing ||
        phase == MeasurePhase.WeightStable ||
        phase == MeasurePhase.MeasuringBia ||
        phase == MeasurePhase.Armed

    // Idle or reduced: static wave (no infinite animation).
    if (!active || reduceAnimations) {
        Canvas(modifier = modifier) {
            drawStaticWave(
                color = color.copy(alpha = if (active) 0.22f else 0.14f),
                ampFactor = if (active) 0.35f else 0.15f,
                cycles = 2.5,
                stroke = 2.2f,
                shift = 0f,
            )
        }
        return
    }

    val infinite = rememberInfiniteTransition(label = "wave")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (phase) {
                    MeasurePhase.MeasuringBia -> 900
                    MeasurePhase.Weighing -> 1600
                    MeasurePhase.WeightStable -> 2200
                    else -> 2800
                },
                easing = androidx.compose.animation.core.LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "waveDrift",
    )

    val ampFactor = when (phase) {
        MeasurePhase.MeasuringBia -> 1f
        MeasurePhase.Weighing -> 0.55f
        MeasurePhase.WeightStable -> 0.35f
        MeasurePhase.Armed -> 0.25f
        else -> 0.15f
    }
    val cycles = when (phase) {
        MeasurePhase.MeasuringBia -> 5.0
        MeasurePhase.Weighing -> 3.0
        else -> 2.5
    }
    val stroke = when (phase) {
        MeasurePhase.MeasuringBia -> 3.2f
        MeasurePhase.Weighing -> 2.6f
        else -> 2.2f
    }

    Canvas(modifier = modifier) {
        drawStaticWave(
            color = color.copy(alpha = 0.28f),
            ampFactor = ampFactor,
            cycles = cycles,
            stroke = stroke,
            shift = drift,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStaticWave(
    color: Color,
    ampFactor: Float,
    cycles: Double,
    stroke: Float,
    shift: Float,
) {
    val path = Path()
    val midY = size.height * 0.55f
    val amp = size.height * 0.12f * ampFactor
    path.moveTo(0f, midY)
    var x = 0f
    while (x <= size.width) {
        val y = midY +
            kotlin.math.sin((x / size.width) * Math.PI * cycles + shift).toFloat() * amp
        path.lineTo(x, y)
        x += 5f
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}
