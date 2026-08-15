package com.anant.freescale.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricTrendTest {
    @Test
    fun weightUp_isWorsened_redUp() {
        val c = metricChange(71f, 70f, MetricHigherIs.Worse)!!
        assertEquals(MetricTrend.Worsened, c.trend)
        assertTrue(c.rose)
    }

    @Test
    fun fatDown_isImproved_greenDown() {
        val c = metricChange(14f, 15f, MetricHigherIs.Worse)!!
        assertEquals(MetricTrend.Improved, c.trend)
        assertTrue(!c.rose)
    }

    @Test
    fun muscleDown_isWorsened_redDown() {
        val c = metricChange(48f, 50f, MetricHigherIs.Better)!!
        assertEquals(MetricTrend.Worsened, c.trend)
        assertTrue(!c.rose)
    }

    @Test
    fun muscleUp_isImproved_greenUp() {
        val c = metricChange(51f, 50f, MetricHigherIs.Better)!!
        assertEquals(MetricTrend.Improved, c.trend)
        assertTrue(c.rose)
    }

    @Test
    fun unchangedWithinEpsilon_isNull() {
        assertNull(metricChange(70.001f, 70f, MetricHigherIs.Worse))
    }
}
