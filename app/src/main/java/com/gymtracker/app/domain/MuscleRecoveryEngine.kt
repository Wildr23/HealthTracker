package com.gymtracker.app.domain

import com.gymtracker.app.data.db.ExerciseEntity
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.Muscle
import com.gymtracker.app.data.db.OneRmFormula
import com.gymtracker.app.data.db.RoutineDayWithExercises
import com.gymtracker.app.data.db.SetType
import com.gymtracker.app.data.db.WorkoutFull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Standard 10 anatomical recovery groups matching the BodyMap & Muscle Recovery model.
 */
enum class RecoveryMuscleGroup(
    val muscles: Set<Muscle>,
    val baseRecoveryHours: Double
) {
    ABS(setOf(Muscle.ABS, Muscle.HIP_FLEXORS), 36.0),
    BACK(setOf(Muscle.BACK, Muscle.LATS, Muscle.TRAPS, Muscle.LOWER_BACK), 72.0),
    BICEPS(setOf(Muscle.BICEPS), 48.0),
    CHEST(setOf(Muscle.CHEST), 66.0),
    FOREARMS(setOf(Muscle.FOREARMS), 36.0),
    GLUTES(setOf(Muscle.GLUTES), 72.0),
    SHOULDERS(setOf(Muscle.FRONT_DELTS, Muscle.SIDE_DELTS, Muscle.REAR_DELTS), 54.0),
    TRICEPS(setOf(Muscle.TRICEPS), 48.0),
    THIGHS(setOf(Muscle.QUADS, Muscle.HAMSTRINGS), 84.0),
    CALVES(setOf(Muscle.CALVES), 42.0)
}

fun Muscle.toRecoveryGroup(): RecoveryMuscleGroup? = when (this) {
    Muscle.CARDIO -> null
    else -> RecoveryMuscleGroup.entries.firstOrNull { this in it.muscles }
}

data class WorkoutMuscleStimulusItem(
    val group: RecoveryMuscleGroup,
    val stimulusPercent: Int,
    val focusSharePercent: Int,
    val workSets: Int,
    val volumeKg: Double
)

data class WorkoutMuscleAnalysis(
    val groupResults: List<WorkoutMuscleStimulusItem>,
    val subMuscleIntensity: Map<Muscle, Float>
)

enum class RecoverySleepImpact {
    LOW_SLEEP,
    NORMAL,
    GOOD_SLEEP,
    UNKNOWN
}

data class MuscleRecoveryItem(
    val group: RecoveryMuscleGroup,
    val recoveryPercent: Int,
    val remainingMinutes: Int,
    val lastTrainedAt: Instant?,
    val lastStimulusPercent: Int?,
    val isInjured: Boolean,
    val injuredDate: LocalDate?
)

data class MuscleRecoverySnapshot(
    val items: List<MuscleRecoveryItem>,
    val muscleFreshnessMap: Map<Muscle, Float>,
    val muscleFatigueMap: Map<Muscle, Float>,
    val injuredMuscleSet: Set<Muscle>,
    val overallReadinessPercent: Int,
    val sleepImpact: RecoverySleepImpact
)

/**
 * Pure domain engine computing per-exercise muscle distributions, personalized workout stimulus
 * scores relative to historical upper-half session baselines, and time-decayed muscle recovery.
 */
object MuscleRecoveryEngine {

    private const val RECOVERY_HORIZON_DAYS = 10L
    private const val BASELINE_WINDOW_DAYS = 365L

    /**
     * Normalized anatomical muscle involvement shares summing to 1.0 for an exercise.
     */
    fun exerciseMuscleShares(exercise: ExerciseEntity): Map<Muscle, Double> {
        if (exercise.type == ExerciseType.CARDIO || exercise.primaryMuscle == Muscle.CARDIO) {
            return emptyMap()
        }
        val sec = exercise.secondaryMuscles
            .filter { it != exercise.primaryMuscle && it != Muscle.CARDIO }
            .distinct()

        return when (sec.size) {
            0 -> mapOf(exercise.primaryMuscle to 1.0)
            1 -> mapOf(exercise.primaryMuscle to 0.70, sec[0] to 0.30)
            2 -> mapOf(exercise.primaryMuscle to 0.62, sec[0] to 0.19, sec[1] to 0.19)
            else -> {
                val secShare = 0.42 / sec.size.toDouble()
                buildMap {
                    put(exercise.primaryMuscle, 0.58)
                    sec.forEach { put(it, secShare) }
                }
            }
        }
    }

