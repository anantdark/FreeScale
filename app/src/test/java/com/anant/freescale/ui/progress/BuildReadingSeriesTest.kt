package com.anant.freescale.ui.progress

import com.anant.freescale.data.ScaleMeasurement
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class BuildReadingSeriesTest {
    @Test
    fun keepsMultipleReadingsOnSameDay() {
        val day = 1_710_000_000_000L
        val readings = listOf(
            ScaleMeasurement(dateTime = Date(day), weight = 70f, fat = 15f),
            ScaleMeasurement(dateTime = Date(day + 3_600_000L), weight = 70.4f, fat = 14.8f),
            ScaleMeasurement(dateTime = Date(day + 7_200_000L), weight = 69.9f, fat = 14.9f),
        )
        val points = buildReadingSeries(readings, ProgressMetric.Weight)
        assertEquals(3, points.size)
        assertEquals(70f, points[0].value, 0.001f)
        assertEquals(70.4f, points[1].value, 0.001f)
        assertEquals(69.9f, points[2].value, 0.001f)
    }

    @Test
    fun weightSeriesSkipsWeightOnlyReadings() {
        val day = 1_710_000_000_000L
        val readings = listOf(
            ScaleMeasurement(dateTime = Date(day), weight = 70f, fat = 15f),
            ScaleMeasurement(dateTime = Date(day + 3_600_000L), weight = 71f, fat = 0f),
            ScaleMeasurement(dateTime = Date(day + 7_200_000L), weight = 69.5f, fat = 14.9f),
        )
        val points = buildReadingSeries(readings, ProgressMetric.Weight)
        assertEquals(2, points.size)
        assertEquals(70f, points[0].value, 0.001f)
        assertEquals(69.5f, points[1].value, 0.001f)
    }
}
