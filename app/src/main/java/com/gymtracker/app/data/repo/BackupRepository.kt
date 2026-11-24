package com.gymtracker.app.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.gymtracker.app.data.db.BodyWeightEntity
import com.gymtracker.app.data.db.CalorieTier
import com.gymtracker.app.data.db.DataSource
import com.gymtracker.app.data.db.ExerciseCatalogSeeder
import com.gymtracker.app.data.db.ExerciseEntity
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.GymTrackerDatabase
import com.gymtracker.app.data.db.HcStatus
import com.gymtracker.app.data.db.Muscle
import com.gymtracker.app.data.db.RoutineDayEntity
import com.gymtracker.app.data.db.RoutineDayExerciseEntity
import com.gymtracker.app.data.db.RoutineEntity
import com.gymtracker.app.data.db.SetType
import com.gymtracker.app.data.db.WorkoutEntity
import com.gymtracker.app.data.db.WorkoutExerciseEntity
import com.gymtracker.app.data.db.WorkoutSetEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAtMillis: Long,
    val exercises: List<BackupExercise>,
    val routines: List<BackupRoutine>,
    val bodyWeights: List<BackupBodyWeight>,
    val workouts: List<BackupWorkout>
)

@Serializable
data class BackupExercise(
    val slug: String,
    val nameEn: String,
    val nameUk: String,
    val type: ExerciseType,
    val primaryMuscle: Muscle,
    val secondaryMuscles: List<Muscle>,
    val calorieTier: CalorieTier,
    val jefitEid: Int? = null,
    val jefitNameMatch: String? = null
)

@Serializable
data class BackupRoutine(
    val name: String,
    val source: DataSource,
    val externalId: String? = null,
    val days: List<BackupRoutineDay>
)

@Serializable
data class BackupRoutineDay(
    val name: String,
    val orderIndex: Int,
    val source: DataSource,
    val externalId: String? = null,
    val exercises: List<BackupRoutineDayExercise>
)

@Serializable
data class BackupRoutineDayExercise(
    val exerciseSlug: String,
    val orderIndex: Int,
    val targetSets: Int,
    val targetReps: String? = null,
    val restSeconds: Int? = null,
    val supersetGroup: Int? = null
)

@Serializable
data class BackupBodyWeight(
    val date: String,
    val kg: Double,
    val source: DataSource,
    val flaggedOutlier: Boolean
)

@Serializable
data class BackupWorkout(
    val remoteId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val title: String? = null,
    val source: DataSource,
    val externalId: String? = null,
    val notes: String? = null,
    val tookPreWorkout: Boolean,
    val excludedFromStats: Boolean,
    val hcStatus: HcStatus,
    val hcSessionId: String? = null,
    val exercises: List<BackupWorkoutExercise>
)

@Serializable
data class BackupWorkoutExercise(
    val exerciseSlug: String,
    val orderIndex: Int,
    val note: String? = null,
    val supersetGroup: Int? = null,
    val sets: List<BackupWorkoutSet>
)

