package com.anant.freescale.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anant.freescale.ui.theme.PlexMonoFamily
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun TrendChartCard(
    series: ChartSeries,
    period: ProgressPeriod,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val deltaTint = remember(series.delta, series.metric) {
        deltaTintColor(series.delta, series.metric, scheme.primary, scheme.error, scheme.onSurface)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    series.metric.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val start = series.startValue
                val end = series.endValue
                if (start != null && end != null) {
                    val unit = series.metric.unitSuffix.let { if (it.isEmpty()) "" else " $it" }
                    Text(
                        "${formatMetricValue(start, series.metric)}$unit → " +
                            "${formatMetricValue(end, series.metric)}$unit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        fontFamily = PlexMonoFamily,
                    )
                } else {
                    Text(
                        "No readings in this ${period.unit.name.lowercase()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            series.delta?.let { d ->
                Text(
                    formatDelta(d, series.metric),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = deltaTint,
                    fontFamily = PlexMonoFamily,
                )
            }
        }

        if (series.points.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nothing to chart yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            ) {
                TrendChart(
                    series = series,
                    period = period,
                    lineColor = scheme.primary,
                    gridColor = scheme.outlineVariant.copy(alpha = 0.45f),
                    labelColor = scheme.onSurfaceVariant,
                    fillColor = scheme.primary.copy(alpha = 0.14f),
                    markerCoreColor = scheme.surface,
                )
            }
        }
    }
}

@Composable
private fun TrendChart(
    series: ChartSeries,
    period: ProgressPeriod,
    lineColor: Color,
    gridColor: Color,
    labelColor: Color,
    fillColor: Color,
    markerCoreColor: Color,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = TextStyle(
        fontSize = 10.sp,
        color = labelColor,
        fontFamily = PlexMonoFamily,
    )
    val dayFmt = remember {
        DateTimeFormatter.ofPattern(if (period.unit == PeriodUnit.Week) "EEE" else "d", Locale.getDefault())
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val leftPad = with(density) { 44.dp.toPx() }
        val rightPad = with(density) { 8.dp.toPx() }
        val topPad = with(density) { 12.dp.toPx() }
        val bottomPad = with(density) { 28.dp.toPx() }
        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - bottomPad
        if (plotW <= 0f || plotH <= 0f) return@Canvas

        val yMin = series.yDomain.min
        val yMax = series.yDomain.max
        val yRange = (yMax - yMin).coerceAtLeast(1e-3f)

        fun xForDayIndex(index: Int, totalSlots: Int): Float {
            if (totalSlots <= 1) return leftPad + plotW / 2f
            return leftPad + plotW * (index.toFloat() / (totalSlots - 1).toFloat())
        }

        fun yForValue(v: Float): Float {
            val t = ((v - yMin) / yRange).coerceIn(0f, 1f)
            return topPad + plotH * (1f - t)
        }

        // Grid + Y labels
        series.yDomain.ticks.forEach { tick ->
            val y = yForValue(tick)
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1f,
            )
            val label = formatMetricValue(tick, series.metric)
            val layout = measurer.measure(label, style = labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    leftPad - layout.size.width - with(density) { 6.dp.toPx() },
                    y - layout.size.height / 2f,
                ),
            )
        }

        val days = sequence {
            var d = period.start
            while (d.isBefore(period.endExclusive)) {
                yield(d)
                d = d.plusDays(1)
            }
        }.toList()
        val slotCount = days.size.coerceAtLeast(1)
        val byDay = series.points.associateBy { it.day }

        // X labels (sparse for months)
        val xLabelStep = when {
            period.unit == PeriodUnit.Week -> 1
            days.size <= 10 -> 1
            else -> 5
        }
        days.forEachIndexed { i, day ->
            if (i % xLabelStep != 0 && i != days.lastIndex) return@forEachIndexed
            val x = xForDayIndex(i, slotCount)
            val label = day.format(dayFmt)
            val layout = measurer.measure(label, style = labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x - layout.size.width / 2f,
                    topPad + plotH + with(density) { 6.dp.toPx() },
                ),
            )
        }

        val plotted = days.mapIndexedNotNull { i, day ->
            val pt = byDay[day] ?: return@mapIndexedNotNull null
            Offset(xForDayIndex(i, slotCount), yForValue(pt.value)) to pt
        }
        if (plotted.isEmpty()) return@Canvas

        val linePath = Path()
        plotted.forEachIndexed { i, (offset, _) ->
            if (i == 0) linePath.moveTo(offset.x, offset.y) else linePath.lineTo(offset.x, offset.y)
        }

        val fillPath = Path().apply {
            addPath(linePath)
            val last = plotted.last().first
            val first = plotted.first().first
            lineTo(last.x, topPad + plotH)
            lineTo(first.x, topPad + plotH)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillColor, Color.Transparent),
                startY = topPad,
                endY = topPad + plotH,
            ),
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = with(density) { 2.5.dp.toPx() },
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        plotted.forEach { (offset, _) ->
            drawCircle(color = lineColor, radius = with(density) { 3.5.dp.toPx() }, center = offset)
            drawCircle(
                color = markerCoreColor,
                radius = with(density) { 1.5.dp.toPx() },
                center = offset,
            )
        }
    }
}

private fun deltaTintColor(
    delta: Float?,
    metric: ProgressMetric,
    primary: Color,
    error: Color,
    neutral: Color,
): Color {
    if (delta == null || abs(delta) < 1e-4f) return neutral
    val dir = metric.decreaseIsPositive ?: return neutral
    val improved = if (dir) delta < 0f else delta > 0f
    return if (improved) primary else error
}
