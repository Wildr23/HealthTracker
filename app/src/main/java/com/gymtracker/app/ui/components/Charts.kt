package com.gymtracker.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymtracker.app.data.db.BodyWeightEntity
import com.gymtracker.app.data.db.HrSampleEntity
import com.gymtracker.app.data.db.PrType
import com.gymtracker.app.domain.CaffeineAnalysis
import com.gymtracker.app.domain.CaffeineCurvePoint
import com.gymtracker.app.domain.CaffeineSleepStatus
import com.gymtracker.app.domain.MetricDataPoint
import com.gymtracker.app.domain.SessionPrPoint
import com.gymtracker.app.domain.TrainingBreak
import com.gymtracker.app.ui.theme.CoralEnergy
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.HrZoneColors
import com.gymtracker.app.ui.theme.TealAccent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun StrengthProgressionChart(
    points: List<SessionPrPoint>,
    breaks: List<TrainingBreak>,
    compactBreaks: Boolean,
    selectedPoint: SessionPrPoint?,
    onSelectPoint: (SessionPrPoint?) -> Unit,
    goalKg: Double? = null,
    goalLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val surfaceBg = MaterialTheme.colorScheme.surface

    val allValues = points.flatMap { listOfNotNull(it.bestE1rmKg, it.maxWeightKg) }
    if (points.isEmpty() || allValues.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(200.dp))
        return
    }

    val boundValues = if (goalKg != null && goalKg > 0.0) allValues + goalKg else allValues
    val minVal = floor((boundValues.min() - 5.0) / 5.0) * 5.0
    val maxVal = ceil((boundValues.max() + 5.0) / 5.0) * 5.0
    val valSpan = (maxVal - minVal).coerceAtLeast(10.0)

    val minEpoch = points.first().date.toEpochDay().toDouble()
    val maxEpoch = points.last().date.toEpochDay().toDouble()
    val epochSpan = (maxEpoch - minEpoch).coerceAtLeast(1.0)

    val breakSet = breaks.map { it.afterSessionIndex }.toSet()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(points, compactBreaks) {
                detectTapGestures { offset ->
                    val leftPad = 42.dp.toPx()
                    val rightPad = 14.dp.toPx()
                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val xs = points.mapIndexed { idx, p ->
                        val ratio = if (compactBreaks || points.size == 1) {
                            if (points.size == 1) 0.5f else idx.toFloat() / (points.size - 1).toFloat()
                        } else {
                            ((p.date.toEpochDay() - minEpoch) / epochSpan).toFloat()
                        }
                        leftPad + ratio * plotW
                    }
                    val nearestIdx = xs.indices.minByOrNull { abs(xs[it] - offset.x) }
                    if (nearestIdx != null) {
                        val candidate = points[nearestIdx]
                        onSelectPoint(if (selectedPoint?.workoutId == candidate.workoutId) null else candidate)
                    }
                }
            }
    ) {
        val leftPad = 42.dp.toPx()
        val rightPad = 14.dp.toPx()
        val topPad = 12.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        fun xFor(idx: Int, p: SessionPrPoint): Float {
            val ratio = if (compactBreaks || points.size == 1) {
                if (points.size == 1) 0.5f else idx.toFloat() / (points.size - 1).toFloat()
            } else {
                ((p.date.toEpochDay() - minEpoch) / epochSpan).toFloat()
            }
            return leftPad + ratio * plotW
        }

        fun yFor(v: Double): Float {
            val ratio = ((v - minVal) / valSpan).toFloat().coerceIn(0f, 1f)
            return topPad + plotH * (1f - ratio)
        }

        // Grid + Y labels (4 steps)
        val steps = 4
        for (i in 0..steps) {
            val v = minVal + (valSpan * i / steps.toDouble())
            val y = yFor(v)
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width - rightPad, y), strokeWidth = 1.dp.toPx())
            val lbl = v.roundToInt().toString()
            val textRes = measurer.measure(lbl, TextStyle(color = axisColor, fontSize = 10.sp))
            drawText(textRes, topLeft = Offset(4.dp.toPx(), y - textRes.size.height / 2f))
        }

        // Training break shading
        if (compactBreaks) {
            for (b in breaks) {
                val idx = b.afterSessionIndex
                if (idx in 0 until points.lastIndex) {
                    val x1 = xFor(idx, points[idx])
                    val x2 = xFor(idx + 1, points[idx + 1])
                    val mid = (x1 + x2) / 2f
                    drawLine(
                        color = CoralEnergy.copy(alpha = 0.65f),
                        start = Offset(mid, topPad),
                        end = Offset(mid, topPad + plotH),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                }
            }
        } else {
            for (b in breaks) {
                val idx = b.afterSessionIndex
                if (idx in 0 until points.lastIndex) {
                    val x1 = xFor(idx, points[idx])
                    val x2 = xFor(idx + 1, points[idx + 1])
                    drawRect(
                        color = CoralEnergy.copy(alpha = 0.10f),
                        topLeft = Offset(x1, topPad),
                        size = Size((x2 - x1).coerceAtLeast(2f), plotH)
                    )
                }
            }
        }

        // Series 1: Max Weight (Teal)
        val maxWeightPath = Path()
        var startedMaxW = false
        points.forEachIndexed { idx, p ->
            val w = p.maxWeightKg ?: return@forEachIndexed
            val pt = Offset(xFor(idx, p), yFor(w))
            if (!startedMaxW) {
                maxWeightPath.moveTo(pt.x, pt.y)
                startedMaxW = true
            } else {
                if (compactBreaks || idx - 1 !in breakSet) {
                    maxWeightPath.lineTo(pt.x, pt.y)
                } else {
                    maxWeightPath.moveTo(pt.x, pt.y)
                }
            }
        }
        drawPath(maxWeightPath, color = ElectricBlue, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))

        // Series 2: Best e1RM (Gold)
        val e1rmPath = Path()
        var startedE1rm = false
        points.forEachIndexed { idx, p ->
            val e = p.bestE1rmKg ?: return@forEachIndexed
            val pt = Offset(xFor(idx, p), yFor(e))
            if (!startedE1rm) {
                e1rmPath.moveTo(pt.x, pt.y)
                startedE1rm = true
            } else {
                if (compactBreaks || idx - 1 !in breakSet) {
                    e1rmPath.lineTo(pt.x, pt.y)
                } else {
                    e1rmPath.moveTo(pt.x, pt.y)
                }
            }
        }
        drawPath(e1rmPath, color = GoldPr, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round))

        // Dots & PR stars + selection crosshair
        val isDense = points.size > 25
        val regR = if (isDense) 2.dp.toPx() else 3.dp.toPx()
        val prR = if (isDense) 3.2.dp.toPx() else 4.5.dp.toPx()
        val haloR = if (isDense) 5.5.dp.toPx() else 8.dp.toPx()
        points.forEachIndexed { idx, p ->
            val x = xFor(idx, p)
            if (selectedPoint?.workoutId == p.workoutId) {
                drawLine(
                    color = axisColor.copy(alpha = 0.6f),
                    start = Offset(x, topPad),
                    end = Offset(x, topPad + plotH),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
            p.maxWeightKg?.let { w ->
                drawCircle(ElectricBlue, radius = regR, center = Offset(x, yFor(w)))
            }
            p.bestE1rmKg?.let { e ->
                val isPr = PrType.BEST_E1RM in p.prBadges
                val y = yFor(e)
                if (isPr) {
                    drawCircle(GoldPr.copy(alpha = 0.28f), radius = haloR, center = Offset(x, y))
                    drawCircle(GoldPr, radius = prR, center = Offset(x, y))
                } else {
                    drawCircle(GoldPr, radius = regR, center = Offset(x, y))
                }
            }
        }

        // Optional green Goal horizontal reference line matching Jefit Chart
        if (goalKg != null && goalKg > 0.0) {
            val gy = yFor(goalKg)
            drawLine(
                color = EmeraldRest,
                start = Offset(leftPad, gy),
                end = Offset(size.width - rightPad, gy),
                strokeWidth = 2.dp.toPx()
            )
            goalLabel?.takeIf { it.isNotBlank() }?.let { lbl ->
                val txtRes = measurer.measure(lbl, TextStyle(color = EmeraldRest, fontSize = 11.sp))
                val padH = 6.dp.toPx()
                val padV = 2.dp.toPx()
                val pillW = txtRes.size.width + padH * 2f
                val pillH = txtRes.size.height + padV * 2f
                val pillX = (size.width - rightPad - pillW - 4.dp.toPx()).coerceAtLeast(leftPad + 4.dp.toPx())
                val pillY = (gy - pillH / 2f).coerceIn(topPad, topPad + plotH - pillH)
                val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                drawRoundRect(
                    color = surfaceBg.copy(alpha = 0.94f),
                    topLeft = Offset(pillX, pillY),
                    size = Size(pillW, pillH),
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = EmeraldRest.copy(alpha = 0.6f),
                    topLeft = Offset(pillX, pillY),
                    size = Size(pillW, pillH),
                    cornerRadius = radius,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawText(txtRes, topLeft = Offset(pillX + padH, pillY + padV))
            }
        }

        // First & last date labels
        val fmt = DateTimeFormatter.ofPattern("MM/yy")
        val firstLbl = measurer.measure(points.first().date.format(fmt), TextStyle(color = axisColor, fontSize = 10.sp))
        drawText(firstLbl, topLeft = Offset(leftPad, topPad + plotH + 4.dp.toPx()))
        if (points.size > 1) {
            val lastLbl = measurer.measure(points.last().date.format(fmt), TextStyle(color = axisColor, fontSize = 10.sp))
            drawText(lastLbl, topLeft = Offset(size.width - rightPad - lastLbl.size.width, topPad + plotH + 4.dp.toPx()))
        }
    }
}

@Composable
fun VolumeBarChart(
    points: List<SessionPrPoint>,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    if (points.isEmpty()) return

    val maxVol = (points.maxOfOrNull { it.totalVolumeKg } ?: 100.0).coerceAtLeast(100.0)
    Canvas(modifier = modifier.fillMaxWidth().height(150.dp)) {
        val leftPad = 46.dp.toPx()
        val rightPad = 12.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        listOf(0.5, 1.0).forEach { frac ->
            val y = topPad + plotH * (1f - frac.toFloat())
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width - rightPad, y), 1.dp.toPx())
            val txt = measurer.measure("${(maxVol * frac).roundToInt()}", TextStyle(color = axisColor, fontSize = 9.sp))
            drawText(txt, topLeft = Offset(2.dp.toPx(), y - txt.size.height / 2f))
        }

        val barSlot = plotW / points.size.toFloat()
        val barW = (barSlot * 0.72f).coerceIn(2f, 22.dp.toPx())
        points.forEachIndexed { idx, p ->
            val h = ((p.totalVolumeKg / maxVol).toFloat().coerceIn(0f, 1f)) * plotH
            val x = leftPad + idx * barSlot + (barSlot - barW) / 2f
            val color = if (PrType.MAX_SESSION_VOLUME in p.prBadges) GoldPr else ElectricBlue.copy(alpha = 0.85f)
            drawRect(color, topLeft = Offset(x, topPad + plotH - h), size = Size(barW, h))
        }
    }
}

