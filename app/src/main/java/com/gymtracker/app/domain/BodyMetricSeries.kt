package com.gymtracker.app.domain

import com.gymtracker.app.data.db.BodyWeightEntity
import com.gymtracker.app.data.scale.ScaleReadingSnapshot
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

enum class BodyCompositionMetric {
    WEIGHT_KG,
    FAT_PERCENT,
    MUSCLE_MASS_KG,
    LEAN_BODY_MASS_KG,
    WATER_PERCENT,
    PROTEIN_PERCENT,
    VISCERAL_FAT,
    BONE_MASS_KG,
    BMI,
    BMR_KCAL,
    METABOLIC_AGE
}

enum class BodyMetricRange(val days: Long?) {
    D14(14),
    M1(30),
    M3(90),
    M6(180),
    Y1(365),
    ALL(null)
}

data class MetricDataPoint(
    val date: LocalDate,
    val value: Double,
    val weightOverlayKg: Double? = null
)

data class BodyMetricSeriesResult(
    val metric: BodyCompositionMetric,
    val points: List<MetricDataPoint>,
    val latest: Double?,
    val delta: Double?,
    val min: Double?,
    val max: Double?,
    val average: Double?,
    val isDeltaGood: Boolean?
)

object BodyMetricSeries {

    fun buildSeries(
        metric: BodyCompositionMetric,
        bodyWeights: List<BodyWeightEntity>,
        scaleReadingsByDate: Map<LocalDate, ScaleReadingSnapshot>,
        range: BodyMetricRange,
        heightCm: Int,
        today: LocalDate = LocalDate.now()
    ): BodyMetricSeriesResult {
        val validBwByDate = bodyWeights
            .filter { !it.flaggedOutlier && it.deletedAt == null }
            .sortedBy { it.updatedAt }
            .associateBy { it.date }

        val cutoffDate = range.days?.let { today.minusDays(it) }
        val heightM = (heightCm.coerceIn(100, 240)) / 100.0

        val allDates = (validBwByDate.keys + scaleReadingsByDate.keys)
            .distinct()
            .filter { d -> cutoffDate == null || !d.isBefore(cutoffDate) }
            .sorted()

        val points = allDates.mapNotNull { d ->
            val bw = validBwByDate[d]
            val scale = scaleReadingsByDate[d]
            val weightOnDay = scale?.weightKg ?: bw?.kg
            val value = when (metric) {
                BodyCompositionMetric.WEIGHT_KG -> weightOnDay
                BodyCompositionMetric.BMI -> scale?.bmi ?: bw?.kg?.let { w -> round1(w / (heightM * heightM)) }
                BodyCompositionMetric.FAT_PERCENT -> scale?.fatPercent ?: bw?.fatPercent
                BodyCompositionMetric.MUSCLE_MASS_KG -> scale?.muscleMassKg
                BodyCompositionMetric.LEAN_BODY_MASS_KG -> scale?.leanBodyMassKg
                BodyCompositionMetric.WATER_PERCENT -> scale?.waterPercent
                BodyCompositionMetric.PROTEIN_PERCENT -> scale?.proteinPercent
                BodyCompositionMetric.VISCERAL_FAT -> scale?.visceralFat?.toDouble()
                BodyCompositionMetric.BONE_MASS_KG -> scale?.boneMassKg
                BodyCompositionMetric.BMR_KCAL -> scale?.bmrKcal?.toDouble()
                BodyCompositionMetric.METABOLIC_AGE -> scale?.metabolicAge?.toDouble()
            } ?: return@mapNotNull null

            MetricDataPoint(
                date = d,
                value = value,
                weightOverlayKg = weightOnDay
            )
        }

        val latest = points.lastOrNull()?.value
        val delta = if (points.size >= 2) {
            round2(points.last().value - points.first().value)
        } else null
        val min = points.minOfOrNull { it.value }
        val max = points.maxOfOrNull { it.value }
        val avg = points.takeIf { it.isNotEmpty() }?.let { list ->
            round2(list.sumOf { it.value } / list.size.toDouble())
        }

        val isDeltaGood = if (delta == null || abs(delta) < 0.01) {
            null
        } else {
            when (metric) {
                BodyCompositionMetric.FAT_PERCENT,
                BodyCompositionMetric.VISCERAL_FAT,
                BodyCompositionMetric.METABOLIC_AGE -> delta < 0.0
                BodyCompositionMetric.MUSCLE_MASS_KG,
                BodyCompositionMetric.LEAN_BODY_MASS_KG,
                BodyCompositionMetric.WATER_PERCENT,
                BodyCompositionMetric.PROTEIN_PERCENT,
                BodyCompositionMetric.BONE_MASS_KG -> delta > 0.0
                BodyCompositionMetric.WEIGHT_KG,
                BodyCompositionMetric.BMI,
                BodyCompositionMetric.BMR_KCAL -> null
            }
        }

        return BodyMetricSeriesResult(
            metric = metric,
            points = points,
            latest = latest,
            delta = delta,
            min = min,
            max = max,
            average = avg,
            isDeltaGood = isDeltaGood
        )
    }

    private fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0
    private fun round2(v: Double): Double = (v * 100.0).roundToInt() / 100.0
}
