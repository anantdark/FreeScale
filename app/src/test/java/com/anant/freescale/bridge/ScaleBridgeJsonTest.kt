package com.anant.freescale.bridge

import com.anant.freescale.data.ScaleMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ScaleBridgeJsonTest {
    @Test
    fun roundTrip_mapsOverlappingFields_asKg() {
        val original = ScaleMeasurement(
            dateTime = Date(1_710_000_000_000L),
            weight = 70.95f,
            bmi = 23.2f,
            fat = 14.6f,
            muscle = 79.8f,
            water = 62.7f,
            bone = 4.1f,
            bmr = 1680f,
            bodyAge = 24,
            visceralFat = 2f,
            subcutaneousFat = 10.5f,
            proteinKg = 17.1f,
            muscleMassKg = 56.62f,
            lbm = 60.59f,
            skeletalMuscle = 48.6f,
            waterKg = 44.49f,
            fatMassKg = 10.36f,
            // FreeScale-only — survives via freescalePayload for restore
            impedance = 500.0,
            algorithm = "WLA25",
            pkt0Hex = "aabb",
        )

        val decoded = ScaleBridgeJson.decode(ScaleBridgeJson.encode(original))

        assertEquals(original.dateTime?.time, decoded.dateTime?.time)
        assertEquals(original.weight, decoded.weight, 0.001f)
        assertEquals(original.bmi, decoded.bmi, 0.001f)
        assertEquals(original.fat, decoded.fat, 0.001f)
        assertEquals(original.muscle, decoded.muscle, 0.001f)
        assertEquals(original.water, decoded.water, 0.001f)
        assertEquals(original.bone, decoded.bone, 0.001f)
        assertEquals(original.bmr, decoded.bmr, 0.001f)
        assertEquals(original.bodyAge, decoded.bodyAge)
        assertEquals(original.visceralFat, decoded.visceralFat, 0.001f)
        assertEquals(original.subcutaneousFat, decoded.subcutaneousFat, 0.001f)
        assertEquals(original.proteinKg, decoded.proteinKg, 0.001f)
        assertEquals(original.muscleMassKg, decoded.muscleMassKg, 0.001f)
        assertEquals(original.lbm, decoded.lbm, 0.001f)
        assertEquals(original.skeletalMuscle, decoded.skeletalMuscle, 0.001f)
        assertEquals(original.waterKg, decoded.waterKg, 0.001f)
        assertEquals(original.fatMassKg, decoded.fatMassKg, 0.001f)

        assertEquals(500.0, decoded.impedance, 0.0)
        assertEquals("WLA25", decoded.algorithm)
        assertEquals("aabb", decoded.pkt0Hex)
        assertTrue(decoded.hasBodyComp)
    }

    @Test
    fun encode_omitsZeroOptionals() {
        val json = ScaleBridgeJson.encode(
            ScaleMeasurement(
                dateTime = Date(1_710_000_000_000L),
                weight = 70f,
            ),
        )
        assertTrue(json.contains("\"weightKg\""))
        assertFalse(json.contains("\"bodyFatPct\""))
        assertFalse(json.contains("\"skeletalMuscleMassKg\""))
    }

    @Test
    fun encode_roundsFloatNoise_toTwoDecimals() {
        // Classic float→double expansion (23.3f becomes 23.299999…)
        val o = ScaleBridgeJson.encodeObject(
            ScaleMeasurement(
                dateTime = Date(1_710_000_000_000L),
                weight = 71.5f,
                bmi = 23.3f,
                water = 60.9f,
                proteinKg = 11.869f,
                muscleMassKg = 55.4125f,
            ),
        )
        assertEquals(23.3, o.getDouble("bmi"), 0.0)
        assertEquals(60.9, o.getDouble("bodyWaterPct"), 0.0)
        assertEquals(11.87, o.getDouble("proteinMassKg"), 0.0)
        assertEquals(55.41, o.getDouble("muscleMassKg"), 0.0)
        // Nested FreeScale dump is allowed to keep raw floats; display fields above are rounded.
        assertTrue(o.has(ScaleBridgeJson.KEY_FREESCALE_PAYLOAD))
    }

    @Test
    fun encode_includesFreescalePayload() {
        val original = ScaleMeasurement(
            dateTime = Date(1_710_000_000_000L),
            weight = 71.5f,
            fat = 17f,
            impedance = 512.0,
            algorithm = "WLA25",
            pkt0Hex = "aabb",
        )
        val o = ScaleBridgeJson.encodeObject(original)
        assertTrue(o.has(ScaleBridgeJson.KEY_FREESCALE_PAYLOAD))
        val decoded = ScaleBridgeJson.decodeObject(o)
        assertEquals(512.0, decoded.impedance, 0.0)
        assertEquals("WLA25", decoded.algorithm)
        assertEquals("aabb", decoded.pkt0Hex)
        assertEquals(71.5f, decoded.weight, 0.001f)
    }
}