@Composable
fun RelativeStrengthChart(
    ratios: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    if (ratios.isEmpty()) return

    val minR = (ratios.minOf { it.second } - 0.1).coerceAtLeast(0.0)
    val maxR = (ratios.maxOf { it.second } + 0.1).coerceAtLeast(minR + 0.2)
    val span = (maxR - minR).coerceAtLeast(0.1)

    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        val leftPad = 42.dp.toPx()
        val rightPad = 12.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 18.dp.toPx()
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        listOf(minR, (minR + maxR) / 2.0, maxR).forEach { v ->
            val ratio = ((v - minR) / span).toFloat()
            val y = topPad + plotH * (1f - ratio)
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width - rightPad, y), 1.dp.toPx())
            val txt = measurer.measure(String.format("%.2fx", v), TextStyle(color = axisColor, fontSize = 9.sp))
            drawText(txt, topLeft = Offset(2.dp.toPx(), y - txt.size.height / 2f))
        }

        val path = Path()
        ratios.forEachIndexed { idx, (_, r) ->
            val xr = if (ratios.size == 1) 0.5f else idx.toFloat() / (ratios.size - 1).toFloat()
            val yr = ((r - minR) / span).toFloat().coerceIn(0f, 1f)
            val pt = Offset(leftPad + xr * plotW, topPad + plotH * (1f - yr))
            if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            drawCircle(CoralEnergy, 3.dp.toPx(), pt)
        }
        drawPath(path, CoralEnergy, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

data class ChartExerciseBlock(
    val start: Instant,
    val end: Instant,
    val label: String
)

@Composable
fun WorkoutHrChart(
    samples: List<HrSampleEntity>,
    setTimes: List<Instant>,
    blocks: List<ChartExerciseBlock>,
    hrMax: Int,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    if (samples.isEmpty()) return

    val sorted = samples.sortedBy { it.time }
    val minT = minOf(sorted.first().time, setTimes.minOrNull() ?: sorted.first().time)
    val maxT = maxOf(sorted.last().time, setTimes.maxOrNull() ?: sorted.last().time)
    val spanSec = Duration.between(minT, maxT).seconds.coerceAtLeast(60L).toFloat()

    val minHr = max(55, (sorted.minOf { it.bpm } - 10) / 10 * 10)
    val maxHr = ((maxOf(sorted.maxOf { it.bpm } + 10, (hrMax * 0.85).roundToInt())) / 10 + 1) * 10
    val hrSpan = (maxHr - minHr).coerceAtLeast(30).toFloat()

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        val leftPad = 36.dp.toPx()
        val rightPad = 12.dp.toPx()
        val topPad = 14.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        fun xFor(t: Instant): Float {
            val sec = Duration.between(minT, t).seconds.toFloat()
            return leftPad + (sec / spanSec).coerceIn(0f, 1f) * plotW
        }

        fun yFor(bpm: Int): Float {
            val ratio = ((bpm - minHr).toFloat() / hrSpan).coerceIn(0f, 1f)
            return topPad + plotH * (1f - ratio)
        }

        // Zone background bands
        val zoneBounds = listOf(0.50, 0.60, 0.70, 0.80, 0.90, 1.05)
        for (z in 0..4) {
            val zLow = (hrMax * zoneBounds[z]).roundToInt().coerceIn(minHr, maxHr)
            val zHigh = (hrMax * zoneBounds[z + 1]).roundToInt().coerceIn(minHr, maxHr)
            if (zHigh > zLow) {
                val yTop = yFor(zHigh)
                val yBot = yFor(zLow)
                drawRect(
                    color = HrZoneColors[z].copy(alpha = 0.08f),
                    topLeft = Offset(leftPad, yTop),
                    size = Size(plotW, (yBot - yTop).coerceAtLeast(1f))
                )
            }
        }

        // Shaded exercise blocks (alternating tint)
        blocks.forEachIndexed { idx, blk ->
            val x1 = xFor(blk.start)
            val x2 = xFor(blk.end)
            if (x2 > x1) {
                drawRect(
                    color = if (idx % 2 == 0) Color.White.copy(alpha = 0.04f) else TealAccent.copy(alpha = 0.05f),
                    topLeft = Offset(x1, topPad),
                    size = Size(x2 - x1, plotH)
                )
            }
        }

        // Set completion vertical markers
        for (st in setTimes) {
            val x = xFor(st)
            drawLine(
                color = GoldPr.copy(alpha = 0.45f),
                start = Offset(x, topPad),
                end = Offset(x, topPad + plotH),
                strokeWidth = 1.dp.toPx()
            )
        }

        // HR labels
        listOf(minHr, (minHr + maxHr) / 2, maxHr).forEach { bpm ->
            val y = yFor(bpm)
            val lbl = measurer.measure("$bpm", TextStyle(color = axisColor, fontSize = 9.sp))
            drawText(lbl, topLeft = Offset(2.dp.toPx(), y - lbl.size.height / 2f))
        }

        // HR smooth polyline
        val path = Path()
        sorted.forEachIndexed { idx, s ->
            val pt = Offset(xFor(s.time), yFor(s.bpm))
            if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }
        drawPath(path, CoralEnergy, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun BodyWeightChart(
    entries: List<BodyWeightEntity>,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val sorted = entries.sortedBy { it.date }
    if (sorted.isEmpty()) return

    val valid = sorted.filter { !it.flaggedOutlier }.ifEmpty { sorted }
    val minKg = floor((valid.minOf { it.kg } - 3.0) / 5.0) * 5.0
    val maxKg = ceil((valid.maxOf { it.kg } + 3.0) / 5.0) * 5.0
    val span = (maxKg - minKg).coerceAtLeast(10.0)

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val leftPad = 38.dp.toPx()
        val rightPad = 12.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        listOf(minKg, (minKg + maxKg) / 2.0, maxKg).forEach { v ->
            val y = topPad + plotH * (1f - ((v - minKg) / span).toFloat().coerceIn(0f, 1f))
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width - rightPad, y), 1.dp.toPx())
            val txt = measurer.measure("${v.roundToInt()}", TextStyle(color = axisColor, fontSize = 9.sp))
            drawText(txt, topLeft = Offset(2.dp.toPx(), y - txt.size.height / 2f))
        }

        val validPoints = mutableListOf<Offset>()
        sorted.forEachIndexed { idx, bw ->
            val xr = if (sorted.size == 1) 0.5f else idx.toFloat() / (sorted.size - 1).toFloat()
            val yr = ((bw.kg - minKg) / span).toFloat().coerceIn(0f, 1f)
            val pt = Offset(leftPad + xr * plotW, topPad + plotH * (1f - yr))
            if (bw.flaggedOutlier) {
                drawCircle(CoralEnergy, radius = 4.5.dp.toPx(), center = pt, style = Stroke(2.dp.toPx()))
            } else {
                validPoints += pt
            }
        }
        drawPath(validPoints.foldIndexed(Path()) { idx, acc, pt -> acc.apply { if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) } }, ElectricBlue, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        validPoints.forEach { drawCircle(ElectricBlue, radius = 3.5.dp.toPx(), center = it) }
    }
}