    /**
     * Normalized integer percentages summing strictly to 100 for display on the Exercise Guide tab.
     */
    fun exerciseMuscleSharePercents(exercise: ExerciseEntity): List<Pair<Muscle, Int>> {
        if (exercise.type == ExerciseType.CARDIO || exercise.primaryMuscle == Muscle.CARDIO) {
            return listOf(Muscle.CARDIO to 100)
        }
        val shares = exerciseMuscleShares(exercise)
        if (shares.isEmpty()) return emptyList()

        val raw = shares.entries.map { (m, s) -> m to (s * 100.0).roundToInt() }.toMutableList()
        val diff = 100 - raw.sumOf { it.second }
        if (diff != 0 && raw.isNotEmpty()) {
            val idx = raw.indexOfFirst { it.first == exercise.primaryMuscle }.takeIf { it >= 0 } ?: 0
            raw[idx] = raw[idx].first to (raw[idx].second + diff).coerceAtLeast(1)
        }
        return raw.sortedByDescending { it.second }
    }

    /**
     * Computes each muscle group's personal baseline stimulus dose from historical workouts.
     * Uses the upper-half median of sessions with >= 2.0 effective sets so light full-body days
     * do not dilute heavy split-day baselines.
     */
    fun computePersonalBaselines(
        history: List<WorkoutFull>,
        formula: OneRmFormula = OneRmFormula.EPLEY,
        bodyweightKg: Double = 70.0
    ): Map<RecoveryMuscleGroup, Double> {
        val validHistory = history.filter { !it.workout.excludedFromStats && !it.workout.isInProgress }
        val bestE1rmByExId = buildBestE1rmMap(validHistory, formula)
        val latestTime = validHistory.maxOfOrNull { it.workout.startedAt } ?: Instant.now()
        val yearCutoff = latestTime.minus(BASELINE_WINDOW_DAYS, ChronoUnit.DAYS)

        data class SessionGroupSample(val startedAt: Instant, val dose: Double, val effSets: Double)

        val samplesByGroup = mutableMapOf<RecoveryMuscleGroup, MutableList<SessionGroupSample>>()
        for (wf in validHistory) {
            val acc = accumulateWorkoutDoses(wf, bestE1rmByExId, formula, bodyweightKg)
            for ((group, stats) in acc.groupStats) {
                if (stats.effectiveSets >= 2.0 && stats.dose > 0.0) {
                    samplesByGroup.getOrPut(group) { mutableListOf() }
                        .add(SessionGroupSample(wf.workout.startedAt, stats.dose, stats.effectiveSets))
                }
            }
        }

        return RecoveryMuscleGroup.entries.associateWith { group ->
            val allSamples = samplesByGroup[group].orEmpty()
            val recentSamples = allSamples.filter { !it.startedAt.isBefore(yearCutoff) }
            val chosen = if (recentSamples.size >= 4) recentSamples else allSamples

            if (chosen.size < 2) {
                defaultBaselineFor(group)
            } else {
                val upperHalf = chosen
                    .map { it.dose }
                    .sortedDescending()
                    .take(((chosen.size + 1) / 2).coerceIn(1, 8))
                    .sorted()
                medianOf(upperHalf).coerceAtLeast(defaultBaselineFor(group) * 0.45)
            }
        }
    }

