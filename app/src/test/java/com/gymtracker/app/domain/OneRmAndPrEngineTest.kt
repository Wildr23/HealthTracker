package com.gymtracker.app.domain

import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.OneRmFormula
import com.gymtracker.app.data.db.PrType
import com.gymtracker.app.data.db.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OneRmAndPrEngineTest {

    @Test
    fun `epley brzycki lombardi formulas calculate accurately`() {
        val epley = OneRmEngine.calculate(105.0, 10, OneRmFormula.EPLEY)!!
        assertEquals(140.0, epley.e1rmKg, 0.01)
        assertFalse(epley.isHighRepLowAccuracy)

        val bench = OneRmEngine.calculate(80.0, 12, OneRmFormula.EPLEY)!!
        assertEquals(112.0, bench.e1rmKg, 0.01)

        val brzycki = OneRmEngine.calculate(100.0, 5, OneRmFormula.BRZYCKI)!!
        assertEquals(112.5, brzycki.e1rmKg, 0.01)

        val lombardi = OneRmEngine.calculate(100.0, 10, OneRmFormula.LOMBARDI)!!
        assertTrue(lombardi.e1rmKg > 120.0)

        val oneRep = OneRmEngine.calculate(120.0, 1, OneRmFormula.EPLEY)!!
        assertEquals(120.0, oneRep.e1rmKg, 0.01)
    }

    @Test
    fun `rpe adjustment adds reserve reps and flags high reps`() {
        val rpe8 = OneRmEngine.calculate(100.0, 8, OneRmFormula.EPLEY, rpe = 8f)!!
        // effective reps = 8 + (10 - 8) = 10 -> 100 * (1 + 10/30) = 133.33
        assertEquals(133.33, rpe8.e1rmKg, 0.05)

        val highRep = OneRmEngine.calculate(40.0, 14, OneRmFormula.EPLEY)!!
        assertTrue(highRep.isHighRepLowAccuracy)

        assertNull(OneRmEngine.calculate(40.0, 18))
        assertNull(OneRmEngine.calculate(0.0, 10))
    }

    @Test
    fun `percentage table generates expected ladder`() {
        val rows = OneRmEngine.percentageTable(140.0, roundingStepKg = 0.5)
        assertEquals(7, rows.size)
        assertEquals(100, rows[0].percent)
        assertEquals(140.0, rows[0].targetWeightKg, 0.01)
        assertEquals(80, rows[4].percent)
        assertEquals(112.0, rows[4].targetWeightKg, 0.01)
    }

    @Test
    fun `pr engine detects all 5 pr categories and training breaks over 60 days`() {
        val s1 = ExerciseSessionInput(
            workoutId = 1L,
            startedAt = Instant.parse("2023-06-01T18:00:00Z"),
            workoutTitle = "Chest",
            note = null,
            blockAvgHr = null,
            blockMaxHr = null,
            sets = listOf(
                PrSetInput(1, 0, 80.0, 8, null, SetType.WORK),
                PrSetInput(2, 1, 80.0, 8, null, SetType.WORK)
            )
        )
        val s2 = ExerciseSessionInput(
            workoutId = 2L,
            startedAt = Instant.parse("2026-04-28T18:00:00Z"),
            workoutTitle = "Full body",
            note = "comeback",
            blockAvgHr = 115,
            blockMaxHr = 128,
            sets = listOf(
                PrSetInput(3, 0, 20.0, 10, null, SetType.WARMUP),
                PrSetInput(4, 1, 80.0, 12, null, SetType.WORK)
            )
        )

        val analysis = PrEngine.analyzeExerciseHistory(ExerciseType.WEIGHTED, listOf(s1, s2))
        assertEquals(1, analysis.trainingBreaks.size)
        assertTrue(analysis.trainingBreaks[0].days > 1000)

        assertNotNull(analysis.records.bestE1rmKg)
        assertEquals(112.0, analysis.records.bestE1rmKg!!, 0.01)
        assertEquals(80.0, analysis.records.maxWeightKg!!, 0.01)

        // Session 2 beats BEST_E1RM, MAX_REPS_AT_WEIGHT (80x12 > 80x8), and MAX_SET_VOLUME
        val s2Badges = analysis.points[1].prBadges
        assertTrue(PrType.BEST_E1RM in s2Badges)
        assertTrue(PrType.MAX_REPS_AT_WEIGHT in s2Badges)
        assertTrue(PrType.MAX_SET_VOLUME in s2Badges)

        // Live PR hit check
        val liveHits = PrEngine.detectLiveSetPrs(
            exerciseType = ExerciseType.WEIGHTED,
            weightKg = 100.0,
            reps = 5,
            rpe = null,
            setType = SetType.WORK,
            priorSessions = listOf(s1, s2),
            currentSessionWeightVolume = 1000.0
        )
        assertTrue(liveHits.any { it.type == PrType.MAX_WEIGHT })
        assertTrue(liveHits.any { it.type == PrType.BEST_E1RM })
    }
}