@Composable
fun BodyCompositionMetricChart(
    points: List<MetricDataPoint>,
    accentColor: Color,
    selectedPoint: MetricDataPoint?,
    onSelectPoint: (MetricDataPoint?) -> Unit,
    showWeightOverlay: Boolean = false,
    formatAxisValue: (Double) -> String = { v ->
        if (abs(v - v.roundToInt()) < 0.05) "${v.roundToInt()}" else String.format("%.1f", v)
    },
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    if (points.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(180.dp))
        return
    }

    val rawMin = points.minOf { it.value }
    val rawMax = points.maxOf { it.value }
    val rawSpan = (rawMax - rawMin).let { if (it < 0.2) 2.0 else it }
    val pad = rawSpan * 0.15
    val minVal = rawMin - pad
    val maxVal = rawMax + pad
    val span = (maxVal - minVal).coerceAtLeast(0.5)

    val overlayWeights = if (showWeightOverlay) points.mapNotNull { it.weightOverlayKg } else emptyList()
    val minW = overlayWeights.minOrNull()?.let { it - 1.5 } ?: 0.0
    val maxW = overlayWeights.maxOrNull()?.let { it + 1.5 } ?: 1.0
    val wSpan = (maxW - minW).coerceAtLeast(2.0)

    val minEpoch = points.first().date.toEpochDay().toDouble()
    val maxEpoch = points.last().date.toEpochDay().toDouble()
    val epochSpan = (maxEpoch - minEpoch).coerceAtLeast(1.0)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val leftPad = 42.dp.toPx()
                    val rightPad = 14.dp.toPx()
                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val xs = points.map { p ->
                        val ratio = if (points.size == 1) 0.5f else ((p.date.toEpochDay() - minEpoch) / epochSpan).toFloat()
                        leftPad + ratio * plotW
                    }
                    val nearestIdx = xs.indices.minByOrNull { abs(xs[it] - offset.x) }
                    if (nearestIdx != null) {
                        val cand = points[nearestIdx]
                        onSelectPoint(if (selectedPoint?.date == cand.date) null else cand)
                    }
                }
            }
    ) {
        val leftPad = 42.dp.toPx()
        val rightPad = 14.dp.toPx()
        val topPad = 12.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        fun xFor(p: MetricDataPoint): Float {
            val ratio = if (points.size == 1) 0.5f else ((p.date.toEpochDay() - minEpoch) / epochSpan).toFloat()
            return leftPad + ratio * plotW
        }

        fun yFor(v: Double): Float {
            val ratio = ((v - minVal) / span).toFloat().coerceIn(0f, 1f)
            return topPad + plotH * (1f - ratio)
        }

        // Grid + Y-axis labels
        val steps = 3
        for (i in 0..steps) {
            val v = minVal + (span * i / steps.toDouble())
            val y = yFor(v)
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width - rightPad, y), 1.dp.toPx())
            val lbl = measurer.measure(formatAxisValue(v), TextStyle(color = axisColor, fontSize = 10.sp))
            drawText(lbl, topLeft = Offset(2.dp.toPx(), y - lbl.size.height / 2f))
        }

        // Optional dashed weight overlay (normalized)
        if (showWeightOverlay && overlayWeights.size >= 2) {
            val wPath = Path()
            var startedW = false
            points.forEach { p ->
                val w = p.weightOverlayKg ?: return@forEach
                val x = xFor(p)
                val y = topPad + plotH * (1f - ((w - minW) / wSpan).toFloat().coerceIn(0f, 1f))
                if (!startedW) {
                    wPath.moveTo(x, y)
                    startedW = true
                } else {
                    wPath.lineTo(x, y)
                }
            }
            drawPath(
                path = wPath,
                color = axisColor.copy(alpha = 0.55f),
                style = Stroke(
                    width = 1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            )
        }

        // Primary curve + vertical gradient fill
        val coords = points.map { p -> Offset(xFor(p), yFor(p.value)) }
        if (coords.size >= 2) {
            val linePath = Path()
            val fillPath = Path()
            coords.forEachIndexed { idx, pt ->
                if (idx == 0) {
                    linePath.moveTo(pt.x, pt.y)
                    fillPath.moveTo(pt.x, topPad + plotH)
                    fillPath.lineTo(pt.x, pt.y)
                } else {
                    linePath.lineTo(pt.x, pt.y)
                    fillPath.lineTo(pt.x, pt.y)
                }
            }
            fillPath.lineTo(coords.last().x, topPad + plotH)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.28f), accentColor.copy(alpha = 0.02f)),
                    startY = topPad,
                    endY = topPad + plotH
                )
            )
            drawPath(linePath, accentColor, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round))
        }

        // Dots & selection crosshair
        val regR = if (points.size > 30) 2.2.dp.toPx() else 3.5.dp.toPx()
        points.forEachIndexed { idx, p ->
            val c = coords[idx]
            val isSelected = selectedPoint?.date == p.date
            if (isSelected) {
                drawLine(
                    color = axisColor.copy(alpha = 0.65f),
                    start = Offset(c.x, topPad),
                    end = Offset(c.x, topPad + plotH),
                    strokeWidth = 1.4.dp.toPx()
                )
                drawCircle(accentColor.copy(alpha = 0.28f), radius = 8.dp.toPx(), center = c)
                drawCircle(accentColor, radius = 4.8.dp.toPx(), center = c)
            } else {
                drawCircle(accentColor, radius = regR, center = c)
            }
        }

        // First & last date labels
        val fmt = DateTimeFormatter.ofPattern("dd.MM")
        val firstLbl = measurer.measure(points.first().date.format(fmt), TextStyle(color = axisColor, fontSize = 10.sp))
        drawText(firstLbl, topLeft = Offset(leftPad, topPad + plotH + 4.dp.toPx()))
        if (points.size > 1) {
            val lastLbl = measurer.measure(points.last().date.format(fmt), TextStyle(color = axisColor, fontSize = 10.sp))
            drawText(lastLbl, topLeft = Offset(size.width - rightPad - lastLbl.size.width, topPad + plotH + 4.dp.toPx()))
        }
    }
}

