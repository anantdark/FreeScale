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
 *
 * Body fat comes from ICOMON WLA37 (same native lib as Dr. Trust); other metrics
 * are derived from fat-free mass via the WLA25 chain. See `BodyCompositionBuilder`.
 *
 * The raw inputs ([impedances], [pkt0Hex]…) are kept alongside the results so a
 * stored row can be recomputed offline if the model changes.
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
    var pkt0Cmd: Int = 0,
    /** The scale's 10 impedances in ohm, as decoded by `Ssw532Impedances`. Empty when BIA was not measured. */
    var impedances: List<Float> = emptyList(),
    var segments: List<SegmentMetrics> = emptyList(),
    var pkt0Hex: String = "",
    var pkt1Hex: String = "",
    var pkt2Hex: String = "",
) {
    val hasBodyComp: Boolean get() = fat > 0f
}
