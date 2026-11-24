package com.gymtracker.app.data.repo

import com.gymtracker.app.data.db.BodyWeightDao
import com.gymtracker.app.data.db.CalorieKind
import com.gymtracker.app.data.db.ExerciseBlockMetricsEntity
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.MetricsDao
import com.gymtracker.app.data.db.OneRmFormula
import com.gymtracker.app.data.db.SetMetricsEntity
import com.gymtracker.app.data.db.SetType
import com.gymtracker.app.data.db.WorkoutDao
import com.gymtracker.app.data.db.WorkoutMetricsEntity
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.domain.HrAnalyticsEngine
import com.gymtracker.app.domain.HrPoint
import com.gymtracker.app.domain.OneRmEngine
import com.gymtracker.app.domain.SetHrInput
import com.gymtracker.app.domain.SmartCalorieEngine
import com.gymtracker.app.domain.SmartCalorieSetInput
import com.gymtracker.app.domain.WatchCalorieBucket
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Single shared recomputer for WorkoutMetrics, SetMetrics, and ExerciseBlockMetrics.
 * Called after active workout edits, Jefit imports, and Health Connect HR/calorie syncs.
 */
@Singleton
class WorkoutMetricsRecomputer @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val metricsDao: MetricsDao,
    private val bodyWeightDao: BodyWeightDao,
    private val prefsRepo: UserPreferencesRepository
) {
    suspend fun recompute(workoutId: Long) {
        val full = workoutDao.getWorkoutFullOnce(workoutId) ?: return
        val prefs = prefsRepo.getOnce()
        val bwKg = bodyWeightDao.getLatestValid()?.kg ?: prefs.defaultBodyWeightKg
        val hrSamples = metricsDao.getHrSamplesOnce(workoutId)
        val calBuckets = metricsDao.getCalorieBucketsOnce(workoutId)

        val hrPoints = hrSamples.map { HrPoint(it.time, it.bpm) }
        val totalBuckets = calBuckets
            .filter { it.kind == CalorieKind.TOTAL }
            .map { WatchCalorieBucket(it.startTime, it.endTime, it.kcal) }
        val activeKcal = calBuckets
            .filter { it.kind == CalorieKind.ACTIVE }
            .takeIf { it.isNotEmpty() }
            ?.sumOf { it.kcal }

        val orderedExercises = full.exercises.sortedBy { it.workoutExercise.orderIndex }
        val allOrderedSets = orderedExercises.flatMap { ex ->
            ex.sets.sortedBy { it.setIndex }.map { s -> ex to s }
        }

        val zonesTrimp = HrAnalyticsEngine.computeZonesAndTrimp(hrPoints, prefs.hrMax)
        val setHrList = HrAnalyticsEngine.computeSetHrMetrics(
            sets = allOrderedSets.map { (_, s) ->
                SetHrInput(
                    setId = s.id,
                    reps = s.reps,
                    startedAt = s.startedAt,
                    completedAt = s.completedAt
                )
            },
            samples = hrPoints,
            workoutEnd = full.workout.endedAt,
            secPerRep = prefs.estSecPerRep
        ).associateBy { it.setId }

        val e1rmEstimateByWorkoutEx = mutableMapOf<Long, Double>()
        for (ex in orderedExercises) {
            if (ex.exercise.type == ExerciseType.WEIGHTED) {
                val maxE1rm = ex.sets
                    .filter { it.setType != SetType.WARMUP && (it.weightKg ?: 0.0) > 0.0 && (it.reps ?: 0) in 1..15 }
                    .mapNotNull { OneRmEngine.calculate(it.weightKg!!, it.reps!!, OneRmFormula.EPLEY, it.rpe)?.e1rmKg }
                    .maxOrNull()
                if (maxE1rm != null) {
                    e1rmEstimateByWorkoutEx[ex.workoutExercise.id] = maxE1rm
                }
            }
        }

        val calorieResult = SmartCalorieEngine.computeWorkoutCalories(
            sets = allOrderedSets.map { (ex, s) ->
                SmartCalorieSetInput(
                    setId = s.id,
                    workoutExerciseId = ex.workoutExercise.id,
                    exerciseType = ex.exercise.type,
                    calorieTier = ex.exercise.calorieTier,
                    weightKg = s.weightKg,
                    reps = s.reps,
                    rpe = s.rpe,
                    setType = s.setType,
                    startedAt = s.startedAt,
                    completedAt = s.completedAt,
                    explicitCardioCalories = s.calories
                )
            },
            watchBuckets = totalBuckets,
            workoutStart = full.workout.startedAt,
            workoutEnd = full.workout.endedAt,
            bodyWeightKg = bwKg,
            tookPreWorkout = full.workout.tookPreWorkout,
            exerciseE1RmKg = e1rmEstimateByWorkoutEx,
            estSecPerRep = prefs.estSecPerRep
        )

        var totalVolumeKg = 0.0
        var totalSets = 0
        var totalReps = 0

        val setMetricsToSave = allOrderedSets.map { (ex, s) ->
            val hr = setHrList[s.id]
            val w = s.weightKg ?: 0.0
            val r = s.reps ?: 0
            val isWeightedWork = ex.exercise.type == ExerciseType.WEIGHTED && s.setType != SetType.WARMUP
            if (isWeightedWork && w > 0.0 && r > 0) {
                totalVolumeKg += w * r
            }
            if (ex.exercise.type != ExerciseType.CARDIO) {
                totalSets++
                totalReps += r
            }
            val canE1rm = isWeightedWork && w > 0.0 && r in 1..15
            SetMetricsEntity(
                setId = s.id,
                peakHr = hr?.peakHr,
                endHr = hr?.endHr,
                restSec = hr?.restSec,
                restAvgHr = hr?.restAvgHr,
                hrRecovery60 = hr?.hrRecovery60,
                sampleCount = hr?.sampleCount ?: 0,
                e1rmEpley = if (canE1rm) OneRmEngine.calculate(w, r, OneRmFormula.EPLEY, s.rpe)?.e1rmKg else null,
                e1rmBrzycki = if (canE1rm) OneRmEngine.calculate(w, r, OneRmFormula.BRZYCKI, s.rpe)?.e1rmKg else null,
                e1rmLombardi = if (canE1rm) OneRmEngine.calculate(w, r, OneRmFormula.LOMBARDI, s.rpe)?.e1rmKg else null
            )
        }

        val blockMetricsToSave = orderedExercises.map { ex ->
            val completedTimes = ex.sets.mapNotNull { it.completedAt }
            val startTimes = ex.sets.mapIndexedNotNull { idx, s ->
                s.startedAt ?: s.completedAt?.minusSeconds(((s.reps ?: 10) * prefs.estSecPerRep).coerceIn(15, 90).toLong())
            }
            val blockStart: Instant? = startTimes.minOrNull()
            val blockEnd: Instant? = completedTimes.maxOrNull()
            val blockHrPoints = if (blockStart != null && blockEnd != null) {
                val endPlusSurge = blockEnd.plusSeconds(20)
                hrPoints.filter { !it.time.isBefore(blockStart) && !it.time.isAfter(endPlusSurge) }
            } else {
                emptyList()
            }
            ExerciseBlockMetricsEntity(
                workoutExerciseId = ex.workoutExercise.id,
                startTime = blockStart,
                endTime = blockEnd,
                avgHr = blockHrPoints.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()?.roundToInt(),
                maxHr = blockHrPoints.maxOfOrNull { it.bpm },
                kcalWatch = calorieResult.perExerciseWatchKcal[ex.workoutExercise.id] ?: 0.0,
                kcalSmart = calorieResult.perExerciseSmartKcal[ex.workoutExercise.id] ?: 0.0
            )
        }

        val durationSec = full.workout.endedAt?.let { end ->
            Duration.between(full.workout.startedAt, end).seconds.coerceAtLeast(0L).toInt()
        } ?: 0

        metricsDao.upsertWorkoutMetrics(
            WorkoutMetricsEntity(
                workoutId = workoutId,
                avgHr = zonesTrimp.avgHr,
                maxHr = zonesTrimp.maxHr,
                minHr = zonesTrimp.minHr,
                kcalWatch = calorieResult.kcalWatch,
                kcalAnaerobicDelta = calorieResult.kcalAnaerobicDelta,
                kcalEpoc = calorieResult.kcalEpoc,
                kcalSmartTotal = calorieResult.kcalSmartTotal,
                kcalActive = activeKcal,
                zoneSeconds = zonesTrimp.zoneSeconds,
                edwardsTrimp = zonesTrimp.edwardsTrimp,
                volumeKg = OneRmEngine.roundToTwoDecimals(totalVolumeKg),
                totalSets = totalSets,
                totalReps = totalReps,
                durationSec = durationSec,
                hasWatchCalories = calorieResult.hasWatchData
            )
        )
        if (setMetricsToSave.isNotEmpty()) {
            metricsDao.upsertSetMetrics(setMetricsToSave)
        }
        if (blockMetricsToSave.isNotEmpty()) {
            metricsDao.upsertExerciseBlockMetrics(blockMetricsToSave)
        }
    }
}
