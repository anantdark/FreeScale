package com.anant.freescale.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Reading",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            MeasurementDetail(m = measurement, debugMode = debugMode)
        }
    }
}