    /**
     * Analyzes a single workout against personal baselines to compute:
     * - stimulusPercent (0-150% vs user's own targeted-session norm)
     * - focusSharePercent (sum = 100% distribution inside the session)
     * - subMuscleIntensity (0.2-1.0 for anatomical BodyMap highlighting)
     */
    fun analyzeWorkoutStimulus(
        workout: WorkoutFull,
        history: List<WorkoutFull>,
        formula: OneRmFormula = OneRmFormula.EPLEY,
        bodyweightKg: Double = 70.0
    ): WorkoutMuscleAnalysis {
        val combinedForE1rm = if (history.any { it.workout.id == workout.workout.id }) history else history + workout
        val validForE1rm = combinedForE1rm.filter { !it.workout.excludedFromStats }
        val bestE1rmByExId = buildBestE1rmMap(validForE1rm, formula)
        val baselines = computePersonalBaselines(history, formula, bodyweightKg)
        val acc = accumulateWorkoutDoses(workout, bestE1rmByExId, formula, bodyweightKg)

        if (acc.groupStats.isEmpty()) {
            return WorkoutMuscleAnalysis(emptyList(), emptyMap())
        }

        val totalFocusScore = acc.groupStats.values.sumOf { it.focusWeightedSets }.coerceAtLeast(1e-6)
        val maxSubScore = acc.subMuscleScore.values.maxOrNull()?.coerceAtLeast(1e-6) ?: 1.0

        val rawItems = acc.groupStats.entries.map { (group, stats) ->
            val base = baselines[group] ?: defaultBaselineFor(group)
            val stim = ((stats.dose / base) * 100.0).roundToInt().coerceIn(5, 150)
            val focus = ((stats.focusWeightedSets / totalFocusScore) * 100.0).roundToInt().coerceAtLeast(1)
            WorkoutMuscleStimulusItem(
                group = group,
                stimulusPercent = stim,
                focusSharePercent = focus,
                workSets = stats.primarySets.coerceAtLeast(if (stats.effectiveSets > 0.0) 1 else 0),
                volumeKg = stats.volumeKg
            )
        }.sortedWith(compareByDescending<WorkoutMuscleStimulusItem> { it.stimulusPercent }.thenByDescending { it.focusSharePercent })
            .toMutableList()

        // Normalize focusSharePercent sum to exactly 100 when items exist
        val focusDiff = 100 - rawItems.sumOf { it.focusSharePercent }
        if (focusDiff != 0 && rawItems.isNotEmpty()) {
            val topFocusIdx = rawItems.indices.maxByOrNull { rawItems[it].focusSharePercent } ?: 0
            val cur = rawItems[topFocusIdx]
            rawItems[topFocusIdx] = cur.copy(focusSharePercent = (cur.focusSharePercent + focusDiff).coerceAtLeast(1))
        }

        val subIntensity = acc.subMuscleScore.mapValues { (_, score) ->
            (score / maxSubScore).toFloat().coerceIn(0.20f, 1.0f)
        }

        return WorkoutMuscleAnalysis(
            groupResults = rawItems,
            subMuscleIntensity = subIntensity
        )
    }

