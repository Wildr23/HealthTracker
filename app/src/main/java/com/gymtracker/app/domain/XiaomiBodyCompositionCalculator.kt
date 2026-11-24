package com.gymtracker.app.domain

import kotlin.math.min
import kotlin.math.roundToInt

enum class ScaleBodyType {
    OBESE,
    OVERWEIGHT,
    THICK_SET,
    LACK_EXERCISE,
    BALANCED,
    BALANCED_MUSCULAR,
    SKINNY,
    BALANCED_SKINNY,
    SKINNY_MUSCULAR
}

enum class MetricStatus {
    LOW,
    NORMAL,
    OPTIMAL,
    HIGH
}

data class ScaleBodyComposition(
    val weightKg: Double,
    val impedanceOhm: Int?,
    val bmi: Double,
    val idealWeightKg: Double,
    val bmrKcal: Int,
    val visceralFat: Int,
    val fatPercent: Double?,
    val leanBodyMassKg: Double?,
    val waterPercent: Double?,
    val muscleMassKg: Double?,
    val boneMassKg: Double?,
    val proteinPercent: Double?,
    val metabolicAge: Int?,
    val bodyType: ScaleBodyType?
)

/**
 * Pure Kotlin body-composition estimation formulas matching Xiaomi Mi Body Composition Scale 2
 * (Huami / Zepp Life / MiScaleLib reverse-engineered equations).
 */
object XiaomiBodyCompositionCalculator {

    fun calculate(
        weightKg: Double,
        heightCm: Int,
        age: Int,
        isMale: Boolean,
        impedanceOhm: Int?
    ): ScaleBodyComposition {
        val safeHeightCm = heightCm.coerceIn(100, 240)
        val safeWeightKg = weightKg.coerceIn(10.0, 250.0)
        val safeAge = age.coerceIn(10, 100)
        val heightM = safeHeightCm / 100.0

        val bmi = round1(safeWeightKg / (heightM * heightM))
        val idealWeightKg = round1(
            if (isMale) (safeHeightCm - 80.0) * 0.7 else (safeHeightCm - 70.0) * 0.6
        )

        val bmrRaw = if (isMale) {
            877.8 + safeWeightKg * 14.916 - safeHeightCm * 0.726 - safeAge * 8.976
        } else {
            864.6 + safeWeightKg * 10.2036 - safeHeightCm * 0.39336 - safeAge * 6.204
        }
        val bmrKcal = bmrRaw.roundToInt().coerceIn(500, 5000)

        val visceralRaw = if (isMale) {
            val denomA = (((safeHeightCm * 0.4) - (safeHeightCm * safeHeightCm * 0.0826)) * -1.0) + 48.0
            val branchA = ((safeWeightKg * 305.0) / denomA) - 2.9 + (safeAge * 0.15)
            val branchB = (((safeHeightCm * 0.143) - (safeWeightKg * (0.765 - safeHeightCm * 0.0015))) * -1.0) +
                (safeAge * 0.15) - 5.0
            min(branchA, branchB)
        } else {
            val subsub = (safeHeightCm * 1.45) + (safeHeightCm * 0.1158 * safeHeightCm) - 120.0
            (safeWeightKg * 500.0 / subsub) - 6.0 + (safeAge * 0.07)
        }
        val visceralFat = visceralRaw.roundToInt().coerceIn(1, 50)

        val validImp = impedanceOhm?.takeIf { it in 100..3000 }
        if (validImp == null) {
            return ScaleBodyComposition(
                weightKg = round2(safeWeightKg),
                impedanceOhm = null,
                bmi = bmi,
                idealWeightKg = idealWeightKg,
                bmrKcal = bmrKcal,
                visceralFat = visceralFat,
                fatPercent = null,
                leanBodyMassKg = null,
                waterPercent = null,
                muscleMassKg = null,
                boneMassKg = null,
                proteinPercent = null,
                metabolicAge = null,
                bodyType = null
            )
        }

        val lbmCoeff = (safeHeightCm * 9.058 / 100.0) * (safeHeightCm / 100.0) +
            (safeWeightKg * 0.32) +
            12.226 -
            (validImp * 0.0068) -
            (safeAge * 0.0542)

        val lbmSub = when {
            !isMale && safeAge <= 49 -> 9.25
            !isMale && safeAge > 49 -> 7.25
            else -> 0.8
        }

        val coeff = when {
            isMale && safeWeightKg < 61.0 -> 0.98
            !isMale && safeWeightKg > 60.0 -> 0.96 * if (safeHeightCm > 160) 1.03 else 1.0
            !isMale && safeWeightKg < 50.0 -> 1.02 * if (safeHeightCm > 160) 1.03 else 1.0
            else -> 1.0
        }

        val fatRaw = (1.0 - (((lbmCoeff - lbmSub) * coeff) / safeWeightKg)) * 100.0
        val fatPercent = round1((if (fatRaw > 63.0) 75.0 else fatRaw).coerceIn(5.0, 75.0))
        val leanBodyMassKg = round1((safeWeightKg * (1.0 - fatPercent / 100.0)).coerceAtLeast(10.0))

        val waterRaw = (100.0 - fatPercent) * 0.7
        val waterAdj = waterRaw * if (waterRaw <= 50.0) 1.02 else 0.98
        val waterPercent = round1(waterAdj.coerceIn(35.0, 75.0))

        val boneBase = ((if (isMale) 0.18016894 else 0.245691014) - (lbmCoeff * 0.05158)) * -1.0
        val boneAdj = if (boneBase > 2.2) boneBase + 0.1 else boneBase - 0.1
        val boneMassKg = round2(boneAdj.coerceIn(0.5, 8.0))

        val muscleMassKg = round1((safeWeightKg - ((fatPercent * 0.01) * safeWeightKg) - boneMassKg).coerceIn(10.0, 120.0))

        val bonePercent = (boneMassKg / safeWeightKg) * 100.0
        val proteinPercent = round1((100.0 - fatPercent - waterPercent - bonePercent).coerceIn(5.0, 32.0))

        val metabolicRaw = if (isMale) {
            safeHeightCm * -0.7471 + safeWeightKg * 0.9161 + safeAge * 0.4184 + validImp * 0.0517 + 54.2267
        } else {
            safeHeightCm * -1.1165 + safeWeightKg * 1.5784 + safeAge * 0.4615 + validImp * 0.0415 + 83.2548
        }
        val metabolicAge = metabolicRaw.roundToInt().coerceIn(15, 80)

        val bodyType = classifyBodyType(
            fatPercent = fatPercent,
            muscleMassKg = muscleMassKg,
            heightCm = safeHeightCm,
            age = safeAge,
            isMale = isMale
        )

        return ScaleBodyComposition(
            weightKg = round2(safeWeightKg),
            impedanceOhm = validImp,
            bmi = bmi,
            idealWeightKg = idealWeightKg,
            bmrKcal = bmrKcal,
            visceralFat = visceralFat,
            fatPercent = fatPercent,
            leanBodyMassKg = leanBodyMassKg,
            waterPercent = waterPercent,
            muscleMassKg = muscleMassKg,
            boneMassKg = boneMassKg,
            proteinPercent = proteinPercent,
            metabolicAge = metabolicAge,
            bodyType = bodyType
        )
    }

