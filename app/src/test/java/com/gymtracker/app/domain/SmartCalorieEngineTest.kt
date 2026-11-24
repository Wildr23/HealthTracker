package com.gymtracker.app.domain

import com.gymtracker.app.data.db.CalorieTier
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SmartCalorieEngineTest {

    @Test
    fun `smart calories compensate watch underestimation on 12480kg strength session with preworkout`() {
        val t0 = Instant.parse("2026-11-17T18:42:00Z")
        val tEnd = t0.plusSeconds(43 * 60L)

        // Watch records ~310 kcal in 1-minute buckets across 43 minutes
        val buckets = (0 until 43).map { min ->
            WatchCalorieBucket(
                startTime = t0.plusSeconds(min * 60L),
                endTime = t0.plusSeconds((min + 1) * 60L),
                kcal = 310.0 / 43.0
            )
        }

        // Reproduce 2026-11-17 exact volume: 12,480 kg across 16 sets
        var setId = 1L
        val sets = buildList {
            // Bench Press (20x12 + 3x 60x12 = 2400 kg)
            listOf(20.0 to 12, 60.0 to 12, 60.0 to 12, 60.0 to 12).forEachIndexed { i, (w, r) ->
                add(SmartCalorieSetInput(setId++, 1, ExerciseType.WEIGHTED, CalorieTier.TIER_2_UPPER_COMPOUND, w, r, null, SetType.WORK, null, t0.plusSeconds(200L + i * 150)))
            }
            // Lat Pulldown (60x12, 60x12, 60x8 = 1920 kg)
            listOf(60.0 to 12, 60.0 to 12, 60.0 to 8).forEachIndexed { i, (w, r) ->
                add(SmartCalorieSetInput(setId++, 2, ExerciseType.WEIGHTED, CalorieTier.TIER_2_UPPER_COMPOUND, w, r, null, SetType.WORK, null, t0.plusSeconds(900L + i * 150)))
            }
            // Leg Press (3x 80x20 = 4800 kg)
            repeat(3) { i ->
                add(SmartCalorieSetInput(setId++, 3, ExerciseType.WEIGHTED, CalorieTier.TIER_1_LOWER_COMPOUND, 80.0, 20, null, SetType.WORK, null, t0.plusSeconds(1400L + i * 160)))
            }
            // DB Incline Curl (15x10, 15x8, 15x8 = 390 kg)
            listOf(15.0 to 10, 15.0 to 8, 15.0 to 8).forEachIndexed { i, (w, r) ->
                add(SmartCalorieSetInput(setId++, 4, ExerciseType.WEIGHTED, CalorieTier.TIER_3_ISOLATION, w, r, null, SetType.WORK, null, t0.plusSeconds(1900L + i * 120)))
            }
            // Cable Tricep Pushdown (60x15, 60x16, 60x12 = 2580 kg) + extra set to match ~12.48t
            listOf(60.0 to 15, 60.0 to 16, 60.0 to 12).forEachIndexed { i, (w, r) ->
                add(SmartCalorieSetInput(setId++, 5, ExerciseType.WEIGHTED, CalorieTier.TIER_3_ISOLATION, w, r, null, SetType.WORK, null, t0.plusSeconds(2300L + i * 90)))
            }
        }

        val res = SmartCalorieEngine.computeWorkoutCalories(
            sets = sets,
            watchBuckets = buckets,
            workoutStart = t0,
            workoutEnd = tEnd,
            bodyWeightKg = 105.0,
            tookPreWorkout = true
        )

        assertTrue(res.hasWatchData)
        assertEquals(310.0, res.kcalWatch, 0.5)
        assertTrue("Expected realistic 460-580 kcal total, got ${res.kcalSmartTotal}", res.kcalSmartTotal in 460.0..580.0)
        assertEquals(res.kcalSmartTotal, res.kcalWatch + res.kcalAnaerobicDelta + res.kcalEpoc, 0.2)
    }

    @Test
    fun `lower compound tier expends more energy than upper compound and isolation for equal load`() {
        val t0 = Instant.parse("2026-11-20T18:00:00Z")
        fun singleSetResult(tier: CalorieTier): Double {
            val s = SmartCalorieSetInput(1, 1, ExerciseType.WEIGHTED, tier, 80.0, 10, null, SetType.WORK, null, t0.plusSeconds(60))
            return SmartCalorieEngine.computeWorkoutCalories(
                sets = listOf(s),
                watchBuckets = emptyList(),
                workoutStart = t0,
                workoutEnd = t0.plusSeconds(120),
                bodyWeightKg = 105.0,
                tookPreWorkout = false
            ).kcalAnaerobicDelta
        }

        val lower = singleSetResult(CalorieTier.TIER_1_LOWER_COMPOUND)
        val upper = singleSetResult(CalorieTier.TIER_2_UPPER_COMPOUND)
        val iso = singleSetResult(CalorieTier.TIER_3_ISOLATION)

        assertTrue(lower > upper)
        assertTrue(upper > iso)
    }
}
