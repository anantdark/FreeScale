package com.anant.freescale.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anant.freescale.ui.theme.PlexMonoFamily
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val TrendGreen = Color(0xFF22C55E)
private val TrendRed = Color(0xFFEF4444)

private enum class StepTrend {
    Improved,
    Worsened,
}

/** Change vs previous point; weight treats a drop as improved (same as Home results). */
private fun stepTrend(prev: Float, curr: Float, metric: ProgressMetric): StepTrend? {
    val delta = curr - prev
    if (abs(delta) < 1e-3f) return null
    val decreaseIsGood = metric.decreaseIsPositive ?: true
    val improved = if (delta < 0f) decreaseIsGood else !decreaseIsGood
    return if (improved) StepTrend.Improved else StepTrend.Worsened
}

private fun StepTrend.color(): Color = when (this) {
    StepTrend.Improved -> TrendGreen
    StepTrend.Worsened -> TrendRed
}
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
            TrendChart(
                series = series,
                lineColor = scheme.primary,
                gridColor = scheme.outlineVariant.copy(alpha = 0.45f),
                labelColor = scheme.onSurfaceVariant,
                fillColor = scheme.primary.copy(alpha = 0.14f),
                markerCoreColor = scheme.surface,
            )
        }
    }
}

@Composable
private fun TrendChart(
    series: ChartSeries,
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
    val dateFmt = remember {
        SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault())
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var scrubIndex by remember(series.points) { mutableStateOf<Int?>(null) }

    val leftPadPx = with(density) { 44.dp.toPx() }
    val rightPadPx = with(density) { 8.dp.toPx() }
    val topPadPx = with(density) { 12.dp.toPx() }
    val bottomPadPx = with(density) { 8.dp.toPx() }

    fun indexForX(x: Float): Int {
        val count = series.points.size
        if (count <= 1) return 0
        val plotW = (canvasSize.width - leftPadPx - rightPadPx).coerceAtLeast(1f)
        val t = ((x - leftPadPx) / plotW).coerceIn(0f, 1f)
        return (t * (count - 1)).roundToInt().coerceIn(0, count - 1)
    }

    fun pointOffset(index: Int): Offset {
        val count = series.points.size
        val plotW = (canvasSize.width - leftPadPx - rightPadPx).coerceAtLeast(1f)
        val plotH = (canvasSize.height - topPadPx - bottomPadPx).coerceAtLeast(1f)
        val yMin = series.yDomain.min
        val yMax = series.yDomain.max
        val yRange = (yMax - yMin).coerceAtLeast(1e-3f)
        val x = if (count <= 1) {
            leftPadPx + plotW / 2f
        } else {
            leftPadPx + plotW * (index.toFloat() / (count - 1).toFloat())
        }
        val v = series.points[index].value
        val yt = ((v - yMin) / yRange).coerceIn(0f, 1f)
        val y = topPadPx + plotH * (1f - yt)
        return Offset(x, y)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .onSizeChanged { canvasSize = it }
            .pointerInput(series.points) {
                detectTapGestures { pos ->
                    scrubIndex = indexForX(pos.x)
                }
            }
            .pointerInput(series.points) {
                detectDragGestures(
                    onDragStart = { pos -> scrubIndex = indexForX(pos.x) },
                    onDrag = { change, _ ->
                        scrubIndex = indexForX(change.position.x)
                        change.consume()
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val leftPad = leftPadPx
            val rightPad = rightPadPx
            val topPad = topPadPx
            val bottomPad = bottomPadPx
            val plotW = size.width - leftPad - rightPad
            val plotH = size.height - topPad - bottomPad
            if (plotW <= 0f || plotH <= 0f) return@Canvas

            val yMin = series.yDomain.min
            val yMax = series.yDomain.max
            val yRange = (yMax - yMin).coerceAtLeast(1e-3f)

            fun xForIndex(index: Int, count: Int): Float {
                if (count <= 1) return leftPad + plotW / 2f
                return leftPad + plotW * (index.toFloat() / (count - 1).toFloat())
            }

            fun yForValue(v: Float): Float {
                val t = ((v - yMin) / yRange).coerceIn(0f, 1f)
                return topPad + plotH * (1f - t)
            }

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

            val count = series.points.size
            val plotted = series.points.mapIndexed { i, pt ->
                Offset(xForIndex(i, count), yForValue(pt.value)) to pt
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

            val strokeWidth = with(density) { 2.5.dp.toPx() }
            for (i in 1 until plotted.size) {
                val from = plotted[i - 1].first
                val to = plotted[i].first
                val segmentColor = stepTrend(
                    series.points[i - 1].value,
                    series.points[i].value,
                    series.metric,
                )?.color() ?: lineColor
                drawLine(
                    color = segmentColor,
                    start = from,
                    end = to,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            plotted.forEachIndexed { i, (offset, _) ->
                val selected = scrubIndex == i
                val outer = with(density) { if (selected) 6.dp.toPx() else 3.5.dp.toPx() }
                val inner = with(density) { if (selected) 2.5.dp.toPx() else 1.5.dp.toPx() }
                if (selected) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.35f),
                        start = Offset(offset.x, topPad),
                        end = Offset(offset.x, topPad + plotH),
                        strokeWidth = with(density) { 1.5.dp.toPx() },
                    )
                }
                drawCircle(color = lineColor, radius = outer, center = offset)
                drawCircle(color = markerCoreColor, radius = inner, center = offset)
            }
        }

        val idx = scrubIndex
        if (idx != null && idx in series.points.indices && canvasSize.width > 0) {
            val pt = series.points[idx]
            val pos = pointOffset(idx)
            val unit = series.metric.unitSuffix.let { if (it.isEmpty()) "" else " $it" }
            val valueText = "${formatMetricValue(pt.value, series.metric)}$unit"
            val whenText = pt.measurement.dateTime?.let { dateFmt.format(it) } ?: "—"
            val prev = series.points.getOrNull(idx - 1)
            val delta = prev?.let { pt.value - it.value }
            val trend = prev?.let { stepTrend(it.value, pt.value, series.metric) }
            val bubbleMaxWidth = with(density) { 220.dp.toPx() }
            val bubbleApproxHeight = with(density) { 68.dp.toPx() }
            val x = (pos.x - bubbleMaxWidth / 2f)
                .coerceIn(0f, (canvasSize.width - bubbleMaxWidth).coerceAtLeast(0f))
            val y = (pos.y - bubbleApproxHeight - with(density) { 10.dp.toPx() })
                .coerceAtLeast(0f)

            Surface(
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        whenText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        valueText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = PlexMonoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (delta != null && trend != null) {
                        val rose = delta > 0f
                        // Direction of change (not health judgment — color carries that).
                        val hint = if (rose) "Increased" else "Decreased"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = if (rose) {
                                    Icons.Filled.ArrowDropUp
                                } else {
                                    Icons.Filled.ArrowDropDown
                                },
                                contentDescription = null,
                                tint = trend.color(),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                "$hint · ${formatDelta(delta, series.metric)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = trend.color(),
                                fontFamily = PlexMonoFamily,
                            )
                        }
                    } else if (idx == 0) {
                        Text(
                            "First reading in range",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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
