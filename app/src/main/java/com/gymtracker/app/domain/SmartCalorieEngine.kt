package com.gymtracker.app.domain

import com.gymtracker.app.data.db.CalorieTier
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.SetType
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

data class SmartCalorieSetInput(
    val setId: Long,
    val workoutExerciseId: Long,
    val exerciseType: ExerciseType,
    val calorieTier: CalorieTier,
    val weightKg: Double?,
    val reps: Int?,
    val rpe: Float?,
    val setType: SetType,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val explicitCardioCalories: Double? = null
)

data class WatchCalorieBucket(
    val startTime: Instant,
    val endTime: Instant,
    val kcal: Double
)

data class SmartCalorieResult(
    val kcalWatch: Double,
    val kcalAnaerobicDelta: Double,
    val kcalEpoc: Double,
    val kcalSmartTotal: Double,
    val hasWatchData: Boolean,
    val perExerciseSmartKcal: Map<Long, Double>,
    val perExerciseWatchKcal: Map<Long, Double>
)

/**
 * 3-Component Smart Hybrid Calorie Engine (BUILD_SPEC.md §4.2).
 * Combines smartwatch calories (OHealth 1-minute buckets) with mechanical + metabolic
 * anaerobic lifting expenditure and heavy-load EPOC afterburn, compensating for
 * grip occlusion and pre-workout autonomic HR blunting.
 */
object SmartCalorieEngine {

    private const val GRAVITY = 9.81
    private const val ECCENTRIC_STABILIZATION_FACTOR = 1.35
    private const val JOULES_PER_KCAL = 4184.0
    private const val MUSCULAR_EFFICIENCY = 0.20
    private const val DEFAULT_INTENSITY = 0.75

