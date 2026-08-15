package com.anant.freescale.ui

import com.anant.freescale.data.ScaleMeasurement

/** Whether a higher value is healthier for this metric. */
enum class MetricHigherIs {
    Better,
    Worse,
}

enum class MetricTrend {
    /** Value moved in a healthier direction. */
    Improved,
    /** Value moved in an unhealthier direction. */
    Worsened,
}

data class MetricChange(
    val trend: MetricTrend,
    /** True if the new value is higher than the previous. */
    val rose: Boolean,
)

fun metricChange(
    current: Float,
    previous: Float,
    higherIs: MetricHigherIs,
    epsilon: Float = 0.005f,
): MetricChange? {
    val delta = current - previous
    if (kotlin.math.abs(delta) < epsilon) return null
    val rose = delta > 0f
    val improved = when (higherIs) {
        MetricHigherIs.Better -> rose
        MetricHigherIs.Worse -> !rose
    }
    return MetricChange(
        trend = if (improved) MetricTrend.Improved else MetricTrend.Worsened,
        rose = rose,
    )
}

/**
 * Prior reading to compare against [current].
 * [historyNewestFirst] is Room history (newest first).
 */
fun previousMeasurement(
    current: ScaleMeasurement,
    historyNewestFirst: List<ScaleMeasurement>,
): ScaleMeasurement? {
    val currentAt = current.dateTime?.time
    if (currentAt != null) {
        val idx = historyNewestFirst.indexOfFirst { it.dateTime?.time == currentAt }
        if (idx >= 0) return historyNewestFirst.getOrNull(idx + 1)
    }
    // Live / unsaved reading: compare to latest stored row.
    return historyNewestFirst.firstOrNull()
}