@Composable
fun WatchDailyBarChart(
    dates: List<LocalDate>,
    values: List<Double>,
    goalValue: Double?,
    accentColor: Color,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    if (dates.isEmpty() || values.isEmpty() || dates.size != values.size) {
        Box(modifier = modifier.fillMaxWidth().height(170.dp))
        return
    }

    val maxData = values.maxOrNull() ?: 0.0
    val maxBound = maxOf(maxData, goalValue ?: 0.0, 1.0) * 1.12

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(185.dp)
            .pointerInput(values) {
                detectTapGestures { offset ->
                    val leftPad = 38.dp.toPx()
                    val rightPad = 10.dp.toPx()
                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val slotW = plotW / values.size.toFloat()
                    val tappedIdx = ((offset.x - leftPad) / slotW).toInt().coerceIn(0, values.lastIndex)
                    onSelectIndex(if (selectedIndex == tappedIdx) null else tappedIdx)
                }
            }
    ) {
        val leftPad = 38.dp.toPx()
        val rightPad = 10.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        fun yFor(v: Double): Float {
            val ratio = (v / maxBound).toFloat().coerceIn(0f, 1f)
            return topPad + plotH * (1f - ratio)
        }

        // Grid + Y labels
        listOf(0.0, maxBound * 0.5, maxBound).forEach { v ->
            val y = yFor(v)
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width - rightPad, y), 1.dp.toPx())
            val labelStr = if (maxBound >= 1000.0) {
                if (v < 1.0) "0" else String.format("%.1fk", v / 1000.0)
            } else if (maxBound >= 15.0) {
                "${v.roundToInt()}"
            } else {
                String.format("%.1f", v)
            }
            val txt = measurer.measure(labelStr, TextStyle(color = axisColor, fontSize = 9.sp))
            drawText(txt, topLeft = Offset(2.dp.toPx(), y - txt.size.height / 2f))
        }

        val slotW = plotW / values.size.toFloat()
        val barW = (slotW * 0.62f).coerceIn(4.dp.toPx(), 24.dp.toPx())
        val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        values.forEachIndexed { idx, v ->
            val xCenter = leftPad + slotW * (idx + 0.5f)
            val barTop = yFor(v)
            val barH = (topPad + plotH - barTop).coerceAtLeast(if (v > 0.0) 3.dp.toPx() else 0f)
            val metGoal = goalValue != null && goalValue > 0.0 && v >= goalValue
            val baseColor = if (metGoal) EmeraldRest else accentColor
            val alpha = when {
                selectedIndex == null -> 0.88f
                selectedIndex == idx -> 1f
                else -> 0.42f
            }
            if (barH > 0f) {
                drawRoundRect(
                    color = baseColor.copy(alpha = alpha),
                    topLeft = Offset(xCenter - barW / 2f, topPad + plotH - barH),
                    size = Size(barW, barH),
                    cornerRadius = radius
                )
            }
        }

        // Goal dashed horizontal line
        if (goalValue != null && goalValue > 0.0 && goalValue <= maxBound) {
            val gy = yFor(goalValue)
            drawLine(
                color = EmeraldRest.copy(alpha = 0.85f),
                start = Offset(leftPad, gy),
                end = Offset(size.width - rightPad, gy),
                strokeWidth = 1.6.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )
        }

        val fmt = DateTimeFormatter.ofPattern("dd.MM")
        val firstLbl = measurer.measure(dates.first().format(fmt), TextStyle(color = axisColor, fontSize = 9.sp))
        drawText(firstLbl, topLeft = Offset(leftPad, topPad + plotH + 4.dp.toPx()))
        if (dates.size > 1) {
            val lastLbl = measurer.measure(dates.last().format(fmt), TextStyle(color = axisColor, fontSize = 9.sp))
            drawText(lastLbl, topLeft = Offset(size.width - rightPad - lastLbl.size.width, topPad + plotH + 4.dp.toPx()))
        }
    }
}