    fun computeWorkoutCalories(
        sets: List<SmartCalorieSetInput>,
        watchBuckets: List<WatchCalorieBucket>,
        workoutStart: Instant,
        workoutEnd: Instant?,
        bodyWeightKg: Double,
        tookPreWorkout: Boolean,
        exerciseE1RmKg: Map<Long, Double> = emptyMap(),
        estSecPerRep: Int = 3
    ): SmartCalorieResult {
        val bw = bodyWeightKg.coerceIn(40.0, 200.0)
        val hasWatchData = watchBuckets.isNotEmpty()
        val kWatchFromBuckets = watchBuckets.sumOf { it.kcal.coerceAtLeast(0.0) }
        val durationSec = if (workoutEnd != null && workoutEnd.isAfter(workoutStart)) {
            Duration.between(workoutStart, workoutEnd).seconds.coerceAtLeast(60L)
        } else {
            (sets.size * 120L).coerceAtLeast(60L)
        }
        val watchRatePerSec = if (hasWatchData) kWatchFromBuckets / durationSec.toDouble() else 0.0

        val resistanceSets = sets.filter {
            it.exerciseType != ExerciseType.CARDIO && (it.reps ?: 0) > 0
        }

        var totalAnaerobicDelta = 0.0
        var totalEstimatedSetEnergy = 0.0
        var totalSetActiveSec = 0.0
        var compoundTonnageKg = 0.0
        var highIntensityCount = 0

        val perExAnaerobic = mutableMapOf<Long, Double>()
        val perExWatch = mutableMapOf<Long, Double>()

        for (set in resistanceSets) {
            val reps = set.reps ?: 0
            val w = (set.weightKg ?: 0.0).coerceAtLeast(0.0)
            val tSetSec = (reps * estSecPerRep).coerceIn(15, 75).toDouble()
            totalSetActiveSec += tSetSec

            val dRom = when (set.calorieTier) {
                CalorieTier.TIER_1_LOWER_COMPOUND -> 0.60
                CalorieTier.TIER_2_UPPER_COMPOUND -> 0.45
                CalorieTier.TIER_3_ISOLATION, CalorieTier.CARDIO -> 0.35
            }

            val mMoved = when {
                set.exerciseType == ExerciseType.BODYWEIGHT -> 0.85 * bw + w
                set.calorieTier == CalorieTier.TIER_1_LOWER_COMPOUND -> w + 0.55 * bw
                else -> w
            }

            val eMech = (mMoved * GRAVITY * dRom * reps * ECCENTRIC_STABILIZATION_FACTOR) /
                (JOULES_PER_KCAL * MUSCULAR_EFFICIENCY)

            val e1rm = exerciseE1RmKg[set.workoutExerciseId] ?: exerciseE1RmKg.values.firstOrNull()
            val intensity = when {
                set.exerciseType == ExerciseType.BODYWEIGHT -> 0.72
                e1rm != null && e1rm > 0.0 && w > 0.0 -> (w / e1rm).coerceIn(0.45, 1.0)
                set.rpe != null -> (0.60 + (set.rpe - 5f) * 0.08).coerceIn(0.5, 1.0)
                else -> DEFAULT_INTENSITY
            }

            if (set.setType != SetType.WARMUP && (intensity >= 0.75 || (set.rpe ?: 0f) >= 8f)) {
                highIntensityCount++
            }
            if (set.setType != SetType.WARMUP &&
                (set.calorieTier == CalorieTier.TIER_1_LOWER_COMPOUND ||
                    set.calorieTier == CalorieTier.TIER_2_UPPER_COMPOUND)
            ) {
                compoundTonnageKg += (if (set.exerciseType == ExerciseType.BODYWEIGHT) 0.75 * bw else w) * reps
            }

            val metLift = 4.5 + 4.5 * intensity.coerceIn(0.5, 1.0)
            val eMet = metLift * 3.5 * (bw / 200.0) * (tSetSec / 60.0)
            val eSet = eMech + eMet
            totalEstimatedSetEnergy += eSet

            val rawWatchForSet = if (hasWatchData) {
                if (set.completedAt != null) {
                    val sEnd = set.completedAt
                    val sStart = set.startedAt ?: sEnd.minusSeconds(tSetSec.toLong())
                    watchEnergyInWindow(watchBuckets, sStart, sEnd, fallbackRatePerSec = watchRatePerSec)
                } else {
                    watchRatePerSec * tSetSec
                }
            } else {
                0.0
            }

            val effectiveWatchForSet = if (tookPreWorkout && hasWatchData) {
                min(rawWatchForSet, eSet * 0.45)
            } else {
                rawWatchForSet
            }

            val delta = max(0.0, eSet - effectiveWatchForSet)
            totalAnaerobicDelta += delta
            perExWatch[set.workoutExerciseId] = (perExWatch[set.workoutExerciseId] ?: 0.0) + rawWatchForSet
            perExAnaerobic[set.workoutExerciseId] = (perExAnaerobic[set.workoutExerciseId] ?: 0.0) + delta
        }

        // Cardio contribution when watch data is missing
        val explicitCardioKcal = sets
            .filter { it.exerciseType == ExerciseType.CARDIO }
            .sumOf { it.explicitCardioCalories ?: 0.0 }

        val kWatchBaseline = if (hasWatchData) {
            kWatchFromBuckets
        } else {
            // Baseline BMR/rest energy outside lifting sets (~1.6 MET during gym rests)
            val restMin = max(0.0, (durationSec - totalSetActiveSec) / 60.0)
            val restKcal = 1.6 * 3.5 * (bw / 200.0) * restMin
            restKcal + explicitCardioKcal
        }

        val anaerobicFinal = if (hasWatchData) totalAnaerobicDelta else totalEstimatedSetEnergy

        val intensityRatio = if (resistanceSets.isNotEmpty()) {
            highIntensityCount.toDouble() / resistanceSets.size.toDouble()
        } else 0.0
        val compoundFactor = (compoundTonnageKg / 10000.0).coerceIn(0.0, 1.0)
        val epocFactor = if (resistanceSets.isEmpty()) {
            0.0
        } else {
            (0.04 + 0.03 * compoundFactor + 0.02 * intensityRatio).coerceIn(0.04, 0.09)
        }
        val kEpoc = (kWatchBaseline + anaerobicFinal) * epocFactor
        val kSmartTotal = kWatchBaseline + anaerobicFinal + kEpoc

        val perExSmart = mutableMapOf<Long, Double>()
        val allExIds = (perExAnaerobic.keys + perExWatch.keys + sets.map { it.workoutExerciseId }).toSet()
        for (exId in allExIds) {
            val wKcal = perExWatch[exId] ?: 0.0
            val aKcal = perExAnaerobic[exId] ?: 0.0
            perExSmart[exId] = round1(wKcal + aKcal * (1.0 + epocFactor))
            perExWatch[exId] = round1(wKcal)
        }

        return SmartCalorieResult(
            kcalWatch = round1(if (hasWatchData) kWatchFromBuckets else 0.0),
            kcalAnaerobicDelta = round1(anaerobicFinal),
            kcalEpoc = round1(kEpoc),
            kcalSmartTotal = round1(kSmartTotal),
            hasWatchData = hasWatchData,
            perExerciseSmartKcal = perExSmart,
            perExerciseWatchKcal = perExWatch
        )
    }

    private fun watchEnergyInWindow(
        buckets: List<WatchCalorieBucket>,
        start: Instant,
        end: Instant,
        fallbackRatePerSec: Double
    ): Double {
        if (!end.isAfter(start)) return 0.0
        var sum = 0.0
        var coveredSec = 0.0
        val targetSec = Duration.between(start, end).seconds.toDouble()
        for (b in buckets) {
            val bDur = Duration.between(b.startTime, b.endTime).seconds
            if (bDur <= 0) continue
            val ovStart = if (start.isAfter(b.startTime)) start else b.startTime
            val ovEnd = if (end.isBefore(b.endTime)) end else b.endTime
            if (ovEnd.isAfter(ovStart)) {
                val ovSec = Duration.between(ovStart, ovEnd).seconds.toDouble()
                sum += b.kcal * (ovSec / bDur.toDouble())
                coveredSec += ovSec
            }
        }
        return if (coveredSec > 0.0) sum else fallbackRatePerSec * targetSec
    }

    private fun round1(v: Double): Double = round(v * 10.0) / 10.0
}
