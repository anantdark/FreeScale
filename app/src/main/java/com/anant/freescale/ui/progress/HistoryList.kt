package com.anant.freescale.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anant.freescale.bridge.FitBuddyBridge
import com.anant.freescale.bridge.ScaleBridgeJson
import com.anant.freescale.data.ScaleMeasurement
import com.anant.freescale.ui.MeasurementDetail
import com.anant.freescale.ui.theme.PlexMonoFamily
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HistoryRow(
    measurement: ScaleMeasurement,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val whenStr = measurement.dateTime?.let {
        SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault()).format(it)
    } ?: "—"

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(whenStr, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (measurement.hasBodyComp) {
                        "Body fat ${String.format(Locale.US, "%.1f", measurement.fat)}%"
                    } else {
                        "Weight only"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${String.format(Locale.US, "%.1f", measurement.weight)} kg",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = PlexMonoFamily,
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDetailSheet(
    measurement: ScaleMeasurement,
    debugMode: Boolean,
    onDismiss: () -> Unit,
    shareEnabled: Boolean = false,
    shareBusy: Boolean = false,
    shareMessage: String? = null,
    shareMessageIsError: Boolean = false,
    onShareToFitBuddy: (() -> Unit)? = null,
    onDismissShareMessage: () -> Unit = {},
    onFitBuddyAvailabilityChanged: (Boolean) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxScrollHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showConfirm by remember { mutableStateOf(false) }
    var showInstallPrompt by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxScrollHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Reading",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                MeasurementDetail(m = measurement, debugMode = debugMode)
            }

            if (onShareToFitBuddy != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Button(
                    onClick = {
                        onDismissShareMessage()
                        val installed = FitBuddyBridge.isAvailable(context)
                        onFitBuddyAvailabilityChanged(installed)
                        if (installed) {
                            showConfirm = true
                        } else {
                            showInstallPrompt = true
                        }
                    },
                    enabled = !shareBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (shareBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Sharing…")
                    } else {
                        Text("Share to FitBuddy")
                    }
                }
            }
        }
    }

    if (showInstallPrompt) {
        FitBuddyNotInstalledDialog(
            onDismiss = { showInstallPrompt = false },
            onOpenWebsite = {
                showInstallPrompt = false
                uriHandler.openUri(FITBUDDY_WEBSITE_URL)
            },
        )
    }

    if (showConfirm && onShareToFitBuddy != null) {
        ShareToFitBuddyConfirmDialog(
            measurement = measurement,
            onDismiss = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                onShareToFitBuddy()
            },
        )
    }

    if (shareMessage != null && !shareBusy) {
        if (shareMessageIsError) {
            AlertDialog(
                onDismissRequest = onDismissShareMessage,
                title = { Text("Couldn't share to FitBuddy") },
                text = { Text(shareMessage) },
                confirmButton = {
                    if (!shareEnabled) {
                        TextButton(
                            onClick = {
                                onDismissShareMessage()
                                uriHandler.openUri(FITBUDDY_WEBSITE_URL)
                            },
                        ) {
                            Text("Get FitBuddy")
                        }
                    } else {
                        TextButton(
                            onClick = {
                                onDismissShareMessage()
                                showConfirm = true
                            },
                        ) {
                            Text("Try again")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissShareMessage) {
                        Text("Close")
                    }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = onDismissShareMessage,
                title = { Text("Shared to FitBuddy") },
                text = {
                    Text(
                        shareMessage.ifBlank {
                            "This reading was saved as a body measurement in FitBuddy."
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismissShareMessage) {
                        Text("OK")
                    }
                },
            )
        }
    }
}

private const val FITBUDDY_WEBSITE_URL = "https://github.com/anantdark/FitBuddy"

@Composable
private fun FitBuddyNotInstalledDialog(
    onDismiss: () -> Unit,
    onOpenWebsite: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("FitBuddy not installed") },
        text = {
            Text(
                "Install FitBuddy to save this reading as a body measurement there. " +
                    "Open the FitBuddy page to download it, then come back and share again.",
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenWebsite) {
                Text("Get FitBuddy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        },
    )
}

@Composable
private fun ShareToFitBuddyConfirmDialog(
    measurement: ScaleMeasurement,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val preview = remember(measurement) { ScaleBridgeJson.previewRows(measurement) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share to FitBuddy?") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "FitBuddy will save the body-comp fields you see here, plus a hidden " +
                        "full FreeScale dump (Ω / BLE / segments) for backup restore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                preview.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = PlexMonoFamily,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