@Composable
fun CaffeineDecayChart(
    analysis: CaffeineAnalysis,
    now: Instant,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val pts = analysis.curvePoints
    if (pts.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(155.dp))
        return
    }

    val accentColor = when (analysis.status) {
        CaffeineSleepStatus.SAFE -> ElectricBlue
        CaffeineSleepStatus.MODERATE -> GoldPr
        CaffeineSleepStatus.HIGH -> CoralEnergy
    }

    var selectedIdx by remember(pts) { mutableStateOf<Int?>(null) }

    val maxMg = maxOf(
        pts.maxOf { it.mg },
        analysis.sleepThresholdMg.toDouble() * 1.4,
        60.0
    ) * 1.1
    val startEpoch = pts.first().instant.epochSecond.toDouble()
    val endEpoch = pts.last().instant.epochSecond.toDouble()
    val spanEpoch = (endEpoch - startEpoch).coerceAtLeast(3600.0)
    val zone = ZoneId.systemDefault()
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(165.dp)
            .pointerInput(pts) {
                detectTapGestures { offset ->
                    val leftPad = 36.dp.toPx()
                    val rightPad = 12.dp.toPx()
                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val ratio = ((offset.x - leftPad) / plotW).coerceIn(0f, 1f)
                    val idx = (ratio * (pts.size - 1)).roundToInt().coerceIn(0, pts.lastIndex)
                    selectedIdx = if (selectedIdx == idx) null else idx
                }
            }
    ) {
        val leftPad = 36.dp.toPx()
        val rightPad = 12.dp.toPx()
        val topPad = 14.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        fun xFor(t: Instant): Float {
            val r = ((t.epochSecond - startEpoch) / spanEpoch).toFloat().coerceIn(0f, 1f)
            return leftPad + plotW * r
        }

        fun yFor(mg: Double): Float {
            val r = (mg / maxMg).toFloat().coerceIn(0f, 1f)
            return topPad + plotH * (1f - r)
        }

        // Horizontal Y grid
        listOf(0.0, maxMg * 0.5, maxMg).forEach { mgVal ->
            val y = yFor(mgVal)
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width - rightPad, y), 1.dp.toPx())
            val lbl = measurer.measure("${mgVal.roundToInt()}", TextStyle(color = axisColor, fontSize = 9.sp))
            drawText(lbl, topLeft = Offset(2.dp.toPx(), y - lbl.size.height / 2f))
        }

        // Sleep threshold (50 mg) horizontal emerald dashed line
        val threshY = yFor(analysis.sleepThresholdMg.toDouble())
        drawLine(
            color = EmeraldRest.copy(alpha = 0.85f),
            start = Offset(leftPad, threshY),
            end = Offset(size.width - rightPad, threshY),
            strokeWidth = 1.4.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        )

        // Vertical Bedtime dashed line
        if (!analysis.bedtimeInstant.isBefore(pts.first().instant) &&
            !analysis.bedtimeInstant.isAfter(pts.last().instant)
        ) {
            val bx = xFor(analysis.bedtimeInstant)
            drawLine(
                color = EmeraldRest.copy(alpha = 0.7f),
                start = Offset(bx, topPad),
                end = Offset(bx, topPad + plotH),
                strokeWidth = 1.4.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
            val bedTxt = measurer.measure(
                analysis.bedtimeInstant.atZone(zone).toLocalTime().format(timeFmt),
                TextStyle(color = EmeraldRest, fontSize = 9.sp)
            )
            drawText(
                bedTxt,
                topLeft = Offset((bx - bedTxt.size.width / 2f).coerceIn(leftPad, size.width - rightPad - bedTxt.size.width), 0f)
            )
        }

        // Curve + Gradient fill
        val coords = pts.map { Offset(xFor(it.instant), yFor(it.mg)) }
        val linePath = Path()
        val fillPath = Path()
        coords.forEachIndexed { idx, c ->
            if (idx == 0) {
                linePath.moveTo(c.x, c.y)
                fillPath.moveTo(c.x, topPad + plotH)
                fillPath.lineTo(c.x, c.y)
            } else {
                linePath.lineTo(c.x, c.y)
                fillPath.lineTo(c.x, c.y)
            }
        }
        fillPath.lineTo(coords.last().x, topPad + plotH)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(accentColor.copy(alpha = 0.30f), accentColor.copy(alpha = 0.02f)),
                startY = topPad,
                endY = topPad + plotH
            )
        )
        drawPath(linePath, accentColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        // Current "Now" dot
        val nowX = xFor(now)
        val nowY = yFor(analysis.activeMgNow)
        drawCircle(accentColor.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = Offset(nowX, nowY))
        drawCircle(accentColor, radius = 4.dp.toPx(), center = Offset(nowX, nowY))

        // Selection crosshair
        selectedIdx?.let { idx ->
            val p = pts[idx]
            val c = coords[idx]
            drawLine(axisColor.copy(alpha = 0.6f), Offset(c.x, topPad), Offset(c.x, topPad + plotH), 1.2.dp.toPx())
            drawCircle(Color.White, radius = 4.5.dp.toPx(), center = c)
            val tipText = "${p.instant.atZone(zone).toLocalTime().format(timeFmt)} • ${p.mg.roundToInt()} mg"
            val tip = measurer.measure(tipText, TextStyle(color = Color.White, fontSize = 10.sp))
            val tx = (c.x - tip.size.width / 2f).coerceIn(leftPad, size.width - rightPad - tip.size.width)
            drawText(tip, topLeft = Offset(tx, 0f))
        }

        // X axis first / now / last time labels
        val firstLbl = measurer.measure(pts.first().instant.atZone(zone).toLocalTime().format(timeFmt), TextStyle(color = axisColor, fontSize = 9.sp))
        drawText(firstLbl, topLeft = Offset(leftPad, topPad + plotH + 4.dp.toPx()))
        val lastLbl = measurer.measure(pts.last().instant.atZone(zone).toLocalTime().format(timeFmt), TextStyle(color = axisColor, fontSize = 9.sp))
        drawText(lastLbl, topLeft = Offset(size.width - rightPad - lastLbl.size.width, topPad + plotH + 4.dp.toPx()))
    }
}
