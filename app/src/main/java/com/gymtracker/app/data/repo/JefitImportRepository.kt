package com.gymtracker.app.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.gymtracker.app.data.db.BodyWeightEntity
import com.gymtracker.app.data.db.DataSource
import com.gymtracker.app.data.db.ExerciseCatalogSeeder
import com.gymtracker.app.data.db.GymTrackerDatabase
import com.gymtracker.app.data.db.HcStatus
import com.gymtracker.app.data.db.ImportRunEntity
import com.gymtracker.app.data.db.RoutineDayEntity
import com.gymtracker.app.data.db.RoutineDayExerciseEntity
import com.gymtracker.app.data.db.RoutineEntity
import com.gymtracker.app.data.db.SyncState
import com.gymtracker.app.data.db.WorkoutEntity
import com.gymtracker.app.data.db.WorkoutExerciseEntity
import com.gymtracker.app.data.db.WorkoutSetEntity
import com.gymtracker.app.data.importer.JefitCsvParser
import com.gymtracker.app.data.importer.JefitImportReport
import com.gymtracker.app.data.importer.JefitParsedBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JefitImportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: GymTrackerDatabase,
    private val seeder: ExerciseCatalogSeeder,
    private val metricsRecomputer: WorkoutMetricsRecomputer
) {
    private val json = Json { prettyPrint = false }

    suspend fun previewFromUri(uri: Uri): JefitParsedBundle = withContext(Dispatchers.IO) {
        seeder.ensureSeeded()
        val rawText = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("Cannot open selected CSV file")
        previewFromString(rawText)
    }

    suspend fun previewFromString(rawCsv: String): JefitParsedBundle = withContext(Dispatchers.IO) {
        seeder.ensureSeeded()
        val catalog = database.exerciseDao().getAllOnce()
        JefitCsvParser(catalog).parse(rawCsv)
    }

    suspend fun commitImport(
        bundle: JefitParsedBundle,
        fileName: String,
        onProgress: (Float) -> Unit = {}
    ): JefitImportReport = withContext(Dispatchers.IO) {
        seeder.ensureSeeded()
        val catalogBySlug = database.exerciseDao().getAllOnce().associateBy { it.slug }
        val routineDayIdByExtId = mutableMapOf<String, Long>()
        val importedWorkoutIds = mutableListOf<Long>()
        val now = Instant.now()

        database.withTransaction {
            val bwDao = database.bodyWeightDao()
            for (bw in bundle.bodyWeights) {
                val existing = bwDao.findByDateAndSource(bw.date, DataSource.JEFIT_IMPORT)
                bwDao.upsert(
                    BodyWeightEntity(
                        id = existing?.id ?: 0,
                        date = bw.date,
                        kg = bw.kg,
                        source = DataSource.JEFIT_IMPORT,
                        fatPercent = bw.fatPercent,
                        chestCm = bw.chestCm,
                        armsCm = bw.armsCm,
                        waistCm = bw.waistCm,
                        hipsCm = bw.hipsCm,
                        thighsCm = bw.thighsCm,
                        calvesCm = bw.calvesCm,
                        shouldersCm = bw.shouldersCm,
                        neckCm = bw.neckCm,
                        forearmsCm = bw.forearmsCm,
                        flaggedOutlier = bw.flaggedOutlier,
                        remoteId = existing?.remoteId ?: java.util.UUID.randomUUID().toString(),
                        updatedAt = now,
                        syncState = SyncState.PENDING_UPLOAD
                    )
                )
            }

            val routineDao = database.routineDao()
            for (r in bundle.routines) {
                val existingR = routineDao.findRoutineByExternalId(DataSource.JEFIT_IMPORT, r.externalId)
                val routineId = if (existingR != null) {
                    routineDao.updateRoutine(existingR.copy(name = r.name, updatedAt = now))
                    existingR.id
                } else {
                    routineDao.insertRoutine(
                        RoutineEntity(
                            name = r.name,
                            source = DataSource.JEFIT_IMPORT,
                            externalId = r.externalId,
                            updatedAt = now
                        )
                    )
                }
                for (day in r.days) {
                    val existingDay = routineDao.findDayByExternalId(DataSource.JEFIT_IMPORT, day.externalId)
                    val dayId = if (existingDay != null) {
                        routineDao.updateDay(
                            existingDay.copy(
                                routineId = routineId,
                                name = day.name,
                                orderIndex = day.orderIndex,
                                updatedAt = now
                            )
                        )
                        existingDay.id
                    } else {
                        routineDao.insertDay(
                            RoutineDayEntity(
                                routineId = routineId,
                                name = day.name,
                                orderIndex = day.orderIndex,
                                source = DataSource.JEFIT_IMPORT,
                                externalId = day.externalId,
                                updatedAt = now
                            )
                        )
                    }
                    routineDayIdByExtId[day.externalId] = dayId
                    routineDao.deleteDayExercises(dayId)
                    val dayExercises = day.exercises.mapNotNull { de ->
                        val exId = catalogBySlug[de.exerciseSlug]?.id ?: return@mapNotNull null
                        RoutineDayExerciseEntity(
                            routineDayId = dayId,
                            exerciseId = exId,
                            orderIndex = de.orderIndex,
                            targetSets = de.targetSets,
                            targetReps = de.targetReps,
                            restSeconds = de.restSeconds,
                            supersetGroup = de.supersetGroup,
                            updatedAt = now
                        )
                    }
                    if (dayExercises.isNotEmpty()) {
                        routineDao.insertDayExercises(dayExercises)
                    }
                }
            }

            val workoutDao = database.workoutDao()
            val total = bundle.workouts.size.coerceAtLeast(1)
            bundle.workouts.forEachIndexed { idx, w ->
                val existingW = workoutDao.findByExternalId(DataSource.JEFIT_IMPORT, w.externalId)
                val routineDayId = w.routineDayExternalId?.let { routineDayIdByExtId[it] }
                val workoutId = if (existingW != null) {
                    workoutDao.updateWorkout(
                        existingW.copy(
                            startedAt = w.startedAt,
                            endedAt = w.endedAt,
                            routineDayId = routineDayId,
                            title = w.title ?: existingW.title,
                            excludedFromStats = w.excludedFromStats,
                            isInProgress = false,
                            updatedAt = now,
                            syncState = SyncState.PENDING_UPLOAD
                        )
                    )
                    existingW.id
                } else {
                    workoutDao.insertWorkout(
                        WorkoutEntity(
                            startedAt = w.startedAt,
                            endedAt = w.endedAt,
                            routineDayId = routineDayId,
                            title = w.title,
                            source = DataSource.JEFIT_IMPORT,
                            externalId = w.externalId,
                            excludedFromStats = w.excludedFromStats,
                            isInProgress = false,
                            hcStatus = HcStatus.NOT_ATTEMPTED,
                            updatedAt = now,
                            syncState = SyncState.PENDING_UPLOAD
                        )
                    )
                }
                // Idempotent overwrite of imported exercises/sets for this workout
                workoutDao.deleteWorkoutExercisesForWorkout(workoutId)
                for (ex in w.exercises) {
                    val exerciseId = catalogBySlug[ex.exerciseSlug]?.id ?: continue
                    val weId = workoutDao.insertWorkoutExercise(
                        WorkoutExerciseEntity(
                            workoutId = workoutId,
                            exerciseId = exerciseId,
                            orderIndex = ex.orderIndex,
                            note = ex.note,
                            updatedAt = now
                        )
                    )
                    val setEntities = ex.sets.map { s ->
                        WorkoutSetEntity(
                            workoutExerciseId = weId,
                            setIndex = s.setIndex,
                            weightKg = s.weightKg,
                            reps = s.reps,
                            durationSec = s.durationSec,
                            distanceKm = s.distanceKm,
                            calories = s.calories,
                            setType = s.setType,
                            completedAt = s.completedAt,
                            isEstimatedTime = s.isEstimatedTime,
                            updatedAt = now
                        )
                    }
                    if (setEntities.isNotEmpty()) {
                        workoutDao.insertWorkoutSets(setEntities)
                    }
                }
                importedWorkoutIds += workoutId
                if (idx % 20 == 0) {
                    onProgress((idx.toFloat() / total) * 0.7f)
                }
            }

            database.importRunDao().insert(
                ImportRunEntity(
                    importedAt = now,
                    fileName = fileName,
                    reportJson = json.encodeToString(bundle.report)
                )
            )
        }

        // Compute workout & set metrics outside transaction in chunks
        val count = importedWorkoutIds.size.coerceAtLeast(1)
        importedWorkoutIds.forEachIndexed { idx, wId ->
            metricsRecomputer.recompute(wId)
            if (idx % 15 == 0) {
                onProgress(0.7f + (idx.toFloat() / count) * 0.3f)
            }
        }
        onProgress(1.0f)
        bundle.report
    }
}
