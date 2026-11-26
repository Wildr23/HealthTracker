package com.gymtracker.app.data.db

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.app.data.health.FakeHealthDataSource
import com.gymtracker.app.data.health.HealthSyncRepository
import com.gymtracker.app.data.importer.JefitCsvParser
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.JefitImportRepository
import com.gymtracker.app.data.repo.SupplementRepository
import com.gymtracker.app.data.repo.WorkoutMetricsRecomputer
import com.gymtracker.app.work.StackReminderScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class GymTrackerDatabaseTest {

    private lateinit var context: Context
    private lateinit var db: GymTrackerDatabase
    private lateinit var seeder: ExerciseCatalogSeeder

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, GymTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seeder = ExerciseCatalogSeeder(context, db.exerciseDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `catalog seeder is idempotent and seeds all 51 exercises`() = runTest {
        seeder.ensureSeeded()
        val firstCount = db.exerciseDao().getAllOnce().size
        assertEquals(51, firstCount)

        // Calling again doesn't duplicate
        ExerciseCatalogSeeder.seedFromJsonString(
            context.assets.open("exercises.json").bufferedReader().readText(),
            db.exerciseDao()
        )
        assertEquals(51, db.exerciseDao().getAllOnce().size)
    }

    @Test
    fun `jefit import transaction is idempotent and health sync preserves samples across reimport`() = runTest {
        seeder.ensureSeeded()
        val prefs = UserPreferencesRepository(context)
        val recomputer = WorkoutMetricsRecomputer(db.workoutDao(), db.metricsDao(), db.bodyWeightDao(), prefs)
        val importRepo = JefitImportRepository(context, db, seeder, recomputer)

        val csv = javaClass.classLoader!!.getResourceAsStream("jefit_fixture.csv")!!
            .bufferedReader(Charsets.UTF_8).readText()
        val bundle = JefitCsvParser(db.exerciseDao().getAllOnce(), ZoneId.of("Europe/Kyiv")).parse(csv)

        importRepo.commitImport(bundle, "jefit_fixture.csv")
        val workoutsAfterFirst = db.workoutDao().getAllWorkoutsOnce()
        assertEquals(3, workoutsAfterFirst.size)

        // Attach FakeHealthDataSource watch workout to session 3 (2026-07-14)
        val squatSession = workoutsAfterFirst.first { it.externalId == "jefit:session:3" }
        val fakeHc = FakeHealthDataSource()
        val (hcSession, hrList, calList) = FakeHealthDataSource.generateWatchWorkout(
            sessionId = "ohealth-1",
            start = squatSession.startedAt,
            end = squatSession.endedAt ?: squatSession.startedAt.plusSeconds(2500),
            baseHr = 112,
            kcalPerMin = 6.5
        )
        fakeHc.sessions += hcSession
        fakeHc.hrSamples += hrList
        fakeHc.calorieSlices += calList

        val healthSync = HealthSyncRepository(db, fakeHc, prefs, recomputer)
        val status = healthSync.syncWorkout(squatSession.id)
        assertEquals(HcStatus.MATCHED, status)
        assertTrue(db.metricsDao().getHrSamplesOnce(squatSession.id).isNotEmpty())
        val metrics = db.metricsDao().getWorkoutMetrics(squatSession.id)!!
        assertTrue(metrics.kcalSmartTotal > metrics.kcalWatch)

        // Re-import the same CSV: workout count stays 3 and cached HR samples are preserved!
        importRepo.commitImport(bundle, "jefit_fixture.csv")
        assertEquals(3, db.workoutDao().getAllWorkoutsOnce().size)
        assertTrue(db.metricsDao().getHrSamplesOnce(squatSession.id).isNotEmpty())
    }

    @Test
    fun `supplement save, log, restock and undo preserve multi-schedules and logs without cascade wipe`() = runTest {
        val prefs = UserPreferencesRepository(context)
        val scheduler = StackReminderScheduler(context, db.supplementDao(), prefs)
        val suppRepo = SupplementRepository(db, db.supplementDao(), db.workoutDao(), scheduler, prefs)

        // Create Omega-3 with 2 daily schedules (morning + evening)
        val itemId = suppRepo.saveSupplement(
            item = SupplementItemEntity(
                nameEn = "Omega-3",
                nameUk = "Омега-3",
                category = SupplementCategory.OMEGA_3,
                defaultDose = 2.0,
                unit = SupplementUnit.CAPSULE,
                servingsRemaining = 60.0
            ),
            schedules = listOf(
                SupplementScheduleEntity(
                    supplementId = 0L,
                    triggerType = SupplementTrigger.WITH_MEAL,
                    timeOfDay = LocalTime.of(9, 0)
                ),
                SupplementScheduleEntity(
                    supplementId = 0L,
                    triggerType = SupplementTrigger.WITH_MEAL,
                    timeOfDay = LocalTime.of(20, 0)
                )
            )
        )
        assertTrue(itemId > 0L)

        val initialScheds = db.supplementDao().getSchedulesForItem(itemId)
        assertEquals(2, initialScheds.size)

        // Log morning dose -> servings decrement, schedules & logs MUST remain intact (no ON DELETE CASCADE!)
        val logId1 = suppRepo.logIntake(itemId = itemId, scheduleId = initialScheds[0].id)
        assertTrue(logId1 > 0L)
        assertEquals(2, db.supplementDao().getSchedulesForItem(itemId).size)
        assertEquals(59.0, db.supplementDao().getItemById(itemId)!!.servingsRemaining!!, 0.01)

        // Log evening dose + restock + update item + undo dose #1
        val logId2 = suppRepo.logIntake(itemId = itemId, scheduleId = initialScheds[1].id)
        assertTrue(logId2 > 0L)
        suppRepo.restock(itemId, 10.0)
        assertEquals(68.0, db.supplementDao().getItemById(itemId)!!.servingsRemaining!!, 0.01)
        assertEquals(2, db.supplementDao().getSchedulesForItem(itemId).size)

        val updatedItem = db.supplementDao().getItemById(itemId)!!.copy(nameEn = "Omega-3 Ultra")
        suppRepo.saveSupplement(updatedItem, db.supplementDao().getSchedulesForItem(itemId))
        assertEquals(2, db.supplementDao().getSchedulesForItem(itemId).size)
        val activeLogsAfterSave = db.supplementDao().getAllLogsOnce().filter { it.deletedAt == null && it.supplementId == itemId }
        assertEquals(2, activeLogsAfterSave.size)

        suppRepo.undoLog(logId1)
        val activeLogsAfterUndo = db.supplementDao().getAllLogsOnce().filter { it.deletedAt == null && it.supplementId == itemId }
        assertEquals(1, activeLogsAfterUndo.size)
        assertEquals(logId2, activeLogsAfterUndo.first().id)
        assertEquals(69.0, db.supplementDao().getItemById(itemId)!!.servingsRemaining!!, 0.01)

        // Updating log time & caffeine mg preserves schedules and servings
        val customTime = Instant.parse("2026-05-01T07:30:00Z")
        suppRepo.updateLogTime(logId2, customTime, caffeineMgOverride = 120)
        val updatedLog = db.supplementDao().getLogById(logId2)!!
        assertEquals(customTime, updatedLog.takenAt)
        assertEquals(120, updatedLog.caffeineMg)
        assertEquals(2, db.supplementDao().getSchedulesForItem(itemId).size)
        assertEquals(69.0, db.supplementDao().getItemById(itemId)!!.servingsRemaining!!, 0.01)
    }
}
