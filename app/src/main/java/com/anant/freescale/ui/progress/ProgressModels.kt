package com.anant.freescale.ui.progress

import com.anant.freescale.data.ScaleMeasurement
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

enum class PeriodUnit {
    Week,
    Month,
}

enum class ProgressMetric(
    val label: String,
    val unitSuffix: String,
    val minSpan: Float,
    /** If true, a decrease is tinted as healthier progress. Null = neutral (weight). */
    val decreaseIsPositive: Boolean?,
) {
    Weight("Weight", "kg", minSpan = 2f, decreaseIsPositive = null),
    BodyFat("Body fat", "%", minSpan = 2f, decreaseIsPositive = true),
    VisceralFat("Visceral fat", "", minSpan = 1f, decreaseIsPositive = true),
    SubcutaneousFat("Subcutaneous", "%", minSpan = 2f, decreaseIsPositive = true),
    Muscle("Muscle", "%", minSpan = 2f, decreaseIsPositive = false),
}

data class ProgressPeriod(
    val unit: PeriodUnit,
    /** Any date inside the period; normalized to period start. */
    val anchor: LocalDate,
) {
    val start: LocalDate = when (unit) {
        PeriodUnit.Week -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        PeriodUnit.Month -> anchor.withDayOfMonth(1)
    }

    val endExclusive: LocalDate = when (unit) {
        PeriodUnit.Week -> start.plusWeeks(1)
        PeriodUnit.Month -> start.plusMonths(1)
    }

    val endInclusive: LocalDate = endExclusive.minusDays(1)

    fun startEpochMs(zone: ZoneId = ZoneId.systemDefault()): Long =
        start.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endExclusiveEpochMs(zone: ZoneId = ZoneId.systemDefault()): Long =
        endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

    fun previous(): ProgressPeriod = when (unit) {
        PeriodUnit.Week -> copy(anchor = start.minusWeeks(1))
        PeriodUnit.Month -> copy(anchor = start.minusMonths(1))
    }

    fun next(): ProgressPeriod = when (unit) {
        PeriodUnit.Week -> copy(anchor = start.plusWeeks(1))
        PeriodUnit.Month -> copy(anchor = start.plusMonths(1))
    }

    /** True when this period starts after today (fully in the future). */
    fun isFullyAfter(today: LocalDate = LocalDate.now()): Boolean =
        start.isAfter(today)

    /** True when the next period would start after today. */
    fun canGoNext(today: LocalDate = LocalDate.now()): Boolean =
        !next().start.isAfter(today)

    fun label(locale: Locale = Locale.getDefault()): String = when (unit) {
        PeriodUnit.Week -> {
            val sameMonth = start.month == endInclusive.month && start.year == endInclusive.year
            if (sameMonth) {
                val month = start.format(DateTimeFormatter.ofPattern("MMM", locale))
                "$month ${start.dayOfMonth}–${endInclusive.dayOfMonth}"
            } else {
                val left = start.format(DateTimeFormatter.ofPattern("MMM d", locale))
                val right = endInclusive.format(DateTimeFormatter.ofPattern("MMM d", locale))
                "$left – $right"
            }
        }
        PeriodUnit.Month ->
            start.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    }

    companion object {
        fun current(unit: PeriodUnit, today: LocalDate = LocalDate.now()): ProgressPeriod =
            ProgressPeriod(unit = unit, anchor = today)
    }
}

data class ChartPoint(
    val epochMs: Long,
    val day: LocalDate,
    val value: Float,
    val measurement: ScaleMeasurement,
)

data class YDomain(
    val min: Float,
    val max: Float,
    val ticks: List<Float>,
)

data class ChartSeries(
    val metric: ProgressMetric,
    val points: List<ChartPoint>,
    val yDomain: YDomain,
    val startValue: Float?,
    val endValue: Float?,
    val delta: Float?,
)

fun ProgressMetric.extract(m: ScaleMeasurement): Float? {
    // Weight chart matches body-comp charts: skip weight-only readings.
    if (!m.hasBodyComp) return null
    val v = when (this) {
        ProgressMetric.Weight -> m.weight
        ProgressMetric.BodyFat -> m.fat
        ProgressMetric.VisceralFat -> m.visceralFat
        ProgressMetric.SubcutaneousFat -> m.subcutaneousFat
        ProgressMetric.Muscle -> m.muscle
    }
    if (v <= 0f) return null
    return v
}

/**
 * One chart point per reading (not collapsed by day).
 * Skips weight-only readings for every metric, including Weight.
 */