    /**
     * Simulates chronological fatigue buildup and time decay across recent workouts to produce
     * per-group recovery percentages, time-to-100% estimates, and BodyMap heatmaps.
     */
    fun computeRecoveryState(
        completedWorkouts: List<WorkoutFull>,
        now: Instant = Instant.now(),
        injuredMuscles: Map<RecoveryMuscleGroup, LocalDate> = emptyMap(),
        sleepMinutesLastNight: Int? = null,
        formula: OneRmFormula = OneRmFormula.EPLEY,
        bodyweightKg: Double = 70.0
    ): MuscleRecoverySnapshot {
        val baselines = computePersonalBaselines(completedWorkouts, formula, bodyweightKg)
        val bestE1rmByExId = buildBestE1rmMap(completedWorkouts.filter { !it.workout.excludedFromStats }, formula)

        val (sleepFactor, sleepImpact) = when {
            sleepMinutesLastNight == null || sleepMinutesLastNight <= 0 -> 1.0 to RecoverySleepImpact.UNKNOWN
            sleepMinutesLastNight < 360 -> 0.85 to RecoverySleepImpact.LOW_SLEEP
            sleepMinutesLastNight >= 480 -> 1.08 to RecoverySleepImpact.GOOD_SLEEP
            else -> 1.0 to RecoverySleepImpact.NORMAL
        }

        val horizonStart = now.minus(RECOVERY_HORIZON_DAYS, ChronoUnit.DAYS)
        val recentWorkouts = completedWorkouts
            .filter {
                !it.workout.excludedFromStats &&
                    !it.workout.isInProgress &&
                    !it.workout.startedAt.isBefore(horizonStart) &&
                    !it.workout.startedAt.isAfter(now)
            }
            .sortedBy { it.workout.startedAt }

        val fatigue = RecoveryMuscleGroup.entries.associateWith { 0.0 }.toMutableMap()
        val lastUpdate = mutableMapOf<RecoveryMuscleGroup, Instant>()
        val lastTrained = mutableMapOf<RecoveryMuscleGroup, Instant>()
        val lastStimulus = mutableMapOf<RecoveryMuscleGroup, Int>()

        for (wf in recentWorkouts) {
            val eventTime = wf.workout.endedAt?.takeIf { !it.isAfter(now) && !it.isBefore(wf.workout.startedAt) }
                ?: wf.workout.startedAt
            val acc = accumulateWorkoutDoses(wf, bestE1rmByExId, formula, bodyweightKg)

            // Decay existing fatigue up to eventTime for worked groups
            for ((group, stats) in acc.groupStats) {
                if (stats.dose <= 0.0) continue
                val prevT = lastUpdate[group]
                val curFatigue = if (prevT != null && eventTime.isAfter(prevT)) {
                    val elapsedHours = Duration.between(prevT, eventTime).toMinutes() / 60.0
                    val fullRecoveryHours = group.baseRecoveryHours / sleepFactor
                    (fatigue[group]!! - elapsedHours / fullRecoveryHours).coerceAtLeast(0.0)
                } else {
                    fatigue[group] ?: 0.0
                }

                val base = baselines[group] ?: defaultBaselineFor(group)
                val stimPct = ((stats.dose / base) * 100.0).roundToInt().coerceIn(5, 150)
                val addedFatigue = (stimPct / 100.0).coerceIn(0.15, 1.15) * 0.90

                fatigue[group] = (curFatigue + addedFatigue).coerceIn(0.0, 1.0)
                lastUpdate[group] = eventTime
                lastTrained[group] = wf.workout.startedAt
                lastStimulus[group] = stimPct
            }
        }

        val items = RecoveryMuscleGroup.entries.map { group ->
            val prevT = lastUpdate[group]
            val currentFatigue = if (prevT != null && now.isAfter(prevT)) {
                val elapsedHours = Duration.between(prevT, now).toMinutes() / 60.0
                val fullRecoveryHours = group.baseRecoveryHours / sleepFactor
                (fatigue[group]!! - elapsedHours / fullRecoveryHours).coerceIn(0.0, 1.0)
            } else {
                fatigue[group]?.coerceIn(0.0, 1.0) ?: 0.0
            }

            val recPct = ((1.0 - currentFatigue) * 100.0).roundToInt().coerceIn(0, 100)
            val remMin = if (recPct >= 100) {
                0
            } else {
                val fullRecoveryHours = group.baseRecoveryHours / sleepFactor
                (currentFatigue * fullRecoveryHours * 60.0).roundToInt().coerceAtLeast(1)
            }
            val injDate = injuredMuscles[group]

            MuscleRecoveryItem(
                group = group,
                recoveryPercent = recPct,
                remainingMinutes = remMin,
                lastTrainedAt = lastTrained[group],
                lastStimulusPercent = lastStimulus[group],
                isInjured = injDate != null,
                injuredDate = injDate
            )
        }

        val fatigueByMuscle = mutableMapOf<Muscle, Float>()
        val freshnessByMuscle = mutableMapOf<Muscle, Float>()
        for (item in items) {
            val fat = (1.0f - item.recoveryPercent / 100f).coerceIn(0f, 1f)
            val fresh = (item.recoveryPercent / 100f).coerceIn(0f, 1f)
            for (m in item.group.muscles) {
                fatigueByMuscle[m] = fat
                freshnessByMuscle[m] = fresh
            }
        }

        val injuredSet = injuredMuscles.keys.flatMap { it.muscles }.toSet()
        val overall = if (items.isEmpty()) 100 else items.map { it.recoveryPercent }.average().roundToInt()

        return MuscleRecoverySnapshot(
            items = items,
            muscleFreshnessMap = freshnessByMuscle,
            muscleFatigueMap = fatigueByMuscle,
            injuredMuscleSet = injuredSet,
            overallReadinessPercent = overall,
            sleepImpact = sleepImpact
        )
    }

