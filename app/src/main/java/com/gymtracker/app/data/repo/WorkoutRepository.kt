package com.gymtracker.app.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.gymtracker.app.data.db.BodyWeightEntity
import com.gymtracker.app.data.db.DataSource
import com.gymtracker.app.data.db.ExerciseCatalogSeeder
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.GymTrackerDatabase
import com.gymtracker.app.data.db.HcStatus
import com.gymtracker.app.data.db.SetType
import com.gymtracker.app.data.db.SyncState
import com.gymtracker.app.data.db.WorkoutEntity
import com.gymtracker.app.data.db.WorkoutExerciseEntity
import com.gymtracker.app.data.db.WorkoutFull
import com.gymtracker.app.data.db.WorkoutSetEntity
import com.gymtracker.app.domain.ExerciseSessionInput
import com.gymtracker.app.domain.LivePrHit
import com.gymtracker.app.domain.PrEngine
import com.gymtracker.app.domain.PrSetInput
import com.gymtracker.app.work.MergeWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: GymTrackerDatabase,
    private val seeder: ExerciseCatalogSeeder,
    private val metricsRecomputer: WorkoutMetricsRecomputer
) {
    fun observeCompletedWorkouts(): Flow<List<WorkoutFull>> =
        database.workoutDao().observeCompletedWorkoutsFull()

    fun observeActiveWorkout(): Flow<WorkoutFull?> =
        database.workoutDao().observeActiveWorkout()

    fun observeWorkout(workoutId: Long): Flow<WorkoutFull?> =
        database.workoutDao().observeWorkoutFull(workoutId)

    fun observePendingHcCount(): Flow<Int> =
        database.workoutDao().observePendingHcCount()

    suspend fun startWorkout(routineDayId: Long?, tookPreWorkout: Boolean, customTitle: String? = null): Long =
        withContext(Dispatchers.IO) {
            seeder.ensureSeeded()
            val existing = database.workoutDao().getActiveWorkoutOnce()
            if (existing != null) return@withContext existing.workout.id

            val now = Instant.now()
            val dayWithExercises = routineDayId?.let { database.routineDao().getDayWithExercises(it) }
            val title = customTitle?.takeIf { it.isNotBlank() } ?: dayWithExercises?.day?.name

            val workoutId = database.withTransaction {
                val wId = database.workoutDao().insertWorkout(
                    WorkoutEntity(
                        startedAt = now,
                        routineDayId = routineDayId,
                        title = title,
                        source = DataSource.APP,
                        externalId = null,
                        tookPreWorkout = tookPreWorkout,
                        isInProgress = true,
                        hcStatus = HcStatus.NOT_ATTEMPTED,
                        updatedAt = now,
                        syncState = SyncState.PENDING_UPLOAD
                    )
                )

                val planned = dayWithExercises?.exercises?.sortedBy { it.dayExercise.orderIndex }.orEmpty()
                planned.forEachIndexed { idx, item ->
                    val weId = database.workoutDao().insertWorkoutExercise(
                        WorkoutExerciseEntity(
                            workoutId = wId,
                            exerciseId = item.exercise.id,
                            orderIndex = idx,
                            supersetGroup = item.dayExercise.supersetGroup,
                            updatedAt = now
                        )
                    )
                    val lastSets = database.workoutDao().getLastCompletedSetsForExercise(item.exercise.id)
                    val targetSetCount = item.dayExercise.targetSets.coerceAtLeast(lastSets.size.coerceAtLeast(1))
                    val prefilled = (0 until targetSetCount).map { sIdx ->
                        val prev = lastSets.getOrNull(sIdx) ?: lastSets.lastOrNull()
                        WorkoutSetEntity(
                            workoutExerciseId = weId,
                            setIndex = sIdx,
                            weightKg = prev?.weightKg ?: if (item.exercise.type == ExerciseType.BODYWEIGHT) 0.0 else null,
                            reps = prev?.reps ?: item.dayExercise.targetReps
                                ?.split(",")?.getOrNull(sIdx)?.trim()?.toIntOrNull(),
                            setType = SetType.WORK,
                            completedAt = null,
                            updatedAt = now
                        )
                    }
                    if (prefilled.isNotEmpty()) {
                        database.workoutDao().insertWorkoutSets(prefilled)
                    }
                }
                wId
            }
            workoutId
        }

    suspend fun togglePreWorkout(workoutId: Long, took: Boolean) = withContext(Dispatchers.IO) {
        val w = database.workoutDao().getWorkoutById(workoutId) ?: return@withContext
        database.workoutDao().updateWorkout(
            w.copy(tookPreWorkout = took, updatedAt = Instant.now(), syncState = SyncState.PENDING_UPLOAD)
        )
    }

    suspend fun addExerciseToWorkout(workoutId: Long, exerciseId: Long) = withContext(Dispatchers.IO) {
        val full = database.workoutDao().getWorkoutFullOnce(workoutId) ?: return@withContext
        val exercise = database.exerciseDao().getById(exerciseId) ?: return@withContext
        val nextOrder = (full.exercises.maxOfOrNull { it.workoutExercise.orderIndex } ?: -1) + 1
        val lastSets = database.workoutDao().getLastCompletedSetsForExercise(exerciseId)
        val now = Instant.now()

        database.withTransaction {
            val weId = database.workoutDao().insertWorkoutExercise(
                WorkoutExerciseEntity(
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    orderIndex = nextOrder,
                    updatedAt = now
                )
            )
            val initialSets = if (lastSets.isNotEmpty()) {
                lastSets.mapIndexed { idx, prev ->
                    WorkoutSetEntity(
                        workoutExerciseId = weId,
                        setIndex = idx,
                        weightKg = prev.weightKg,
                        reps = prev.reps,
                        setType = SetType.WORK,
                        completedAt = null,
                        updatedAt = now
                    )
                }
            } else {
                listOf(
                    WorkoutSetEntity(
                        workoutExerciseId = weId,
                        setIndex = 0,
                        weightKg = if (exercise.type == ExerciseType.BODYWEIGHT) 0.0 else null,
                        reps = null,
                        setType = SetType.WORK,
                        completedAt = null,
                        updatedAt = now
                    )
                )
            }
            database.workoutDao().insertWorkoutSets(initialSets)
        }
    }

    suspend fun removeExerciseFromWorkout(workoutExerciseId: Long) = withContext(Dispatchers.IO) {
        database.workoutDao().deleteWorkoutExerciseById(workoutExerciseId)
    }

    suspend fun moveExercise(workoutId: Long, workoutExerciseId: Long, delta: Int) = withContext(Dispatchers.IO) {
        val full = database.workoutDao().getWorkoutFullOnce(workoutId) ?: return@withContext
        val sorted = full.exercises.map { it.workoutExercise }.sortedBy { it.orderIndex }.toMutableList()
        val idx = sorted.indexOfFirst { it.id == workoutExerciseId }
        val target = idx + delta
        if (idx < 0 || target !in sorted.indices) return@withContext
        val moved = sorted.removeAt(idx)
        sorted.add(target, moved)
        val now = Instant.now()
        database.withTransaction {
            sorted.forEachIndexed { i, item ->
                database.workoutDao().updateWorkoutExercise(
                    item.copy(orderIndex = i, updatedAt = now, syncState = SyncState.PENDING_UPLOAD)
                )
            }
        }
    }

    suspend fun updateExerciseNote(workoutExerciseId: Long, note: String?) = withContext(Dispatchers.IO) {
        // Read parent via raw query? Let's get via workout or add helper. Quick safe implementation:
        val allWe = database.workoutDao().getAllWorkoutExercisesOnce()
        val target = allWe.firstOrNull { it.id == workoutExerciseId } ?: return@withContext
        database.workoutDao().updateWorkoutExercise(
            target.copy(note = note?.takeIf { it.isNotBlank() }, updatedAt = Instant.now(), syncState = SyncState.PENDING_UPLOAD)
        )
    }

    suspend fun addSet(workoutExerciseId: Long) = withContext(Dispatchers.IO) {
        val allSets = database.workoutDao().getAllWorkoutSetsOnce()
            .filter { it.workoutExerciseId == workoutExerciseId && it.deletedAt == null }
            .sortedBy { it.setIndex }
        val last = allSets.lastOrNull()
        val nextIdx = (last?.setIndex ?: -1) + 1
        database.workoutDao().insertWorkoutSet(
            WorkoutSetEntity(
                workoutExerciseId = workoutExerciseId,
                setIndex = nextIdx,
                weightKg = last?.weightKg,
                reps = last?.reps,
                setType = SetType.WORK,
                completedAt = null,
                updatedAt = Instant.now()
            )
        )
    }

    suspend fun updateSetValues(
        set: WorkoutSetEntity,
        weightKg: Double?,
        reps: Int?,
        setType: SetType,
        rpe: Float?,
        recomputeWorkoutId: Long? = null
    ) = withContext(Dispatchers.IO) {
        val updated = set.copy(
            weightKg = weightKg,
            reps = reps,
            setType = setType,
            rpe = rpe,
            rir = rpe?.let { (10f - it).toInt().coerceIn(0, 5) },
            updatedAt = Instant.now(),
            syncState = SyncState.PENDING_UPLOAD
        )
        database.workoutDao().updateWorkoutSet(updated)
        if (recomputeWorkoutId != null) {
            metricsRecomputer.recompute(recomputeWorkoutId)
        }
    }

    suspend fun toggleSetCompleted(
        workoutId: Long,
        workoutExerciseId: Long,
        exerciseId: Long,
        set: WorkoutSetEntity,
        weightKg: Double?,
        reps: Int?,
        rpe: Float?,
        setType: SetType
    ): List<LivePrHit> = withContext(Dispatchers.IO) {
        val now = Instant.now()
        val completing = set.completedAt == null
        val updated = set.copy(
            weightKg = weightKg,
            reps = reps,
            rpe = rpe,
            rir = rpe?.let { (10f - it).toInt().coerceIn(0, 5) },
            setType = setType,
            completedAt = if (completing) now else null,
            updatedAt = now,
            syncState = SyncState.PENDING_UPLOAD
        )
        database.workoutDao().updateWorkoutSet(updated)

        if (!completing) return@withContext emptyList()

        val exercise = database.exerciseDao().getById(exerciseId) ?: return@withContext emptyList()
        val fullWorkout = database.workoutDao().getWorkoutFullOnce(workoutId)
        val currentSessionVol = fullWorkout?.exercises
            ?.firstOrNull { it.workoutExercise.id == workoutExerciseId }
            ?.sets
            ?.filter { it.setType != SetType.WARMUP && it.completedAt != null }
            ?.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
            ?: 0.0

        val priorCompleted = database.workoutDao().getCompletedWorkoutsOnceForExercise(exerciseId)
        PrEngine.detectLiveSetPrs(
            exerciseType = exercise.type,
            weightKg = weightKg,
            reps = reps,
            rpe = rpe,
            setType = setType,
            priorSessions = priorCompleted,
            currentSessionWeightVolume = currentSessionVol
        )
    }

    suspend fun deleteSet(setId: Long, recomputeWorkoutId: Long? = null) = withContext(Dispatchers.IO) {
        database.workoutDao().deleteWorkoutSetById(setId)
        if (recomputeWorkoutId != null) {
            metricsRecomputer.recompute(recomputeWorkoutId)
        }
    }

    suspend fun finishWorkout(workoutId: Long): Long = withContext(Dispatchers.IO) {
        val full = database.workoutDao().getWorkoutFullOnce(workoutId) ?: return@withContext workoutId
        val now = Instant.now()
        val hasAnyCompleted = full.exercises.any { ex -> ex.sets.any { it.completedAt != null } }

        database.withTransaction {
            // Remove uncompleted placeholder sets if user completed at least one set; otherwise keep workout cleanly
            if (hasAnyCompleted) {
                for (ex in full.exercises) {
                    val uncompleted = ex.sets.filter { it.completedAt == null }
                    uncompleted.forEach { database.workoutDao().deleteWorkoutSetById(it.id) }
                    if (ex.sets.all { it.completedAt == null }) {
                        database.workoutDao().deleteWorkoutExerciseById(ex.workoutExercise.id)
                    }
                }
            }
            database.workoutDao().updateWorkout(
                full.workout.copy(
                    endedAt = now,
                    isInProgress = false,
                    hcStatus = HcStatus.PENDING,
                    updatedAt = now,
                    syncState = SyncState.PENDING_UPLOAD
                )
            )
        }

        metricsRecomputer.recompute(workoutId)
        MergeWorker.enqueueForWorkout(context, workoutId, initialDelayMinutes = 2)
        workoutId
    }

    suspend fun discardWorkout(workoutId: Long) = withContext(Dispatchers.IO) {
        database.workoutDao().deleteWorkoutById(workoutId)
    }

    suspend fun addManualBodyWeight(date: LocalDate, kg: Double) = withContext(Dispatchers.IO) {
        val existing = database.bodyWeightDao().findByDateAndSource(date, DataSource.APP)
        database.bodyWeightDao().upsert(
            BodyWeightEntity(
                id = existing?.id ?: 0,
                date = date,
                kg = kg,
                source = DataSource.APP,
                flaggedOutlier = false,
                remoteId = existing?.remoteId ?: java.util.UUID.randomUUID().toString(),
                updatedAt = Instant.now()
            )
        )
    }

    suspend fun toggleBodyWeightOutlier(entry: BodyWeightEntity) = withContext(Dispatchers.IO) {
        database.bodyWeightDao().upsert(
            entry.copy(flaggedOutlier = !entry.flaggedOutlier, updatedAt = Instant.now())
        )
    }

    private suspend fun com.gymtracker.app.data.db.WorkoutDao.getCompletedWorkoutsOnceForExercise(
        exerciseId: Long
    ): List<ExerciseSessionInput> {
        val allCompleted = getAllWorkoutsOnce().filter { !it.isInProgress && !it.excludedFromStats && it.deletedAt == null }
        val allWe = getAllWorkoutExercisesOnce().filter { it.exerciseId == exerciseId && it.deletedAt == null }
        val weByWorkout = allWe.groupBy { it.workoutId }
        val allSets = getAllWorkoutSetsOnce().filter { it.deletedAt == null }.groupBy { it.workoutExerciseId }

        return allCompleted.mapNotNull { w ->
            val exList = weByWorkout[w.id] ?: return@mapNotNull null
            val firstWe = exList.first()
            val sets = exList.flatMap { allSets[it.id].orEmpty() }.sortedBy { it.setIndex }
            ExerciseSessionInput(
                workoutId = w.id,
                startedAt = w.startedAt,
                workoutTitle = w.title,
                note = firstWe.note,
                blockAvgHr = null,
                blockMaxHr = null,
                sets = sets.map { s ->
                    PrSetInput(s.id, s.setIndex, s.weightKg, s.reps, s.rpe, s.setType, null)
                }
            )
        }
    }
}
