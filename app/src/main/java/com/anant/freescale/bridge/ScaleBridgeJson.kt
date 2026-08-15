package com.anant.freescale.bridge

import com.anant.freescale.data.ScaleMeasurement
import com.anant.freescale.data.backup.MeasurementBackupJson
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Wire format shared with FitBuddy (`anant-scale-bridge` v1).
 *
 * Overlapping body-comp fields are always present for FitBuddy UI/charts.
 * [KEY_FREESCALE_PAYLOAD] carries the full FreeScale reading (Ω / BLE / segments / …)
 * so FitBuddy can store it opaquely and FreeScale can restore a complete row later.
 *
 * Floating metrics are rounded to 2 decimal places before encode so float→double
 * binary noise (e.g. 23.299999…) never lands in FitBuddy display fields.
 */
object ScaleBridgeJson {
    const val FORMAT = "anant-scale-bridge"
    const val VERSION = 1
    const val KEY_FREESCALE_PAYLOAD = "freescalePayload"

    private val dateFmt: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

    private val displayWhen: SimpleDateFormat
        get() = SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault())

    fun encode(m: ScaleMeasurement): String = encodeObject(m).toString()

    fun encodeArray(measurements: List<ScaleMeasurement>): String {
        val arr = JSONArray()
        measurements.forEach { arr.put(encodeObject(it)) }
        return arr.toString()
    }

    fun decode(json: String): ScaleMeasurement = decodeObject(JSONObject(json))

    fun decodeArray(json: String): List<ScaleMeasurement> {
        val arr = JSONArray(json)
        return List(arr.length()) { i -> decodeObject(arr.getJSONObject(i)) }
    }

    /**
     * Human-readable rows for the share confirmation preview
     * (overlapping fields only — full FreeScale dump is attached silently).
     */
    fun previewRows(m: ScaleMeasurement): List<Pair<String, String>> {
        val at = m.dateTime?.time ?: 0L
        val rows = mutableListOf<Pair<String, String>>()
        if (at > 0L) {
            rows += "When" to displayWhen.format(Date(at))
        }
        rows += "Weight" to "${fmt2(m.weight)} kg"
        addPreview(rows, "BMI", m.bmi)
        addPreview(rows, "Body fat", m.fat, "%")
        addPreview(rows, "Muscle rate", m.muscle, "%")
        addPreview(rows, "Body water", m.water, "%")
        addPreview(rows, "Bone mass", m.bone, " kg")
        if (m.bmr > 0f) rows += "BMR" to "${m.bmr.toInt()} kcal"
        if (m.bodyAge > 0) rows += "Metabolic age" to "${m.bodyAge}"
        addPreview(rows, "Visceral fat", m.visceralFat)
        addPreview(rows, "Subcutaneous fat", m.subcutaneousFat, "%")
        addPreview(rows, "Protein mass", m.proteinKg, " kg")
        addPreview(rows, "Muscle mass", m.muscleMassKg, " kg")
        addPreview(rows, "Fat-free mass", m.lbm, " kg")
        addPreview(rows, "Skeletal muscle", m.skeletalMuscle, " kg")
        addPreview(rows, "Water weight", m.waterKg, " kg")
        addPreview(rows, "Fat mass", m.fatMassKg, " kg")
        rows += "Full FreeScale dump" to "Included (hidden in FitBuddy)"
        return rows
    }

    fun encodeObject(m: ScaleMeasurement): JSONObject {
        val at = m.dateTime?.time ?: 0L
        require(at > 0L) { "Measurement needs a timestamp to share" }
        require(m.weight > 0f) { "Measurement needs weight to share" }

        val o = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("timestamp", at)
            .put("dateString", dateFmt.format(Date(at)))
            .put("weightKg", round2(m.weight))

        putOptional(o, "bmi", m.bmi)
        putOptional(o, "bodyFatPct", m.fat)
        putOptional(o, "muscleRatePct", m.muscle)
        putOptional(o, "bodyWaterPct", m.water)
        putOptional(o, "boneMassKg", m.bone)
        if (m.bmr > 0f) o.put("bmr", m.bmr.toInt())
        if (m.bodyAge > 0) o.put("metabolicAge", m.bodyAge)
        putOptional(o, "visceralFat", m.visceralFat)
        putOptional(o, "subcutaneousFatPct", m.subcutaneousFat)
        putOptional(o, "proteinMassKg", m.proteinKg)
        putOptional(o, "muscleMassKg", m.muscleMassKg)
        putOptional(o, "fatFreeMassKg", m.lbm)
        putOptional(o, "skeletalMuscleMassKg", m.skeletalMuscle)
        putOptional(o, "waterWeightKg", m.waterKg)
        putOptional(o, "fatMassKg", m.fatMassKg)
        o.put(KEY_FREESCALE_PAYLOAD, MeasurementBackupJson.encodeMeasurement(m))
        return o
    }

    fun decodeObject(o: JSONObject): ScaleMeasurement {
        val format = o.optString("format")
        if (format.isNotEmpty() && format != FORMAT) {
            throw IllegalArgumentException("Not a scale bridge payload (format=$format)")
        }
        val version = o.optInt("version", VERSION)
        if (version < 1 || version > VERSION) {
            throw IllegalArgumentException("Unsupported bridge version $version")
        }
        val payload = o.optJSONObject(KEY_FREESCALE_PAYLOAD)
        if (payload != null) {
            return MeasurementBackupJson.decodeMeasurement(payload)
        }
        val at = o.getLong("timestamp")
        return ScaleMeasurement(
            dateTime = Date(at),
            weight = round2(o.getDouble("weightKg")).toFloat(),
            bmi = optFloat(o, "bmi"),
            fat = optFloat(o, "bodyFatPct"),
            muscle = optFloat(o, "muscleRatePct"),
            water = optFloat(o, "bodyWaterPct"),
            bone = optFloat(o, "boneMassKg"),
            bmr = if (o.has("bmr") && !o.isNull("bmr")) o.getInt("bmr").toFloat() else 0f,
            bodyAge = if (o.has("metabolicAge") && !o.isNull("metabolicAge")) {
                o.getInt("metabolicAge")
            } else {
                0
            },
            visceralFat = optFloat(o, "visceralFat"),
            subcutaneousFat = optFloat(o, "subcutaneousFatPct"),
            proteinKg = optFloat(o, "proteinMassKg"),
            muscleMassKg = optFloat(o, "muscleMassKg"),
            lbm = optFloat(o, "fatFreeMassKg"),
            skeletalMuscle = optFloat(o, "skeletalMuscleMassKg"),
            waterKg = optFloat(o, "waterWeightKg"),
            fatMassKg = optFloat(o, "fatMassKg"),
        )
    }

    private fun putOptional(o: JSONObject, key: String, value: Float) {
        if (value > 0f) o.put(key, round2(value))
    }

    private fun optFloat(o: JSONObject, key: String): Float {
        if (!o.has(key) || o.isNull(key)) return 0f
        return round2(o.getDouble(key)).toFloat()
    }

    private fun addPreview(
        rows: MutableList<Pair<String, String>>,
        label: String,
        value: Float,
        suffix: String = "",
    ) {
        if (value > 0f) rows += label to "${fmt2(value)}$suffix"
    }

    /** Round via decimal string so float binary noise never expands. */
    fun round2(value: Float): Double =
        String.format(Locale.US, "%.2f", value).toDouble()

    fun round2(value: Double): Double =
        String.format(Locale.US, "%.2f", value).toDouble()

    private fun fmt2(value: Float): String =
        String.format(Locale.US, "%.2f", value)
}
