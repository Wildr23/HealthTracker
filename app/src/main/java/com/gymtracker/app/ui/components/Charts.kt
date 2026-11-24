package com.gymtracker.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
