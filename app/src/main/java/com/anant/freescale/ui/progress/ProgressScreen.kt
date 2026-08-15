package com.anant.freescale.ui.progress

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anant.freescale.MeasureViewModel
import com.anant.freescale.data.ScaleMeasurement

@Composable
fun ProgressScreen(vm: MeasureViewModel) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val periodReadings by vm.periodReadings.collectAsStateWithLifecycle()
    val history by vm.measurementHistory.collectAsStateWithLifecycle()
    val count by vm.measurementCount.collectAsStateWithLifecycle()
    val debugMode by vm.debugMode.collectAsStateWithLifecycle()
    val fitBuddyState by vm.fitBuddyState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.refreshFitBuddyAvailability()
    }

    val period = progress.period
    val series = remember(periodReadings, progress.metric, period.start, period.endExclusive) {
        val points = buildReadingSeries(periodReadings, progress.metric)
        buildChartSeries(points, progress.metric)
    }

    var selected by remember { mutableStateOf<ScaleMeasurement?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Progress", style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (count == 0) {
                        "No readings saved yet"
                    } else {
                        "$count reading${if (count == 1) "" else "s"} on this phone"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "period") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = period.unit == PeriodUnit.Week,
                    onClick = { vm.setProgressPeriodUnit(PeriodUnit.Week) },
                    label = { Text("Week") },
                )
                FilterChip(
                    selected = period.unit == PeriodUnit.Month,
                    onClick = { vm.setProgressPeriodUnit(PeriodUnit.Month) },
                    label = { Text("Month") },
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = vm::goToPreviousProgressPeriod) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous ${period.unit.name.lowercase()}",
                    )
                }
                Text(
                    period.label(),
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(
                    onClick = vm::goToNextProgressPeriod,
                    enabled = period.canGoNext(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next ${period.unit.name.lowercase()}",
                    )
                }
            }
        }

        item(key = "metrics") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProgressMetric.entries.forEach { m ->
                    FilterChip(
                        selected = progress.metric == m,
                        onClick = { vm.setProgressMetric(m) },
                        label = { Text(m.label) },
                    )
                }
            }
        }

        item(key = "chart") {
            TrendChartCard(series = series, period = period)
        }

        item(key = "history-header") {
            Text("History", style = MaterialTheme.typography.titleLarge)
        }

        if (history.isEmpty()) {
            item(key = "history-empty") {
                Text(
                    "Saved readings will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(
                items = history,
                key = { m -> "${m.dateTime?.time ?: 0}-${m.weight}-${"%.3f".format(m.fat)}" },
            ) { m ->
                HistoryRow(
                    measurement = m,
                    onClick = { selected = m },
                )
            }
        }
    }

    selected?.let { m ->
        MeasurementDetailSheet(
            measurement = m,
            debugMode = debugMode,
            onDismiss = {
                vm.dismissFitBuddyMessage()
                selected = null
            },
            shareEnabled = fitBuddyState.available,
            shareBusy = fitBuddyState.busy,
            shareMessage = fitBuddyState.message,
            shareMessageIsError = fitBuddyState.isError,
            onShareToFitBuddy = {
                vm.refreshFitBuddyAvailability()
                vm.shareToFitBuddy(m)
            },
            onDismissShareMessage = vm::dismissFitBuddyMessage,
            onFitBuddyAvailabilityChanged = vm::setFitBuddyAvailable,
        )
    }
}
