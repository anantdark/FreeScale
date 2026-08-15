package com.anant.freescale.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anant.freescale.data.ScaleMeasurement
import com.anant.freescale.ui.theme.PlexMonoFamily
import java.text.SimpleDateFormat
import java.util.Locale

private val TrendGreen = Color(0xFF22C55E)
private val TrendRed = Color(0xFFEF4444)

@Composable
fun MeasurementDetail(
    m: ScaleMeasurement,
    debugMode: Boolean,
    previous: ScaleMeasurement? = null,
) {
    val whenStr = m.dateTime?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it)
    } ?: "-"

    // Must be a single Column: AnimatedVisibility stacks multiple children like a Box.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Results", style = MaterialTheme.typography.titleLarge)

        SectionTitle("Profile used")
        MetricRow("Time", whenStr)
        MetricRow("Sex", m.genderLabel.ifBlank { "-" })
        MetricRow("Age", "${m.ageYears} y")
        MetricRow("Height", "${"%.0f".format(m.heightCm)} cm")
        if (debugMode) {
            MetricRow("Algorithm", m.algorithm.ifBlank { "-" })
        }

        SectionTitle("Weight & BMI")
        MetricRow(
            "Weight",
            fmtKg(m.weight),
            change = previous?.let {
                metricChange(m.weight, it.weight, MetricHigherIs.Worse)
            },
        )
        if (!m.hasBodyComp) {
            Text(
                "(weight only, no BIA this session)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        } else {
            MetricRow(
                "BMI",
                "%.2f".format(m.bmi),
                change = previous?.takeIf { it.hasBodyComp }?.let {
                    metricChange(m.bmi, it.bmi, MetricHigherIs.Worse)
                },
            )
            MetricRow("Ideal weight (Broca)", fmtKg(m.idealWeightKg))
            MetricRow(
                "Obesity degree",
                "${"%.1f".format(m.obesityDegree)}%",
                change = previous?.takeIf { it.hasBodyComp }?.let {
                    metricChange(m.obesityDegree, it.obesityDegree, MetricHigherIs.Worse)
                },
            )

            SectionTitle("Fat")
            MetricRow(
                "Body fat",
                "${"%.2f".format(m.fat)}%  ·  ${fmtKg(m.fatMassKg)}",
                change = previous?.takeIf { it.hasBodyComp }?.let {
                    metricChange(m.fat, it.fat, MetricHigherIs.Worse)
                },
            )
            MetricRow(
                "Subcutaneous fat",
                "${"%.2f".format(m.subcutaneousFat)}%",
                change = previous?.takeIf { it.hasBodyComp }?.let {
                    metricChange(m.subcutaneousFat, it.subcutaneousFat, MetricHigherIs.Worse)
                },
            )
            MetricRow(
                "Visceral fat",
                "${"%.2f".format(m.visceralFat)}%",
                change = previous?.takeIf { it.hasBodyComp }?.let {
                    metricChange(m.visceralFat, it.visceralFat, MetricHigherIs.Worse)
                },
            )

            SectionTitle("Lean mass & water")
            MetricRow(
                "Muscle",
                "${"%.2f".format(m.muscle)}%  ·  ${fmtKg(m.muscleMassKg)}",
                change = previous?.takeIf { it.hasBodyComp }?.let {
                    metricChange(m.muscleMassKg, it.muscleMassKg, MetricHigherIs.Better)
                },
            )
            MetricRow(
                "Skeletal muscle",
                "${"%.2f".format(m.skeletalMuscle)}%",
                change = previous?.takeIf { it.hasBodyComp }?.let {
                    metricChange(m.skeletalMuscle, it.skeletalMuscle, MetricHigherIs.Better)
                },
            )
            MetricRow(
                "Fat-free mass (LBM)",
                fmtKg(m.lbm),
                change = previous?.takeIf { it.hasBodyComp }?.let {
                    metricChange(m.lbm, it.lbm, MetricHigherIs.Better)
                },
            )
            MetricRow(
                "Bone mass",
                fmtKg(m.bone),
                change = previous?.takeIf { it.hasBodyComp }?.let {
                    metricChange(m.bone, it.bone, MetricHigherIs.Better)
                },
            )
            MetricRow(
                "Protein",
                "${"%.2f".format(m.protein)}%  ·  ${fmtKg(m.proteinKg)}",
                change = previous?.takeIf { it.hasBodyComp }?.let {
                    metricChange(m.proteinKg, it.proteinKg, MetricHigherIs.Better)
                },
            )
            MetricRow(
                "Body water",
                "${"%.2f".format(m.water)}%  ·  ${fmtKg(m.waterKg)}",
                change = previous?.takeIf { it.hasBodyComp }?.let {
                    metricChange(m.water, it.water, MetricHigherIs.Better)
                },
            )

            SectionTitle("Metabolism")
            MetricRow(
                "BMR",
                "${"%.0f".format(m.bmr)} kcal/day",
                change = previous?.takeIf { it.hasBodyComp }?.let {
                    metricChange(m.bmr, it.bmr, MetricHigherIs.Better)
                },
            )
            MetricRow(
                "Metabolic age",
                "${m.bodyAge} y",
                change = previous?.takeIf { it.hasBodyComp && it.bodyAge > 0 && m.bodyAge > 0 }?.let {
                    metricChange(m.bodyAge.toFloat(), it.bodyAge.toFloat(), MetricHigherIs.Worse)
                },
            )
            MetricRow(
                "Body score",
                "${"%.0f".format(m.bodyScore)} / 100",
                change = previous?.takeIf { it.hasBodyComp && it.bodyScore > 0f && m.bodyScore > 0f }?.let {
                    metricChange(m.bodyScore, it.bodyScore, MetricHigherIs.Better)
                },
            )

            if (m.segments.isNotEmpty()) {
                SectionTitle("Segmental composition")
                m.segments.forEach { s ->
                    MetricRow(
                        s.name,
                        "fat ${"%.2f".format(s.fatKg)} kg (${"%.1f".format(s.fatPct)}%)  ·  " +
                            "mus ${"%.2f".format(s.muscleKg)} kg (${"%.1f".format(s.musclePct)}%)",
                    )
                }
            }

            if (debugMode) {
                SectionTitle("Impedance (raw from scale)")
                MetricRow("Foot-path Z (Z3+Z4+Z5)", "${"%.2f".format(m.impedance)} Ω")
                MetricRow("H²/R coefficient", "%.4f".format(m.h2rCoeff))
                MetricRow("pkt0 channel A", "${"%.2f".format(m.channelAOhm)} Ω")
                MetricRow("pkt0 channel B", "${"%.2f".format(m.channelBOhm)} Ω")
                MetricRow("pkt0 cmd", "0x${m.pkt0Cmd.toString(16)}  valid=${m.pkt0ValidFlag}")

                SectionTitle("8-electrode segments (pkt1)")
                m.segmentLabeled().forEach { (label, ohm) ->
                    MetricRow(label, "${"%.2f".format(ohm)} Ω")
                }

                if (m.wla25Inputs.size == 10) {
                    SectionTitle("WLA25 input vector (10 Ω)")
                    val names = listOf(
                        "i0 Z3 trunk", "i1 Z1", "i2 Z2", "i3 Z4 R-leg", "i4 Z5 L-leg",
                        "i5 Z8", "i6 chB", "i7 chA", "i8 Z6", "i9 Z7",
                    )
                    m.wla25Inputs.forEachIndexed { i, v ->
                        MetricRow(names.getOrElse(i) { "i$i" }, "${"%.2f".format(v)} Ω")
                    }
                }

                SectionTitle("BLE frames (hex)")
                HexBlock("FFB3 pkt0 (weight + channels)", m.pkt0Hex)
                HexBlock("FFB3 pkt1 (segmental Z)", m.pkt1Hex)
                HexBlock("FFB3 pkt2 (end)", m.pkt2Hex)

                Text(
                    "Primary algorithm: Chipsea/ICOMON WLA25 (same family as Fitdays / FG2211), " +
                        "ported from sacoma-lib. Vendor app may still differ slightly if it uses a newer .so revision.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    change: MetricChange? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1.1f),
            style = MaterialTheme.typography.bodyMedium,
            softWrap = true,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = PlexMonoFamily,
                softWrap = true,
            )
            if (change != null) {
                val color = when (change.trend) {
                    MetricTrend.Improved -> TrendGreen
                    MetricTrend.Worsened -> TrendRed
                }
                Icon(
                    imageVector = if (change.rose) {
                        Icons.Filled.ArrowDropUp
                    } else {
                        Icons.Filled.ArrowDropDown
                    },
                    contentDescription = when (change.trend) {
                        MetricTrend.Improved -> "Improved vs last reading"
                        MetricTrend.Worsened -> "Worsened vs last reading"
                    },
                    tint = color,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun HexBlock(title: String, hex: String) {
    Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    Text(
        text = hex.ifBlank { "(none)" },
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        softWrap = true,
    )
}

private fun fmtKg(v: Float) = "${"%.2f".format(v)} kg"
