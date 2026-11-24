package com.gymtracker.app.domain

import com.gymtracker.app.data.db.BodyWeightEntity
import com.gymtracker.app.data.db.DataSource
import com.gymtracker.app.data.scale.ScaleReadingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BodyMetricSeriesTest {

    private val today = LocalDate.of(2026, 5, 1)

    private fun bw(
        daysAgo: Long,
        kg: Double,
        fatPercent: Double? = null,
        flaggedOutlier: Boolean = false,
        deletedAt: Instant? = null
    ) = BodyWeightEntity(
        date = today.minusDays(daysAgo),
        kg = kg,
        source = DataSource.APP,
        fatPercent = fatPercent,
        flaggedOutlier = flaggedOutlier,
        updatedAt = Instant.now(),
        deletedAt = deletedAt
    )

    private fun snapshot(
        daysAgo: Long,
        weightKg: Double,
        bmi: Double = 25.0,
        bmrKcal: Int = 1900,
        visceralFat: Int = 10,
        fatPercent: Double? = 20.0,
        leanBodyMassKg: Double? = 68.0,
        waterPercent: Double? = 58.0,
        muscleMassKg: Double? = 64.5,
        boneMassKg: Double? = 3.5,
        proteinPercent: Double? = 18.0,
        metabolicAge: Int? = 30
    ): Pair<LocalDate, ScaleReadingSnapshot> {
        val date = today.minusDays(daysAgo)
        return date to ScaleReadingSnapshot(
            dateIso = date.toString(),
            capturedAtEpochSec = 1700000000L - daysAgo * 86400L,
            weightKg = weightKg,
            impedanceOhm = 480,
            bmi = bmi,
            idealWeightKg = 74.0,
            bmrKcal = bmrKcal,
            visceralFat = visceralFat,
            fatPercent = fatPercent,
            leanBodyMassKg = leanBodyMassKg,
            waterPercent = waterPercent,
            muscleMassKg = muscleMassKg,
            boneMassKg = boneMassKg,
            proteinPercent = proteinPercent,
            metabolicAge = metabolicAge
        )
    }

    @Test
    fun allElevenMetrics_buildPointsAndWeightOverlay() {
        val scales = mapOf(
            snapshot(
                daysAgo = 10,
                weightKg = 90.0,
                bmi = 26.8,
                bmrKcal = 1950,
                visceralFat = 12,
                fatPercent = 24.0,
                leanBodyMassKg = 68.4,
                waterPercent = 54.0,
                muscleMassKg = 64.8,
                boneMassKg = 3.6,
                proteinPercent = 16.5,
                metabolicAge = 34
            ),
            snapshot(
                daysAgo = 0,
                weightKg = 88.0,
                bmi = 26.2,
                bmrKcal = 1920,
                visceralFat = 11,
                fatPercent = 22.0,
                leanBodyMassKg = 69.0,
                waterPercent = 55.5,
                muscleMassKg = 65.4,
                boneMassKg = 3.7,
                proteinPercent = 17.5,
                metabolicAge = 32
            )
        )

        BodyCompositionMetric.entries.forEach { metric ->
            val result = BodyMetricSeries.buildSeries(
                metric = metric,
                bodyWeights = emptyList(),
                scaleReadingsByDate = scales,
                range = BodyMetricRange.M1,
                heightCm = 183,
                today = today
            )
            assertEquals("Expected 2 points for $metric", 2, result.points.size)
            assertEquals(90.0, result.points[0].weightOverlayKg!!, 0.001)
            assertEquals(88.0, result.points[1].weightOverlayKg!!, 0.001)
            assertNotNull("Expected latest value for $metric", result.latest)
            assertNotNull("Expected delta for $metric", result.delta)
        }
    }

    @Test
    fun ranges_filterPointsByCutoff() {
        val bws = listOf(
            bw(daysAgo = 400, kg = 95.0),
            bw(daysAgo = 200, kg = 93.0),
            bw(daysAgo = 100, kg = 91.0),
            bw(daysAgo = 40, kg = 89.0),
            bw(daysAgo = 20, kg = 88.0),
            bw(daysAgo = 5, kg = 87.0)
        )

        fun countFor(range: BodyMetricRange) = BodyMetricSeries.buildSeries(
            metric = BodyCompositionMetric.WEIGHT_KG,
            bodyWeights = bws,
            scaleReadingsByDate = emptyMap(),
            range = range,
            heightCm = 183,
            today = today
        ).points.size

        assertEquals(1, countFor(BodyMetricRange.D14))
        assertEquals(2, countFor(BodyMetricRange.M1))
        assertEquals(3, countFor(BodyMetricRange.M3))
        assertEquals(4, countFor(BodyMetricRange.M6))
        assertEquals(5, countFor(BodyMetricRange.Y1))
        assertEquals(6, countFor(BodyMetricRange.ALL))
    }

    @Test
    fun flaggedOutliersAndDeletedEntries_areExcluded_whileScaleSnapshotStillFills() {
        val bws = listOf(
            bw(daysAgo = 5, kg = 150.0, fatPercent = 40.0, flaggedOutlier = true),
            bw(daysAgo = 3, kg = 88.0, fatPercent = 21.0, deletedAt = Instant.now()),
            bw(daysAgo = 1, kg = 86.5, fatPercent = 20.0)
        )

        val resWeight = BodyMetricSeries.buildSeries(
            metric = BodyCompositionMetric.WEIGHT_KG,
            bodyWeights = bws,
            scaleReadingsByDate = emptyMap(),
            range = BodyMetricRange.M1,
            heightCm = 183,
            today = today
        )
        assertEquals(1, resWeight.points.size)
        assertEquals(86.5, resWeight.latest!!, 0.001)

        // Fallback BMI computed from valid BodyWeightEntity
        val resBmi = BodyMetricSeries.buildSeries(
            metric = BodyCompositionMetric.BMI,
            bodyWeights = bws,
            scaleReadingsByDate = emptyMap(),
            range = BodyMetricRange.M1,
            heightCm = 183,
            today = today
        )
        assertEquals(1, resBmi.points.size)
        assertEquals(25.8, resBmi.latest!!, 0.1)
    }

    @Test
    fun isDeltaGood_reflectsSignPerMetricSemantics() {
        fun deltaGoodFor(metric: BodyCompositionMetric, first: Double, last: Double): Boolean? {
            fun make(daysAgo: Long, v: Double): Pair<LocalDate, ScaleReadingSnapshot> = when (metric) {
                BodyCompositionMetric.WEIGHT_KG -> snapshot(daysAgo, weightKg = v)
                BodyCompositionMetric.BMI -> snapshot(daysAgo, weightKg = 85.0, bmi = v)
                BodyCompositionMetric.BMR_KCAL -> snapshot(daysAgo, weightKg = 85.0, bmrKcal = v.toInt())
                BodyCompositionMetric.FAT_PERCENT -> snapshot(daysAgo, weightKg = 85.0, fatPercent = v)
                BodyCompositionMetric.VISCERAL_FAT -> snapshot(daysAgo, weightKg = 85.0, visceralFat = v.toInt())
                BodyCompositionMetric.METABOLIC_AGE -> snapshot(daysAgo, weightKg = 85.0, metabolicAge = v.toInt())
                BodyCompositionMetric.MUSCLE_MASS_KG -> snapshot(daysAgo, weightKg = 85.0, muscleMassKg = v)
                BodyCompositionMetric.LEAN_BODY_MASS_KG -> snapshot(daysAgo, weightKg = 85.0, leanBodyMassKg = v)
                BodyCompositionMetric.WATER_PERCENT -> snapshot(daysAgo, weightKg = 85.0, waterPercent = v)
                BodyCompositionMetric.PROTEIN_PERCENT -> snapshot(daysAgo, weightKg = 85.0, proteinPercent = v)
                BodyCompositionMetric.BONE_MASS_KG -> snapshot(daysAgo, weightKg = 85.0, boneMassKg = v)
            }

            return BodyMetricSeries.buildSeries(
                metric = metric,
                bodyWeights = emptyList(),
                scaleReadingsByDate = mapOf(make(10, first), make(0, last)),
                range = BodyMetricRange.M1,
                heightCm = 183,
                today = today
            ).isDeltaGood
        }

        // Decrease is good; increase is bad
        for (metric in listOf(
            BodyCompositionMetric.FAT_PERCENT,
            BodyCompositionMetric.VISCERAL_FAT,
            BodyCompositionMetric.METABOLIC_AGE
        )) {
            assertTrue("$metric down should be good", deltaGoodFor(metric, 25.0, 22.0) == true)
            assertFalse("$metric up should be bad", deltaGoodFor(metric, 22.0, 25.0) == true)
            assertEquals(false, deltaGoodFor(metric, 22.0, 25.0))
            assertNull("$metric flat should be null", deltaGoodFor(metric, 22.0, 22.0))
        }

        // Increase is good; decrease is bad
        for (metric in listOf(
            BodyCompositionMetric.MUSCLE_MASS_KG,
            BodyCompositionMetric.LEAN_BODY_MASS_KG,
            BodyCompositionMetric.WATER_PERCENT,
            BodyCompositionMetric.PROTEIN_PERCENT,
            BodyCompositionMetric.BONE_MASS_KG
        )) {
            assertTrue("$metric up should be good", deltaGoodFor(metric, 50.0, 53.0) == true)
            assertEquals(false, deltaGoodFor(metric, 53.0, 50.0))
            assertNull("$metric flat should be null", deltaGoodFor(metric, 50.0, 50.0))
        }

        // Neutral regardless of direction
        for (metric in listOf(
            BodyCompositionMetric.WEIGHT_KG,
            BodyCompositionMetric.BMI,
            BodyCompositionMetric.BMR_KCAL
        )) {
            assertNull("$metric down should be null", deltaGoodFor(metric, 90.0, 85.0))
            assertNull("$metric up should be null", deltaGoodFor(metric, 85.0, 90.0))
        }
    }
}
