package com.anant.freescale.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.anant.freescale.data.ScaleMeasurement
import com.anant.freescale.data.SegmentMetrics
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordedAtEpochMs: Long,
    val userId: Int,
    val genderLabel: String,
    val algorithm: String,
    val weight: Float,
    val heightCm: Float,
    val ageYears: Int,
    val bmi: Float,
    val fat: Float,
    val fatMassKg: Float,
    val subcutaneousFat: Float,
    val visceralFat: Float,
    val water: Float,
    val waterKg: Float,
    val muscle: Float,
    val muscleMassKg: Float,
    val skeletalMuscle: Float,
    val bone: Float,
    val protein: Float,
    val proteinKg: Float,
    val lbm: Float,
    val bmr: Float,
    val bodyAge: Int,
    val obesityDegree: Float,
    val idealWeightKg: Float,
    val bodyScore: Float,
    val impedance: Double,
    val h2rCoeff: Double,
    val channelAOhm: Double,
    val channelBOhm: Double,
    val pkt0Cmd: Int,
    val pkt0ValidFlag: Int,
    val zSegmentsJson: String,
    val wla25InputsJson: String,
    val segmentsJson: String,
    val pkt0Hex: String,
    val pkt1Hex: String,
    val pkt2Hex: String,
) {
    fun toDomain(): ScaleMeasurement = ScaleMeasurement(
        userId = userId,
        dateTime = Date(recordedAtEpochMs),
        genderLabel = genderLabel,
        algorithm = algorithm,
        weight = weight,
        heightCm = heightCm,
        ageYears = ageYears,
        bmi = bmi,
        fat = fat,
        fatMassKg = fatMassKg,
        subcutaneousFat = subcutaneousFat,
        visceralFat = visceralFat,
        water = water,
        waterKg = waterKg,
        muscle = muscle,
        muscleMassKg = muscleMassKg,
        skeletalMuscle = skeletalMuscle,
        bone = bone,
        protein = protein,
        proteinKg = proteinKg,
        lbm = lbm,
        bmr = bmr,
        bodyAge = bodyAge,
        obesityDegree = obesityDegree,
        idealWeightKg = idealWeightKg,
        bodyScore = bodyScore,
        impedance = impedance,
        h2rCoeff = h2rCoeff,
        channelAOhm = channelAOhm,
        channelBOhm = channelBOhm,
        pkt0Cmd = pkt0Cmd,
        pkt0ValidFlag = pkt0ValidFlag,
        zSegments = decodeFloatList(zSegmentsJson),
        wla25Inputs = decodeFloatList(wla25InputsJson),
        segments = decodeSegments(segmentsJson),
        pkt0Hex = pkt0Hex,
        pkt1Hex = pkt1Hex,
        pkt2Hex = pkt2Hex,
    )

    companion object {
        fun fromDomain(m: ScaleMeasurement): MeasurementEntity {
            val at = m.dateTime?.time ?: System.currentTimeMillis()
            return MeasurementEntity(
                recordedAtEpochMs = at,
                userId = m.userId,
                genderLabel = m.genderLabel,
                algorithm = m.algorithm,
                weight = m.weight,
                heightCm = m.heightCm,
                ageYears = m.ageYears,
                bmi = m.bmi,
                fat = m.fat,
                fatMassKg = m.fatMassKg,
                subcutaneousFat = m.subcutaneousFat,
                visceralFat = m.visceralFat,
                water = m.water,
                waterKg = m.waterKg,
                muscle = m.muscle,
                muscleMassKg = m.muscleMassKg,
                skeletalMuscle = m.skeletalMuscle,
                bone = m.bone,
                protein = m.protein,
                proteinKg = m.proteinKg,
                lbm = m.lbm,
                bmr = m.bmr,
                bodyAge = m.bodyAge,
                obesityDegree = m.obesityDegree,
                idealWeightKg = m.idealWeightKg,
                bodyScore = m.bodyScore,
                impedance = m.impedance,
                h2rCoeff = m.h2rCoeff,
                channelAOhm = m.channelAOhm,
                channelBOhm = m.channelBOhm,
                pkt0Cmd = m.pkt0Cmd,
                pkt0ValidFlag = m.pkt0ValidFlag,
                zSegmentsJson = encodeFloatList(m.zSegments),
                wla25InputsJson = encodeFloatList(m.wla25Inputs),
                segmentsJson = encodeSegments(m.segments),
                pkt0Hex = m.pkt0Hex,
                pkt1Hex = m.pkt1Hex,
                pkt2Hex = m.pkt2Hex,
            )
        }

        private fun encodeFloatList(values: List<Float>): String {
            val arr = JSONArray()
            values.forEach { arr.put(it.toDouble()) }
            return arr.toString()
        }

        private fun decodeFloatList(json: String): List<Float> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                List(arr.length()) { i -> arr.getDouble(i).toFloat() }
            }.getOrDefault(emptyList())
        }

        private fun encodeSegments(segments: List<SegmentMetrics>): String {
            val arr = JSONArray()
            segments.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("name", s.name)
                        .put("fatKg", s.fatKg.toDouble())
                        .put("fatPct", s.fatPct.toDouble())
                        .put("muscleKg", s.muscleKg.toDouble())
                        .put("musclePct", s.musclePct.toDouble()),
                )
            }
            return arr.toString()
        }

        private fun decodeSegments(json: String): List<SegmentMetrics> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    SegmentMetrics(
                        name = o.optString("name"),
                        fatKg = o.optDouble("fatKg").toFloat(),
                        fatPct = o.optDouble("fatPct").toFloat(),
                        muscleKg = o.optDouble("muscleKg").toFloat(),
                        musclePct = o.optDouble("musclePct").toFloat(),
                    )
                }
            }.getOrDefault(emptyList())
        }
    }
}
