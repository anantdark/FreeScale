package com.anant.freescale.data.backup

import com.anant.freescale.BuildConfig
import com.anant.freescale.data.ScaleMeasurement
import com.anant.freescale.data.SegmentMetrics
import com.anant.freescale.data.UserProfilePrefs
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * Portable FreeScale backup document (JSON).
 *
 * Schema version 1: profile + full measurement dump (no auto-ids).
 */
object MeasurementBackupJson {
    const val FORMAT = "freescale-backup"
    const val VERSION = 1

    data class Document(
        val exportedAtEpochMs: Long,
        val appVersionName: String,
        val profile: UserProfilePrefs?,
        val measurements: List<ScaleMeasurement>,
    )

    fun encode(
        measurements: List<ScaleMeasurement>,
        profile: UserProfilePrefs?,
        exportedAtEpochMs: Long = System.currentTimeMillis(),
    ): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAtEpochMs", exportedAtEpochMs)
            .put("appVersionName", BuildConfig.VERSION_NAME)
        if (profile != null) {
            root.put(
                "profile",
                JSONObject()
                    .put("heightCm", profile.heightCm)
                    .put("ageYears", profile.ageYears)
                    .put("male", profile.male),
            )
        } else {
            root.put("profile", JSONObject.NULL)
        }
        val arr = JSONArray()
        measurements.forEach { arr.put(encodeMeasurement(it)) }
        root.put("measurements", arr)
        return root.toString(2)
    }

    fun decode(json: String): Document {
        val root = JSONObject(json)
        val format = root.optString("format")
        if (format != FORMAT) {
            throw IllegalArgumentException("Not a FreeScale backup (format=$format)")
        }
        val version = root.optInt("version", 0)
        if (version < 1 || version > VERSION) {
            throw IllegalArgumentException("Unsupported backup version $version")
        }
        val profileObj = root.optJSONObject("profile")
        val profile = profileObj?.let {
            UserProfilePrefs(
                heightCm = it.optString("heightCm", "175"),
                ageYears = it.optString("ageYears", "26"),
                male = it.optBoolean("male", true),
            )
        }
        val arr = root.optJSONArray("measurements") ?: JSONArray()
        val measurements = buildList {
            for (i in 0 until arr.length()) {
                add(decodeMeasurement(arr.getJSONObject(i)))
            }
        }
        return Document(
            exportedAtEpochMs = root.optLong("exportedAtEpochMs", 0L),
            appVersionName = root.optString("appVersionName"),
            profile = profile,
            measurements = measurements,
        )
    }

    private fun encodeMeasurement(m: ScaleMeasurement): JSONObject {
        val at = m.dateTime?.time ?: 0L
        return JSONObject()
            .put("recordedAtEpochMs", at)
            .put("userId", m.userId)
            .put("genderLabel", m.genderLabel)
            .put("algorithm", m.algorithm)
            .put("weight", m.weight.toDouble())
            .put("heightCm", m.heightCm.toDouble())
            .put("ageYears", m.ageYears)
            .put("bmi", m.bmi.toDouble())
            .put("fat", m.fat.toDouble())
            .put("fatMassKg", m.fatMassKg.toDouble())
            .put("subcutaneousFat", m.subcutaneousFat.toDouble())
            .put("visceralFat", m.visceralFat.toDouble())
            .put("water", m.water.toDouble())
            .put("waterKg", m.waterKg.toDouble())
            .put("muscle", m.muscle.toDouble())
            .put("muscleMassKg", m.muscleMassKg.toDouble())
            .put("skeletalMuscle", m.skeletalMuscle.toDouble())
            .put("bone", m.bone.toDouble())
            .put("protein", m.protein.toDouble())
            .put("proteinKg", m.proteinKg.toDouble())
            .put("lbm", m.lbm.toDouble())
            .put("bmr", m.bmr.toDouble())
            .put("bodyAge", m.bodyAge)
            .put("obesityDegree", m.obesityDegree.toDouble())
            .put("idealWeightKg", m.idealWeightKg.toDouble())
            .put("bodyScore", m.bodyScore.toDouble())
            .put("impedance", m.impedance)
            .put("h2rCoeff", m.h2rCoeff)
            .put("channelAOhm", m.channelAOhm)
            .put("channelBOhm", m.channelBOhm)
            .put("pkt0Cmd", m.pkt0Cmd)
            .put("pkt0ValidFlag", m.pkt0ValidFlag)
            .put("zSegments", encodeFloatArray(m.zSegments))
            .put("wla25Inputs", encodeFloatArray(m.wla25Inputs))
            .put("segments", encodeSegments(m.segments))
            .put("pkt0Hex", m.pkt0Hex)
            .put("pkt1Hex", m.pkt1Hex)
            .put("pkt2Hex", m.pkt2Hex)
    }

    private fun decodeMeasurement(o: JSONObject): ScaleMeasurement {
        val at = o.optLong("recordedAtEpochMs", 0L)
        return ScaleMeasurement(
            userId = o.optInt("userId", 1),
            dateTime = if (at > 0L) Date(at) else null,
            genderLabel = o.optString("genderLabel"),
            algorithm = o.optString("algorithm"),
            weight = o.optDouble("weight").toFloat(),
            heightCm = o.optDouble("heightCm").toFloat(),
            ageYears = o.optInt("ageYears"),
            bmi = o.optDouble("bmi").toFloat(),
            fat = o.optDouble("fat").toFloat(),
            fatMassKg = o.optDouble("fatMassKg").toFloat(),
            subcutaneousFat = o.optDouble("subcutaneousFat").toFloat(),
            visceralFat = o.optDouble("visceralFat").toFloat(),
            water = o.optDouble("water").toFloat(),
            waterKg = o.optDouble("waterKg").toFloat(),
            muscle = o.optDouble("muscle").toFloat(),
            muscleMassKg = o.optDouble("muscleMassKg").toFloat(),
            skeletalMuscle = o.optDouble("skeletalMuscle").toFloat(),
            bone = o.optDouble("bone").toFloat(),
            protein = o.optDouble("protein").toFloat(),
            proteinKg = o.optDouble("proteinKg").toFloat(),
            lbm = o.optDouble("lbm").toFloat(),
            bmr = o.optDouble("bmr").toFloat(),
            bodyAge = o.optInt("bodyAge"),
            obesityDegree = o.optDouble("obesityDegree").toFloat(),
            idealWeightKg = o.optDouble("idealWeightKg").toFloat(),
            bodyScore = o.optDouble("bodyScore").toFloat(),
            impedance = o.optDouble("impedance"),
            h2rCoeff = o.optDouble("h2rCoeff"),
            channelAOhm = o.optDouble("channelAOhm"),
            channelBOhm = o.optDouble("channelBOhm"),
            pkt0Cmd = o.optInt("pkt0Cmd"),
            pkt0ValidFlag = o.optInt("pkt0ValidFlag"),
            zSegments = decodeFloatArray(o.optJSONArray("zSegments")),
            wla25Inputs = decodeFloatArray(o.optJSONArray("wla25Inputs")),
            segments = decodeSegments(o.optJSONArray("segments")),
            pkt0Hex = o.optString("pkt0Hex"),
            pkt1Hex = o.optString("pkt1Hex"),
            pkt2Hex = o.optString("pkt2Hex"),
        )
    }

    private fun encodeFloatArray(values: List<Float>): JSONArray {
        val arr = JSONArray()
        values.forEach { arr.put(it.toDouble()) }
        return arr
    }

    private fun decodeFloatArray(arr: JSONArray?): List<Float> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i -> arr.getDouble(i).toFloat() }
    }

    private fun encodeSegments(segments: List<SegmentMetrics>): JSONArray {
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
        return arr
    }

    private fun decodeSegments(arr: JSONArray?): List<SegmentMetrics> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            SegmentMetrics(
                name = o.optString("name"),
                fatKg = o.optDouble("fatKg").toFloat(),
                fatPct = o.optDouble("fatPct").toFloat(),
                muscleKg = o.optDouble("muscleKg").toFloat(),
                musclePct = o.optDouble("musclePct").toFloat(),
            )
        }
    }
}