    fun bmiStatus(bmi: Double): MetricStatus = when {
        bmi < 18.5 -> MetricStatus.LOW
        bmi < 25.0 -> MetricStatus.OPTIMAL
        bmi < 28.0 -> MetricStatus.NORMAL
        else -> MetricStatus.HIGH
    }

    fun fatStatus(fatPct: Double, isMale: Boolean, age: Int): MetricStatus {
        val (low, optHigh, high) = if (isMale) {
            when {
                age < 40 -> Triple(11.0, 17.0, 22.0)
                age < 60 -> Triple(12.0, 19.0, 25.0)
                else -> Triple(14.0, 21.0, 27.0)
            }
        } else {
            when {
                age < 40 -> Triple(21.0, 27.0, 33.0)
                age < 60 -> Triple(22.0, 29.0, 35.0)
                else -> Triple(23.0, 30.0, 36.0)
            }
        }
        return when {
            fatPct < low -> MetricStatus.LOW
            fatPct <= optHigh -> MetricStatus.OPTIMAL
            fatPct <= high -> MetricStatus.NORMAL
            else -> MetricStatus.HIGH
        }
    }

    fun waterStatus(waterPct: Double, isMale: Boolean): MetricStatus {
        val (low, high) = if (isMale) 55.0 to 65.0 else 45.0 to 60.0
        return when {
            waterPct < low -> MetricStatus.LOW
            waterPct <= high -> MetricStatus.OPTIMAL
            else -> MetricStatus.NORMAL
        }
    }

    fun visceralStatus(visceralFat: Int): MetricStatus = when {
        visceralFat <= 9 -> MetricStatus.OPTIMAL
        visceralFat <= 14 -> MetricStatus.NORMAL
        else -> MetricStatus.HIGH
    }

    fun proteinStatus(proteinPct: Double): MetricStatus = when {
        proteinPct < 16.0 -> MetricStatus.LOW
        proteinPct <= 20.0 -> MetricStatus.OPTIMAL
        else -> MetricStatus.NORMAL
    }

    private fun classifyBodyType(
        fatPercent: Double,
        muscleMassKg: Double,
        heightCm: Int,
        age: Int,
        isMale: Boolean
    ): ScaleBodyType {
        val (fatLow, fatHigh) = if (isMale) {
            if (age < 40) 11.0 to 21.0 else 12.0 to 23.0
        } else {
            if (age < 40) 21.0 to 31.0 else 22.0 to 33.0
        }
        val (muscleLow, muscleHigh) = if (isMale) {
            when {
                heightCm < 160 -> 38.5 to 46.5
                heightCm <= 170 -> 44.0 to 52.4
                else -> 49.4 to 59.4
            }
        } else {
            when {
                heightCm < 150 -> 29.1 to 34.7
                heightCm <= 160 -> 32.9 to 37.5
                else -> 36.5 to 42.5
            }
        }

        val fatLevel = when {
            fatPercent < fatLow -> -1
            fatPercent <= fatHigh -> 0
            else -> 1
        }
        val muscleLevel = when {
            muscleMassKg < muscleLow -> -1
            muscleMassKg <= muscleHigh -> 0
            else -> 1
        }

        return when {
            fatLevel > 0 && muscleLevel < 0 -> ScaleBodyType.OBESE
            fatLevel > 0 && muscleLevel == 0 -> ScaleBodyType.OVERWEIGHT
            fatLevel > 0 && muscleLevel > 0 -> ScaleBodyType.THICK_SET
            fatLevel == 0 && muscleLevel < 0 -> ScaleBodyType.LACK_EXERCISE
            fatLevel == 0 && muscleLevel == 0 -> ScaleBodyType.BALANCED
            fatLevel == 0 && muscleLevel > 0 -> ScaleBodyType.BALANCED_MUSCULAR
            fatLevel < 0 && muscleLevel < 0 -> ScaleBodyType.SKINNY
            fatLevel < 0 && muscleLevel == 0 -> ScaleBodyType.BALANCED_SKINNY
            else -> ScaleBodyType.SKINNY_MUSCULAR
        }
    }

    private fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0
    private fun round2(v: Double): Double = (v * 100.0).roundToInt() / 100.0
}
