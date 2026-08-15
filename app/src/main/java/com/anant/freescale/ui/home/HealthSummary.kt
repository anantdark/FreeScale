package com.anant.freescale.ui.home

import androidx.compose.ui.graphics.Color
import com.anant.freescale.data.ScaleMeasurement

enum class HealthTone {
    Good,
    Fair,
    Poor,
}

data class HealthSummary(
    val tone: HealthTone,
    val title: String,
    val scoreLabel: String,
    val subtitle: String,
    val metricName: String,
)

/** Prefer WLA25 body score; fall back to BMI when score is missing. */
fun healthSummary(m: ScaleMeasurement?): HealthSummary? {
    if (m == null || m.weight <= 0f) return null
    if (m.hasBodyComp && m.bodyScore > 0f) {
        val score = m.bodyScore
        val tone = when {
            score >= 80f -> HealthTone.Good
            score >= 60f -> HealthTone.Fair
            else -> HealthTone.Poor
        }
        val title = when (tone) {
            HealthTone.Good -> "Strong"
            HealthTone.Fair -> "Fair"
            HealthTone.Poor -> "Attention"
        }
        return HealthSummary(
            tone = tone,
            title = title,
            scoreLabel = "%.0f".format(score),
            subtitle = "Body score · BMI %.1f".format(m.bmi),
            metricName = "Score",
        )
    }
    if (m.bmi > 0f) {
        val bmi = m.bmi
        val tone = when {
            bmi in 18.5f..24.9f -> HealthTone.Good
            bmi in 17f..<18.5f || bmi in 25f..29.9f -> HealthTone.Fair
            else -> HealthTone.Poor
        }
        val title = when (tone) {
            HealthTone.Good -> "Healthy BMI"
            HealthTone.Fair -> "Borderline BMI"
            HealthTone.Poor -> "BMI alert"
        }
        return HealthSummary(
            tone = tone,
            title = title,
            scoreLabel = "%.1f".format(bmi),
            subtitle = "BMI (no body score yet)",
            metricName = "BMI",
        )
    }
    return null
}

fun HealthTone.indicatorColor(): Color = when (this) {
    HealthTone.Good -> Color(0xFF2E9B6B)
    HealthTone.Fair -> Color(0xFFD4A017)
    HealthTone.Poor -> Color(0xFFD64545)
}

fun HealthTone.softContainer(): Color = when (this) {
    HealthTone.Good -> Color(0xFF2E9B6B).copy(alpha = 0.14f)
    HealthTone.Fair -> Color(0xFFD4A017).copy(alpha = 0.16f)
    HealthTone.Poor -> Color(0xFFD64545).copy(alpha = 0.14f)
}