fun buildReadingSeries(
    readings: List<ScaleMeasurement>,
    metric: ProgressMetric,
    zone: ZoneId = ZoneId.systemDefault(),
): List<ChartPoint> {
    return readings.mapNotNull { m ->
        val epoch = m.dateTime?.time ?: return@mapNotNull null
        val value = metric.extract(m) ?: return@mapNotNull null
        val day = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
        ChartPoint(epochMs = epoch, day = day, value = value, measurement = m)
    }.sortedBy { it.epochMs }
}

fun buildChartSeries(points: List<ChartPoint>, metric: ProgressMetric): ChartSeries {
    val start = points.firstOrNull()?.value
    val end = points.lastOrNull()?.value
    val delta = if (start != null && end != null) end - start else null
    return ChartSeries(
        metric = metric,
        points = points,
        yDomain = computeYDomain(points.map { it.value }, metric.minSpan),
        startValue = start,
        endValue = end,
        delta = delta,
    )
}

fun computeYDomain(values: List<Float>, minSpan: Float): YDomain {
    if (values.isEmpty()) {
        return YDomain(min = 0f, max = minSpan, ticks = listOf(0f, minSpan / 2f, minSpan))
    }
    val rawMin = values.min()
    val rawMax = values.max()
    val mid = (rawMin + rawMax) / 2f
    val span = maxOf(rawMax - rawMin, minSpan)
    val pad = span * 0.15f
    var yMin = mid - span / 2f - pad
    var yMax = mid + span / 2f + pad
    // Keep non-negative floors for physical metrics
    if (yMin < 0f && rawMin >= 0f) {
        yMax += -yMin
        yMin = 0f
    }
    val nice = niceBounds(yMin.toDouble(), yMax.toDouble())
    yMin = nice.first.toFloat()
    yMax = nice.second.toFloat()
    val ticks = niceTicks(yMin, yMax, targetCount = 4)
    return YDomain(min = yMin, max = yMax, ticks = ticks)
}

/** Round [lo, hi] outward to a clean tick-friendly interval. */
private fun niceBounds(lo: Double, hi: Double): Pair<Double, Double> {
    if (hi <= lo) return lo to (lo + 1.0)
    val range = niceNumber(hi - lo, round = false)
    val step = niceNumber(range / 4.0, round = true)
    val niceLo = floor(lo / step) * step
    val niceHi = ceil(hi / step) * step
    return niceLo to niceHi
}

private fun niceTicks(min: Float, max: Float, targetCount: Int): List<Float> {
    if (max <= min) return listOf(min)
    val range = niceNumber((max - min).toDouble(), round = false)
    val step = niceNumber(range / targetCount.coerceAtLeast(1), round = true).toFloat()
    if (step <= 0f) return listOf(min, max)
    val ticks = mutableListOf<Float>()
    var v = (ceil(min / step) * step)
    // Avoid floating dust
    var guard = 0
    while (v <= max + step * 0.001f && guard < 20) {
        if (v >= min - step * 0.001f) ticks += v
        v += step
        guard++
    }
    if (ticks.isEmpty()) return listOf(min, max)
    return ticks
}

/** Wilkinson's "nice number" helper. */
private fun niceNumber(range: Double, round: Boolean): Double {
    if (range <= 0.0) return 1.0
    val exp = floor(log10(range))
    val frac = range / 10.0.pow(exp)
    val niceFrac = if (round) {
        when {
            frac < 1.5 -> 1.0
            frac < 3.0 -> 2.0
            frac < 7.0 -> 5.0
            else -> 10.0
        }
    } else {
        when {
            frac <= 1.0 -> 1.0
            frac <= 2.0 -> 2.0
            frac <= 5.0 -> 5.0
            else -> 10.0
        }
    }
    return niceFrac * 10.0.pow(exp)
}

fun formatMetricValue(value: Float, metric: ProgressMetric): String = when (metric) {
    ProgressMetric.Weight -> String.format(Locale.US, "%.1f", value)
    ProgressMetric.VisceralFat -> String.format(Locale.US, "%.1f", value)
    else -> String.format(Locale.US, "%.1f", value)
}

fun formatDelta(delta: Float, metric: ProgressMetric): String {
    val sign = when {
        delta > 0f -> "+"
        else -> ""
    }
    val body = formatMetricValue(delta, metric)
    val suffix = metric.unitSuffix
    return if (suffix.isEmpty()) "$sign$body" else "$sign$body $suffix"
}