@Serializable
data class BackupWorkoutSet(
    val setIndex: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val rpe: Float? = null,
    val durationSec: Int? = null,
    val distanceKm: Double? = null,
    val calories: Double? = null,
    val setType: SetType,
    val completedAtMillis: Long? = null
)

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: GymTrackerDatabase,
    private val seeder: ExerciseCatalogSeeder,
    private val metricsRecomputer: WorkoutMetricsRecomputer
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportJsonToUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val payload = buildBackupPayload()
        val encoded = json.encodeToString(payload)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.bufferedWriter(Charsets.UTF_8).use { it.write(encoded) }
        } ?: error("Cannot write backup file")
        payload.workouts.size
    }

    suspend fun buildBackupPayload(): BackupPayload {
        val exercises = database.exerciseDao().getAllOnce()
        val exById = exercises.associateBy { it.id }
        val routines = database.routineDao().getAllRoutinesOnce()
        val daysByRoutine = database.routineDao().getAllDaysOnce().groupBy { it.routineId }
        val dayExByDay = database.routineDao().getAllDayExercisesOnce().groupBy { it.routineDayId }
        val bodyWeights = database.bodyWeightDao().getAllAsc()

        val workouts = database.workoutDao().getAllWorkoutsOnce().filter { !it.isInProgress }
        val weByWorkout = database.workoutDao().getAllWorkoutExercisesOnce().groupBy { it.workoutId }
        val setsByWe = database.workoutDao().getAllWorkoutSetsOnce().groupBy { it.workoutExerciseId }

        return BackupPayload(
            exportedAtMillis = Instant.now().toEpochMilli(),
            exercises = exercises.map {
                BackupExercise(
                    slug = it.slug,
                    nameEn = it.nameEn,
                    nameUk = it.nameUk,
                    type = it.type,
                    primaryMuscle = it.primaryMuscle,
                    secondaryMuscles = it.secondaryMuscles,
                    calorieTier = it.calorieTier,
                    jefitEid = it.jefitEid,
                    jefitNameMatch = it.jefitNameMatch
                )
            },
            routines = routines.map { r ->
                BackupRoutine(
                    name = r.name,
                    source = r.source,
                    externalId = r.externalId,
                    days = daysByRoutine[r.id].orEmpty().sortedBy { it.orderIndex }.map { d ->
                        BackupRoutineDay(
                            name = d.name,
                            orderIndex = d.orderIndex,
                            source = d.source,
                            externalId = d.externalId,
                            exercises = dayExByDay[d.id].orEmpty().sortedBy { it.orderIndex }.mapNotNull { de ->
                                val slug = exById[de.exerciseId]?.slug ?: return@mapNotNull null
                                BackupRoutineDayExercise(
                                    exerciseSlug = slug,
                                    orderIndex = de.orderIndex,
                                    targetSets = de.targetSets,
                                    targetReps = de.targetReps,
                                    restSeconds = de.restSeconds,
                                    supersetGroup = de.supersetGroup
                                )
                            }
                        )
                    }
                )
            },
            bodyWeights = bodyWeights.map {
                BackupBodyWeight(it.date.toString(), it.kg, it.source, it.flaggedOutlier)
            },
            workouts = workouts.map { w ->
                BackupWorkout(
                    remoteId = w.remoteId,
                    startedAtMillis = w.startedAt.toEpochMilli(),
                    endedAtMillis = w.endedAt?.toEpochMilli(),
                    title = w.title,
                    source = w.source,
                    externalId = w.externalId,
                    notes = w.notes,
                    tookPreWorkout = w.tookPreWorkout,
                    excludedFromStats = w.excludedFromStats,
                    hcStatus = w.hcStatus,
                    hcSessionId = w.hcSessionId,
                    exercises = weByWorkout[w.id].orEmpty().sortedBy { it.orderIndex }.mapNotNull { we ->
                        val slug = exById[we.exerciseId]?.slug ?: return@mapNotNull null
                        BackupWorkoutExercise(
                            exerciseSlug = slug,
                            orderIndex = we.orderIndex,
                            note = we.note,
                            supersetGroup = we.supersetGroup,
                            sets = setsByWe[we.id].orEmpty().sortedBy { it.setIndex }.map { s ->
                                BackupWorkoutSet(
                                    setIndex = s.setIndex,
                                    weightKg = s.weightKg,
                                    reps = s.reps,
                                    rpe = s.rpe,
                                    durationSec = s.durationSec,
                                    distanceKm = s.distanceKm,
                                    calories = s.calories,
                                    setType = s.setType,
                                    completedAtMillis = s.completedAt?.toEpochMilli()
                                )
                            }
                        )
                    }
                )
            }
        )
    }

    suspend fun importJsonFromUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("Cannot open backup file")
        restoreBackupPayload(json.decodeFromString(raw))
    }

    suspend fun restoreBackupPayload(payload: BackupPayload): Int {
        seeder.ensureSeeded()
        val now = Instant.now()
        val restoredWorkoutIds = mutableListOf<Long>()

        database.withTransaction {
            val exDao = database.exerciseDao()
            for (ex in payload.exercises) {
                if (exDao.getBySlug(ex.slug) == null) {
                    exDao.insertIgnore(
                        ExerciseEntity(
                            slug = ex.slug,
                            nameEn = ex.nameEn,
                            nameUk = ex.nameUk,
                            type = ex.type,
                            primaryMuscle = ex.primaryMuscle,
                            secondaryMuscles = ex.secondaryMuscles,
                            calorieTier = ex.calorieTier,
                            jefitEid = ex.jefitEid,
                            jefitNameMatch = ex.jefitNameMatch,
                            updatedAt = now
                        )
                    )
                }
            }
            val exBySlug = exDao.getAllOnce().associateBy { it.slug }

            val bwDao = database.bodyWeightDao()
            for (bw in payload.bodyWeights) {
                val d = LocalDate.parse(bw.date)
                val existing = bwDao.findByDateAndSource(d, bw.source)
                bwDao.upsert(
                    BodyWeightEntity(
                        id = existing?.id ?: 0,
                        date = d,
                        kg = bw.kg,
                        source = bw.source,
                        flaggedOutlier = bw.flaggedOutlier,
                        updatedAt = now
                    )
                )
            }

            val routineDao = database.routineDao()
            for (r in payload.routines) {
                val existingR = r.externalId?.let { routineDao.findRoutineByExternalId(r.source, it) }
                val rId = if (existingR != null) {
                    routineDao.updateRoutine(existingR.copy(name = r.name, updatedAt = now))
                    existingR.id
                } else {
                    routineDao.insertRoutine(RoutineEntity(name = r.name, source = r.source, externalId = r.externalId, updatedAt = now))
                }
                for (d in r.days) {
                    val existingD = d.externalId?.let { routineDao.findDayByExternalId(d.source, it) }
                    val dId = if (existingD != null) {
                        routineDao.updateDay(existingD.copy(name = d.name, orderIndex = d.orderIndex, updatedAt = now))
                        existingD.id
                    } else {
                        routineDao.insertDay(
                            RoutineDayEntity(
                                routineId = rId,
                                name = d.name,
                                orderIndex = d.orderIndex,
                                source = d.source,
                                externalId = d.externalId,
                                updatedAt = now
                            )
                        )
                    }
                    routineDao.deleteDayExercises(dId)
                    val items = d.exercises.mapNotNull { de ->
                        val exId = exBySlug[de.exerciseSlug]?.id ?: return@mapNotNull null
                        RoutineDayExerciseEntity(
                            routineDayId = dId,
                            exerciseId = exId,
                            orderIndex = de.orderIndex,
                            targetSets = de.targetSets,
                            targetReps = de.targetReps,
                            restSeconds = de.restSeconds,
                            supersetGroup = de.supersetGroup,
                            updatedAt = now
                        )
                    }
                    if (items.isNotEmpty()) routineDao.insertDayExercises(items)
                }
            }

            val wDao = database.workoutDao()
            val existingByRemoteId = wDao.getAllWorkoutsOnce().associateBy { it.remoteId }
            for (w in payload.workouts) {
                val existing = existingByRemoteId[w.remoteId]
                    ?: w.externalId?.let { wDao.findByExternalId(w.source, it) }
                val wId = if (existing != null) {
                    wDao.updateWorkout(
                        existing.copy(
                            startedAt = Instant.ofEpochMilli(w.startedAtMillis),
                            endedAt = w.endedAtMillis?.let(Instant::ofEpochMilli),
                            title = w.title,
                            notes = w.notes,
                            tookPreWorkout = w.tookPreWorkout,
                            excludedFromStats = w.excludedFromStats,
                            updatedAt = now
                        )
                    )
                    existing.id
                } else {
                    wDao.insertWorkout(
                        WorkoutEntity(
                            remoteId = w.remoteId,
                            startedAt = Instant.ofEpochMilli(w.startedAtMillis),
                            endedAt = w.endedAtMillis?.let(Instant::ofEpochMilli),
                            title = w.title,
                            source = w.source,
                            externalId = w.externalId,
                            notes = w.notes,
                            tookPreWorkout = w.tookPreWorkout,
                            excludedFromStats = w.excludedFromStats,
                            hcStatus = w.hcStatus,
                            hcSessionId = w.hcSessionId,
                            updatedAt = now
                        )
                    )
                }
                wDao.deleteWorkoutExercisesForWorkout(wId)
                for (we in w.exercises) {
                    val exId = exBySlug[we.exerciseSlug]?.id ?: continue
                    val weId = wDao.insertWorkoutExercise(
                        WorkoutExerciseEntity(
                            workoutId = wId,
                            exerciseId = exId,
                            orderIndex = we.orderIndex,
                            note = we.note,
                            supersetGroup = we.supersetGroup,
                            updatedAt = now
                        )
                    )
                    val setList = we.sets.map { s ->
                        WorkoutSetEntity(
                            workoutExerciseId = weId,
                            setIndex = s.setIndex,
                            weightKg = s.weightKg,
                            reps = s.reps,
                            rpe = s.rpe,
                            durationSec = s.durationSec,
                            distanceKm = s.distanceKm,
                            calories = s.calories,
                            setType = s.setType,
                            completedAt = s.completedAtMillis?.let(Instant::ofEpochMilli),
                            updatedAt = now
                        )
                    }
                    if (setList.isNotEmpty()) wDao.insertWorkoutSets(setList)
                }
                restoredWorkoutIds += wId
            }
        }

        restoredWorkoutIds.forEach { metricsRecomputer.recompute(it) }
        return restoredWorkoutIds.size
    }
}
