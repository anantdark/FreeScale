package com.anant.freescale.data

import java.util.Date

data class SegmentMetrics(
    val name: String,
    val fatKg: Float = 0f,
    val fatPct: Float = 0f,
    val muscleKg: Float = 0f,
    val musclePct: Float = 0f,
)

/**
 * Full open dump of one SSW532 measurement.
 * Composition derived with Chipsea/ICOMON WLA25 (same OEM family as FG2211WB).
 */
data class ScaleMeasurement(
    var userId: Int = 1,
    var dateTime: Date? = null,
    var genderLabel: String = "",
    var algorithm: String = "",
    var weight: Float = 0.0f,
    var heightCm: Float = 0.0f,
    var ageYears: Int = 0,
    var bmi: Float = 0.0f,
    var fat: Float = 0.0f,
    var fatMassKg: Float = 0.0f,
    var subcutaneousFat: Float = 0.0f,
    var visceralFat: Float = 0.0f,
    var water: Float = 0.0f,
    var waterKg: Float = 0.0f,
    var muscle: Float = 0.0f,
    var muscleMassKg: Float = 0.0f,
    var skeletalMuscle: Float = 0.0f,
    var bone: Float = 0.0f,
    var protein: Float = 0.0f,
    var proteinKg: Float = 0.0f,
    var lbm: Float = 0.0f,
    var bmr: Float = 0.0f,
    var bodyAge: Int = 0,
    var obesityDegree: Float = 0.0f,
    var idealWeightKg: Float = 0.0f,
    var bodyScore: Float = 0.0f,
    var impedance: Double = 0.0,
    var h2rCoeff: Double = 0.0,
    var channelAOhm: Double = 0.0,
    var channelBOhm: Double = 0.0,
    var pkt0Cmd: Int = 0,
    var pkt0ValidFlag: Int = 0,
    var zSegments: List<Float> = emptyList(),
    var wla25Inputs: List<Float> = emptyList(),
    var segments: List<SegmentMetrics> = emptyList(),
    var pkt0Hex: String = "",
    var pkt1Hex: String = "",
    var pkt2Hex: String = "",
) {
    val hasBodyComp: Boolean get() = fat > 0f

    fun segmentLabeled(): List<Pair<String, Float>> {
        val labels = listOf(
            "Z1 (arm path)",
            "Z2 (arm path)",
            "Z3 Trunk",
            "Z4 Right leg",
            "Z5 Left leg",
            "Z6 Cross-body",
            "Z7 Cross-body",
            "Z8 Trunk/path",
        )
        return zSegments.mapIndexed { i, v ->
            (labels.getOrElse(i) { "Z${i + 1}" }) to v
        }
    }
}
