package com.gymtracker.app.data.importer

import com.gymtracker.app.data.db.ExerciseEntity
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.SeedExerciseJson
import com.gymtracker.app.domain.OneRmEngine
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class JefitCsvImporterTest {

    private fun loadCatalog(): List<ExerciseEntity> {
        val assetFile = listOf(
            File("src/main/assets/exercises.json"),
            File("app/src/main/assets/exercises.json")
        ).first { it.exists() }
        val items = Json { ignoreUnknownKeys = true }
            .decodeFromString<List<SeedExerciseJson>>(assetFile.readText(Charsets.UTF_8))
        return items.mapIndexed { idx, s ->
            ExerciseEntity(
                id = (idx + 1).toLong(),
                slug = s.slug,
                nameEn = s.nameEn,
                nameUk = s.nameUk,
                type = s.type,
                primaryMuscle = s.primaryMuscle,
                secondaryMuscles = s.secondaryMuscles,
                calorieTier = s.calorieTier,
                jefitEid = s.jefitEid,
                jefitNameMatch = s.jefitNameMatch
            )
        }
    }

    @Test
    fun `synthetic fixture parses all sections splits outliers and skips empty session`() {
        val catalog = loadCatalog()
        assertEquals(51, catalog.size)

        val csv = javaClass.classLoader!!.getResourceAsStream("jefit_fixture.csv")!!
            .bufferedReader(Charsets.UTF_8).readText()
        val bundle = JefitCsvParser(catalog, ZoneId.of("Europe/Kyiv")).parse(csv)
        val r = bundle.report

        assertEquals(4, r.sessionsRead)
        assertEquals(3, r.sessionsImported)
        assertEquals(1, r.sessionsSkippedEmpty)
        assertEquals(listOf("2"), r.skippedEmptySessionIds)
        assertEquals(1, r.routinesCount)
        assertEquals(1, r.routineDaysCount)
        assertEquals(2, bundle.routines[0].days[0].exercises.size)

        // Body weights: 0 skipped, 60 kg on 2026-03-11 flagged as outlier
        assertEquals(4, r.bodyWeightsCount)
        assertEquals(1, r.bodyWeightOutliers.size)
        assertEquals(60.0, r.bodyWeightOutliers[0].kg, 0.01)

        // Special splits (eid=112 Concentration Curl, eid=250 Cable Shoulder Extension)
        assertEquals(2, r.specialMappings.size)
        assertTrue(r.specialMappings.any { it.mappedSlug == "dumbbell-concentration-curl" })
        assertTrue(r.specialMappings.any { it.mappedSlug == "cable-shoulder-extension" })

        // External HC duplicate cardio (eid=322 blank ename)
        assertEquals(1, r.externalHcDuplicatesCount)
        val hcWorkout = bundle.workouts.first { it.externalId == "jefit:session:4" }
        assertTrue(hcWorkout.excludedFromStats)

        // Spot-check 2026-07-14 Squat sets & timestamps
        val squatWorkout = bundle.workouts.first { it.externalId == "jefit:session:3" }
        val squatEx = squatWorkout.exercises.first { it.exerciseSlug == "barbell-squat" }
        assertEquals("глибокий присяд", squatEx.note)
        assertEquals(5, squatEx.sets.size)
        assertEquals(listOf(60.0, 60.0, 100.0, 105.0, 105.0), squatEx.sets.map { it.weightKg })
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Europe/Kyiv"))
        assertEquals(
            listOf("18:03:39", "18:06:52", "18:10:36", "18:14:41", "18:18:33"),
            squatEx.sets.map { timeFmt.format(it.completedAt!!) }
        )

        // Cardio set-log disagreement recorded without crashing
        assertEquals(1, r.setCountDisagreements.size)
    }

    @Test
    fun `real jefit export csv matches audited counts and spot checks when present`() {
        val realFile = listOf(
            File("dikiyr_20261123.csv"),
            File("../dikiyr_20261123.csv")
        ).firstOrNull { it.exists() }
        Assume.assumeTrue("Real CSV not present in CI", realFile != null)

        val catalog = loadCatalog()
        val zone = ZoneId.of("Europe/Kyiv")
        val bundle = JefitCsvParser(catalog, zone).parse(realFile!!.readText(Charsets.UTF_8))
        val r = bundle.report

        assertEquals(244, r.sessionsRead)
        assertEquals(242, r.sessionsImported)
        assertEquals(2, r.sessionsSkippedEmpty)
        assertEquals(1345, r.exerciseLogsCount)
        assertEquals(1157, r.setLogRowsCount)
        assertEquals(60, r.timestampedStrengthWorkoutsCount)
        assertEquals(1142, r.timestampedStrengthSetsCount)
        assertEquals(40, r.externalHcDuplicatesCount)
        assertEquals(49, r.distinctEidsMapped)
        assertTrue(r.unmappedEids.isEmpty())
        assertEquals(15, r.setCountDisagreements.size)
        assertEquals(4, r.notesAttachedCount) // 4 notes have eid > 0

        // Outlier check: 60 kg on 2026-03-11 flagged, 105 kg not flagged
        assertTrue(r.bodyWeightOutliers.any { it.date == "2026-03-11" && it.kg == 60.0 })
        assertFalse(r.bodyWeightOutliers.any { it.date == "2026-03-16" })

        // Spot check 1: 2026-07-14 Barbell Squat
        val wSquat = bundle.workouts.first { it.externalId == "jefit:session:169" }
        val squat = wSquat.exercises.first { it.exerciseSlug == "barbell-squat" }
        assertEquals(5, squat.sets.size)
        assertEquals(listOf(60.0, 60.0, 100.0, 105.0, 105.0), squat.sets.map { it.weightKg })
        assertEquals(listOf(10, 10, 10, 10, 10), squat.sets.map { it.reps })
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)
        assertEquals(
            listOf("18:03:39", "18:06:52", "18:10:36", "18:14:41", "18:18:33"),
            squat.sets.map { timeFmt.format(it.completedAt!!) }
        )
        assertEquals(140.0, OneRmEngine.calculate(105.0, 10)!!.e1rmKg, 0.01)

        // Spot check 2: 2026-04-28 Barbell Bench Press
        val wBench26 = bundle.workouts.first { it.externalId == "jefit:session:142" }
        val bench26 = wBench26.exercises.first { it.exerciseSlug == "barbell-bench-press" }
        assertEquals(listOf(20.0, 50.0, 70.0, 80.0), bench26.sets.map { it.weightKg })
        assertEquals(listOf(10, 12, 12, 12), bench26.sets.map { it.reps })
        assertEquals(112.0, OneRmEngine.calculate(80.0, 12)!!.e1rmKg, 0.01)

        // Spot check 3: 2019-09-10 First workout
        val wFirst = bundle.workouts.first { it.externalId == "jefit:session:1" }
        val benchFirst = wFirst.exercises.first { it.exerciseSlug == "barbell-bench-press" }
        assertEquals(listOf(20.0, 40.0, 45.0, 50.0), benchFirst.sets.map { it.weightKg })
        assertEquals(listOf(20, 10, 10, 12), benchFirst.sets.map { it.reps })

        // Spot check 4: 2026-11-17 session _id=1794940950
        val wNov17 = bundle.workouts.first { it.externalId == "jefit:session:1794940950" }
        assertEquals(5, wNov17.exercises.size)
        val nov17Vol = wNov17.exercises.sumOf { ex ->
            ex.sets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
        }
        assertEquals(12090.0, nov17Vol, 0.1)
        assertNotNull(wNov17.exercises.first().sets.first().completedAt)
    }
}
