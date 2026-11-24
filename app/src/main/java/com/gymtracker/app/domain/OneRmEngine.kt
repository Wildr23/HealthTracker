package com.gymtracker.app.domain

import com.gymtracker.app.data.db.OneRmFormula
import kotlin.math.pow
import kotlin.math.round

data class OneRmResult(
    val e1rmKg: Double,
    val isHighRepLowAccuracy: Boolean
)

data class RepMaxRow(
    val percent: Int,
    val estimatedMaxReps: Int,
    val targetWeightKg: Double
)

object OneRmEngine {

    private val percentageLadder = listOf(
        100 to 1,
        95 to 2,
        90 to 4,
        85 to 6,
        80 to 8,
        75 to 10,
        70 to 12
    )

    /**
     * Computes Estimated 1RM (e1RM) for a weighted working set.
     * Supports Epley, Brzycki, Lombardi, and optional RPE adjustment (RIR = 10 - RPE).
     * Returns null when weightKg <= 0 or reps outside [1, 15].
     */
    fun calculate(
        weightKg: Double,
        reps: Int,
        formula: OneRmFormula = OneRmFormula.EPLEY,
        rpe: Float? = null
    ): OneRmResult? {
        if (weightKg <= 0.0 || reps < 1 || reps > 15) return null
        val rir = if (rpe != null) (10f - rpe).coerceIn(0f, 5f) else 0f
        val effReps = reps.toDouble() + rir.toDouble()

        val raw = if (effReps <= 1.0) {
            weightKg
        } else {
            when (formula) {
                OneRmFormula.EPLEY -> weightKg * (1.0 + effReps / 30.0)
                OneRmFormula.BRZYCKI -> weightKg * (36.0 / (37.0 - effReps).coerceAtLeast(1.0))
                OneRmFormula.LOMBARDI -> weightKg * effReps.pow(0.10)
            }
        }
        return OneRmResult(
            e1rmKg = roundToTwoDecimals(raw),
            isHighRepLowAccuracy = reps > 12
        )
    }

    /**
     * Builds a working-weight percentage matrix from 100% down to 70% of 1RM.
     */
    fun percentageTable(e1rmKg: Double, roundingStepKg: Double = 0.5): List<RepMaxRow> {
        if (e1rmKg <= 0.0) return emptyList()
        val step = roundingStepKg.coerceAtLeast(0.25)
        return percentageLadder.map { (pct, estReps) ->
            val raw = e1rmKg * (pct / 100.0)
            val rounded = round(raw / step) * step
            RepMaxRow(
                percent = pct,
                estimatedMaxReps = estReps,
                targetWeightKg = roundToTwoDecimals(rounded)
            )
        }
    }

    internal fun roundToTwoDecimals(v: Double): Double = round(v * 100.0) / 100.0
}
