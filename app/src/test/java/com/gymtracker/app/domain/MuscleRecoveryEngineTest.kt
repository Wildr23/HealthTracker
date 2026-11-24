package com.gymtracker.app.domain

import com.gymtracker.app.data.db.CalorieTier
import com.gymtracker.app.data.db.DataSource
import com.gymtracker.app.data.db.ExerciseEntity
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.Muscle
import com.gymtracker.app.data.db.SetType
import com.gymtracker.app.data.db.WorkoutEntity
import com.gymtracker.app.data.db.WorkoutExerciseEntity
import com.gymtracker.app.data.db.WorkoutExerciseWithSets
import com.gymtracker.app.data.db.WorkoutFull
import com.gymtracker.app.data.db.WorkoutSetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MuscleRecoveryEngineTest {

    private val benchExercise = ExerciseEntity(
        id = 1L,
        slug = "barbell-bench-press",
        nameEn = "Barbell Bench Press",
        nameUk = "Жим штанги лежачи",
        type = ExerciseType.WEIGHTED,
        primaryMuscle = Muscle.CHEST,
        secondaryMuscles = listOf(Muscle.TRICEPS, Muscle.FRONT_DELTS),
        calorieTier = CalorieTier.TIER_2_UPPER_COMPOUND
    )

    private val curlExercise = ExerciseEntity(
        id = 2L,
        slug = "barbell-curl",
        nameEn = "Barbell Curl",
        nameUk = "Згинання зі штангою",
        type = ExerciseType.WEIGHTED,
        primaryMuscle = Muscle.BICEPS,
        secondaryMuscles = listOf(Muscle.FOREARMS),
        calorieTier = CalorieTier.TIER_3_ISOLATION
    )

    private val flyExercise = ExerciseEntity(
        id = 3L,
        slug = "machine-fly",
        nameEn = "Machine Fly",
        nameUk = "Батерфляй",
        type = ExerciseType.WEIGHTED,
        primaryMuscle = Muscle.CHEST,
        secondaryMuscles = emptyList(),
        calorieTier = CalorieTier.TIER_3_ISOLATION
    )

    @Test
    fun `exerciseMuscleSharePercents sum strictly to 100`() {
        val benchShares = MuscleRecoveryEngine.exerciseMuscleSharePercents(benchExercise)
        assertEquals(100, benchShares.sumOf { it.second })
        assertEquals(Muscle.CHEST, benchShares.first().first)
        assertEquals(62, benchShares.first().second)

        val isoShares = MuscleRecoveryEngine.exerciseMuscleSharePercents(
            flyExercise
        )
        assertEquals(listOf(Muscle.CHEST to 100), isoShares)
    }

    @Test
    fun `workout stimulus scales relative to personal upper-half baseline`() {
        val baseTime = Instant.parse("2026-10-01T18:00:00Z")
        // 4 historical heavy chest days (~8 working sets @ 100x8)
        val history = (0 until 4).map { idx ->
            makeWorkout(
                id = (idx + 1).toLong(),
                startedAt = baseTime.plus(idx * 7L, ChronoUnit.DAYS),
                exercises = listOf(
                    benchExercise to (1..4).map { s -> Triple(100.0, 8, SetType.WORK) },
                    flyExercise to (1..4).map { s -> Triple(70.0, 10, SetType.WORK) }
                )
            )
        }

        // Light full-body chest block: 3 sets of 60x12 (with 1 warmup ignored)
        val lightWorkout = makeWorkout(
            id = 100L,
            startedAt = baseTime.plus(30, ChronoUnit.DAYS),
            exercises = listOf(
                benchExercise to listOf(
                    Triple(20.0, 12, SetType.WARMUP),
                    Triple(60.0, 12, SetType.WORK),
                    Triple(60.0, 12, SetType.WORK),
                    Triple(60.0, 12, SetType.WORK)
                )
            )
        )

        val lightAnalysis = MuscleRecoveryEngine.analyzeWorkoutStimulus(lightWorkout, history)
        val heavyAnalysis = MuscleRecoveryEngine.analyzeWorkoutStimulus(history.first(), history)

        val lightChest = lightAnalysis.groupResults.first { it.group == RecoveryMuscleGroup.CHEST }
        val heavyChest = heavyAnalysis.groupResults.first { it.group == RecoveryMuscleGroup.CHEST }

        assertTrue("Light chest (${lightChest.stimulusPercent}%) should be well below 60%", lightChest.stimulusPercent < 60)
        assertTrue("Heavy chest (${heavyChest.stimulusPercent}%) should be near 100%", heavyChest.stimulusPercent in 85..115)
        assertEquals(100, lightAnalysis.groupResults.sumOf { it.focusSharePercent })
        assertEquals(100, heavyAnalysis.groupResults.sumOf { it.focusSharePercent })
    }

    @Test
    fun `recovery decays smoothly over hours and accumulates across consecutive workouts`() {
        val workoutTime = Instant.parse("2026-11-20T18:00:00Z")
        val heavyBiceps = makeWorkout(
            id = 10L,
            startedAt = workoutTime,
            exercises = listOf(
                curlExercise to (1..6).map { Triple(45.0, 10, SetType.WORK) }
            )
        )

        val immediatelyAfter = MuscleRecoveryEngine.computeRecoveryState(
            completedWorkouts = listOf(heavyBiceps),
            now = workoutTime.plus(1, ChronoUnit.HOURS)
        )
        val bicepsNow = immediatelyAfter.items.first { it.group == RecoveryMuscleGroup.BICEPS }
        assertTrue("Biceps right after heavy session should be fatigued (<40%), got ${bicepsNow.recoveryPercent}%", bicepsNow.recoveryPercent < 40)
        assertTrue(bicepsNow.remainingMinutes > 0)

        val afterHalfTime = MuscleRecoveryEngine.computeRecoveryState(
            completedWorkouts = listOf(heavyBiceps),
            now = workoutTime.plus(24, ChronoUnit.HOURS)
        ).items.first { it.group == RecoveryMuscleGroup.BICEPS }
        assertTrue("After 24h recovery (${afterHalfTime.recoveryPercent}%) should exceed 1h (${bicepsNow.recoveryPercent}%)", afterHalfTime.recoveryPercent > bicepsNow.recoveryPercent)

        val afterFullTime = MuscleRecoveryEngine.computeRecoveryState(
            completedWorkouts = listOf(heavyBiceps),
            now = workoutTime.plus(50, ChronoUnit.HOURS)
        ).items.first { it.group == RecoveryMuscleGroup.BICEPS }
        assertEquals(100, afterFullTime.recoveryPercent)
        assertEquals(0, afterFullTime.remainingMinutes)
    }

    @Test
    fun `low sleep slows recovery and injuries appear in snapshot`() {
        val t = Instant.parse("2026-11-22T18:00:00Z")
        val w = makeWorkout(
            id = 20L,
            startedAt = t,
            exercises = listOf(benchExercise to (1..5).map { Triple(90.0, 8, SetType.WORK) })
        )

        val normalSleep = MuscleRecoveryEngine.computeRecoveryState(
            completedWorkouts = listOf(w),
            now = t.plus(20, ChronoUnit.HOURS),
            sleepMinutesLastNight = 450
        ).items.first { it.group == RecoveryMuscleGroup.CHEST }

        val lowSleep = MuscleRecoveryEngine.computeRecoveryState(
            completedWorkouts = listOf(w),
            now = t.plus(20, ChronoUnit.HOURS),
            sleepMinutesLastNight = 300,
            injuredMuscles = mapOf(RecoveryMuscleGroup.SHOULDERS to LocalDate.of(2026, 11, 20))
        )
        val lowSleepChest = lowSleep.items.first { it.group == RecoveryMuscleGroup.CHEST }
        val injuredShoulder = lowSleep.items.first { it.group == RecoveryMuscleGroup.SHOULDERS }

        assertTrue("Low sleep recovery (${lowSleepChest.recoveryPercent}%) <= normal (${normalSleep.recoveryPercent}%)", lowSleepChest.recoveryPercent <= normalSleep.recoveryPercent)
        assertEquals(RecoverySleepImpact.LOW_SLEEP, lowSleep.sleepImpact)
        assertTrue(injuredShoulder.isInjured)
        assertTrue(Muscle.FRONT_DELTS in lowSleep.injuredMuscleSet)
    }

    private fun makeWorkout(
        id: Long,
        startedAt: Instant,
        exercises: List<Pair<ExerciseEntity, List<Triple<Double, Int, SetType>>>>
    ): WorkoutFull {
        val workout = WorkoutEntity(
            id = id,
            startedAt = startedAt,
            endedAt = startedAt.plus(45, ChronoUnit.MINUTES),
            title = "Test Workout $id",
            source = DataSource.APP,
            isInProgress = false
        )
        val exList = exercises.mapIndexed { idx, (ex, setSpecs) ->
            val weId = id * 100 + idx
            val we = WorkoutExerciseEntity(
                id = weId,
                workoutId = id,
                exerciseId = ex.id,
                orderIndex = idx
            )
            val sets = setSpecs.mapIndexed { sIdx, (w, r, type) ->
                WorkoutSetEntity(
                    id = weId * 10 + sIdx,
                    workoutExerciseId = weId,
                    setIndex = sIdx,
                    weightKg = w,
                    reps = r,
                    setType = type,
                    completedAt = startedAt.plus((sIdx + 1) * 3L, ChronoUnit.MINUTES)
                )
            }
            WorkoutExerciseWithSets(we, ex, sets, null)
        }
        return WorkoutFull(workout, exList, null)
    }
}
