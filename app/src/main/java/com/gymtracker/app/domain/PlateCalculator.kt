package com.gymtracker.app.domain

import kotlin.math.roundToInt

data class PlateCount(
    val weightKg: Double,
    val countPerSide: Int
)

data class PlateBreakdown(
    val targetWeightKg: Double,
    val barWeightKg: Double,
    val achievableWeightKg: Double,
    val platesPerSide: List<PlateCount>,
    val remainderPerSideKg: Double
)

object PlateCalculator {

    val DEFAULT_PLATES_KG = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    fun calculate(
        targetWeightKg: Double,
        barWeightKg: Double = 20.0,
        availablePlates: List<Double> = DEFAULT_PLATES_KG
    ): PlateBreakdown {
        if (targetWeightKg <= barWeightKg) {
            return PlateBreakdown(
                targetWeightKg = targetWeightKg,
                barWeightKg = barWeightKg,
                achievableWeightKg = barWeightKg,
                platesPerSide = emptyList(),
                remainderPerSideKg = 0.0
            )
        }

        var remainingPerSide = (targetWeightKg - barWeightKg) / 2.0
        val sortedPlates = availablePlates.filter { it > 0.0 }.sortedDescending()
        val chosen = mutableListOf<PlateCount>()

        for (plate in sortedPlates) {
            val count = ((remainingPerSide + 1e-6) / plate).toInt()
            if (count > 0) {
                chosen += PlateCount(plate, count)
                remainingPerSide -= count * plate
            }
        }

        val perSideLoaded = chosen.sumOf { it.weightKg * it.countPerSide }
        val totalAchievable = ((barWeightKg + perSideLoaded * 2.0) * 100.0).roundToInt() / 100.0
        val rem = (remainingPerSide.coerceAtLeast(0.0) * 100.0).roundToInt() / 100.0

        return PlateBreakdown(
            targetWeightKg = targetWeightKg,
            barWeightKg = barWeightKg,
            achievableWeightKg = totalAchievable,
            platesPerSide = chosen,
            remainderPerSideKg = rem
        )
    }
}