    /**
     * Weighted readiness percentage (0-100) for a routine day based on primary muscle recovery.
     */
    fun readinessForRoutineDay(
        day: RoutineDayWithExercises,
        recovery: MuscleRecoverySnapshot
    ): Int {
        val byGroup = recovery.items.associateBy { it.group }
        var weightedSum = 0.0
        var totalWeight = 0.0

        for (de in day.exercises) {
            if (de.exercise.type == ExerciseType.CARDIO) continue
            val group = de.exercise.primaryMuscle.toRecoveryGroup() ?: continue
            val w = de.dayExercise.targetSets.coerceAtLeast(1).toDouble()
            val rec = byGroup[group]?.recoveryPercent ?: 100
            weightedSum += rec * w
            totalWeight += w
        }
        return if (totalWeight <= 0.0) 100 else (weightedSum / totalWeight).roundToInt().coerceIn(0, 100)
    }

    private data class GroupAccum(
        var dose: Double = 0.0,
        var effectiveSets: Double = 0.0,
        var focusWeightedSets: Double = 0.0,
        var primarySets: Int = 0,
        var volumeKg: Double = 0.0
    )

    private data class WorkoutDoseAccum(
        val groupStats: Map<RecoveryMuscleGroup, GroupAccum>,
        val subMuscleScore: Map<Muscle, Double>
    )

    private fun accumulateWorkoutDoses(
        wf: WorkoutFull,
        bestE1rmByExId: Map<Long, Double>,
        formula: OneRmFormula,
        bodyweightKg: Double
    ): WorkoutDoseAccum {
        val groupMap = mutableMapOf<RecoveryMuscleGroup, GroupAccum>()
        val subMap = mutableMapOf<Muscle, Double>()
        val inProgress = wf.workout.isInProgress

        for (we in wf.exercises) {
            val ex = we.exercise
            if (ex.type == ExerciseType.CARDIO || ex.primaryMuscle == Muscle.CARDIO) continue

            val workSets = we.sets.filter {
                it.setType != SetType.WARMUP && (!inProgress || it.completedAt != null)
            }
            if (workSets.isEmpty()) continue

            val bestE1rm = bestE1rmByExId[ex.id]
            var exDose = 0.0
            var exVol = 0.0

            for (s in workSets) {
                val reps = (s.reps ?: 0).coerceAtLeast(0)
                if (reps == 0) continue

                val w = s.weightKg ?: 0.0
                val setLoad = if (ex.type == ExerciseType.BODYWEIGHT || w <= 0.0) {
                    val bwMult = when (s.setType) {
                        SetType.FAILURE -> 1.15
                        SetType.DROP -> 1.05
                        else -> 1.0
                    }
                    bodyweightKg * reps * bwMult
                } else {
                    exVol += w * reps
                    val kBase = if (s.rpe != null) {
                        0.75 + (s.rpe - 6f).coerceIn(0f, 4f) * 0.1125
                    } else if (bestE1rm != null && bestE1rm > 0.0) {
                        val rel = (w / bestE1rm).coerceIn(0.35, 1.05)
                        0.70 + (rel - 0.35) * 0.72
                    } else {
                        val setEst = OneRmEngine.calculate(w, reps.coerceIn(1, 15), formula)?.e1rmKg
                        if (setEst != null && setEst > 0.0) {
                            val rel = (w / setEst).coerceIn(0.35, 1.05)
                            0.70 + (rel - 0.35) * 0.72
                        } else 0.85
                    }
                    val typeMult = when (s.setType) {
                        SetType.FAILURE -> 1.15
                        SetType.DROP -> 1.05
                        else -> 1.0
                    }
                    w * reps * kBase * typeMult
                }
                exDose += setLoad
            }

            if (exDose <= 0.0) continue

            val shares = exerciseMuscleShares(ex)
            val setCount = workSets.size

            // Distribute focus shares cleanly using normalized shares
            for ((muscle, share) in shares) {
                val g = muscle.toRecoveryGroup() ?: continue
                val accum = groupMap.getOrPut(g) { GroupAccum() }
                accum.focusWeightedSets += setCount * share
                subMap[muscle] = (subMap[muscle] ?: 0.0) + setCount * (if (muscle == ex.primaryMuscle) 1.0 else 0.45)
            }

            // Primary + 0.45 synergy weights for personalized dose & baseline accumulation
            ex.primaryMuscle.toRecoveryGroup()?.let { pg ->
                val accum = groupMap.getOrPut(pg) { GroupAccum() }
                accum.dose += exDose
                accum.effectiveSets += setCount.toDouble()
                accum.primarySets += setCount
                accum.volumeKg += exVol
            }

            val secondaries = ex.secondaryMuscles
                .filter { it != ex.primaryMuscle && it != Muscle.CARDIO }
                .distinct()
            for (sm in secondaries) {
                sm.toRecoveryGroup()?.let { sg ->
                    val accum = groupMap.getOrPut(sg) { GroupAccum() }
                    accum.dose += exDose * 0.45
                    accum.effectiveSets += setCount * 0.45
                    accum.volumeKg += exVol * 0.45
                }
            }
        }

        return WorkoutDoseAccum(groupMap, subMap)
    }

    private fun buildBestE1rmMap(workouts: List<WorkoutFull>, formula: OneRmFormula): Map<Long, Double> {
        val map = mutableMapOf<Long, Double>()
        for (wf in workouts) {
            for (we in wf.exercises) {
                if (we.exercise.type != ExerciseType.WEIGHTED) continue
                for (s in we.sets) {
                    if (s.setType == SetType.WARMUP) continue
                    val w = s.weightKg ?: continue
                    val r = s.reps ?: continue
                    if (w <= 0.0 || r !in 1..15) continue
                    val e1 = OneRmEngine.calculate(w, r, formula, s.rpe)?.e1rmKg ?: continue
                    val prev = map[we.exercise.id] ?: 0.0
                    if (e1 > prev) map[we.exercise.id] = e1
                }
            }
        }
        return map
    }

    private fun defaultBaselineFor(group: RecoveryMuscleGroup): Double = when (group) {
        RecoveryMuscleGroup.BACK,
        RecoveryMuscleGroup.CHEST,
        RecoveryMuscleGroup.THIGHS,
        RecoveryMuscleGroup.GLUTES -> 4800.0
        RecoveryMuscleGroup.SHOULDERS,
        RecoveryMuscleGroup.BICEPS,
        RecoveryMuscleGroup.TRICEPS -> 3000.0
        RecoveryMuscleGroup.ABS,
        RecoveryMuscleGroup.FOREARMS,
        RecoveryMuscleGroup.CALVES -> 2000.0
    }

    private fun medianOf(sortedAsc: List<Double>): Double {
        if (sortedAsc.isEmpty()) return 0.0
        val mid = sortedAsc.size / 2
        return if (sortedAsc.size % 2 == 1) {
            sortedAsc[mid]
        } else {
            (sortedAsc[mid - 1] + sortedAsc[mid]) / 2.0
        }
    }
}
