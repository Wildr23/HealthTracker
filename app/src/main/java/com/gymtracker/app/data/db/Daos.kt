package com.gymtracker.app.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

data class WorkoutExerciseWithSets(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<WorkoutSetEntity>,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val blockMetrics: ExerciseBlockMetricsEntity?
)

data class WorkoutFull(
    @Embedded val workout: WorkoutEntity,
    @Relation(
        entity = WorkoutExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "workoutId"
    )
    val exercises: List<WorkoutExerciseWithSets>,
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val metrics: WorkoutMetricsEntity?
)

data class RoutineDayExerciseWithExercise(
    @Embedded val dayExercise: RoutineDayExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity
)

data class RoutineDayWithExercises(
    @Embedded val day: RoutineDayEntity,
    @Relation(
        entity = RoutineDayExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "routineDayId"
    )
    val exercises: List<RoutineDayExerciseWithExercise>
)

data class RoutineWithDays(
    @Embedded val routine: RoutineEntity,
    @Relation(
        entity = RoutineDayEntity::class,
        parentColumn = "id",
        entityColumn = "routineId"
    )
    val days: List<RoutineDayWithExercises>
)

data class ExerciseSessionRow(
    val workoutId: Long,
    val workoutStartedAt: Instant,
    val workoutTitle: String?,
    val workoutExerciseId: Long,
    val note: String?,
    val blockAvgHr: Int?,
    val blockMaxHr: Int?
)

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL AND isArchived = 0 ORDER BY nameEn ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL")
    suspend fun getAllOnce(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: Long): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(entity: ExerciseEntity): Long

    @Update
    suspend fun update(entity: ExerciseEntity)

    @Query(
        """
        SELECT exerciseId, COUNT(*) as cnt
        FROM workout_exercises we
        INNER JOIN workouts w ON w.id = we.workoutId
        WHERE w.excludedFromStats = 0 AND w.deletedAt IS NULL AND we.deletedAt IS NULL
        GROUP BY exerciseId
        """
    )
    fun observeUsageCounts(): Flow<List<ExerciseUsageCount>>
}

data class ExerciseUsageCount(
    val exerciseId: Long,
    val cnt: Int
)

@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines WHERE deletedAt IS NULL ORDER BY id DESC")
    fun observeRoutinesWithDays(): Flow<List<RoutineWithDays>>

    @Transaction
    @Query("SELECT * FROM routine_days WHERE id = :dayId")
    suspend fun getDayWithExercises(dayId: Long): RoutineDayWithExercises?

    @Query("SELECT * FROM routines WHERE source = :source AND externalId = :externalId LIMIT 1")
    suspend fun findRoutineByExternalId(source: DataSource, externalId: String): RoutineEntity?

    @Query("SELECT * FROM routine_days WHERE source = :source AND externalId = :externalId LIMIT 1")
    suspend fun findDayByExternalId(source: DataSource, externalId: String): RoutineDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutine(routine: RoutineEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: RoutineDayEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDay(day: RoutineDayEntity): Long

    @Update
    suspend fun updateDay(day: RoutineDayEntity)

    @Query("DELETE FROM routine_day_exercises WHERE routineDayId = :dayId")
    suspend fun deleteDayExercises(dayId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDayExercises(items: List<RoutineDayExerciseEntity>)

    @Query("SELECT * FROM routines WHERE deletedAt IS NULL")
    suspend fun getAllRoutinesOnce(): List<RoutineEntity>

    @Query("SELECT * FROM routine_days WHERE deletedAt IS NULL")
    suspend fun getAllDaysOnce(): List<RoutineDayEntity>

    @Query("SELECT * FROM routine_day_exercises WHERE deletedAt IS NULL")
    suspend fun getAllDayExercisesOnce(): List<RoutineDayExerciseEntity>

    @Query("SELECT * FROM routines WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findRoutineByRemoteId(remoteId: String): RoutineEntity?

    @Query("SELECT * FROM routine_days WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findDayByRemoteId(remoteId: String): RoutineDayEntity?

    @Query("SELECT * FROM routine_day_exercises WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findDayExerciseByRemoteId(remoteId: String): RoutineDayExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDayExercise(item: RoutineDayExerciseEntity): Long
}

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts WHERE deletedAt IS NULL AND isInProgress = 0 ORDER BY startedAt DESC")
    fun observeCompletedWorkoutsFull(): Flow<List<WorkoutFull>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE deletedAt IS NULL AND isInProgress = 1 ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveWorkout(): Flow<WorkoutFull?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE deletedAt IS NULL AND isInProgress = 1 ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveWorkoutOnce(): WorkoutFull?

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    fun observeWorkoutFull(workoutId: Long): Flow<WorkoutFull?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    suspend fun getWorkoutFullOnce(workoutId: Long): WorkoutFull?

    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    suspend fun getWorkoutById(workoutId: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE source = :source AND externalId = :externalId LIMIT 1")
    suspend fun findByExternalId(source: DataSource, externalId: String): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE deletedAt IS NULL AND isInProgress = 0 AND hcStatus IN (:statuses)")
    suspend fun getByHcStatus(statuses: List<HcStatus>): List<WorkoutEntity>

    @Query("SELECT COUNT(*) FROM workouts WHERE deletedAt IS NULL AND isInProgress = 0 AND hcStatus = 'PENDING'")
    fun observePendingHcCount(): Flow<Int>

    @Query("SELECT * FROM workouts WHERE deletedAt IS NULL AND isInProgress = 0 AND excludedFromStats = 0 AND startedAt >= :fromInstant ORDER BY startedAt ASC")
    suspend fun getWorkoutsSince(fromInstant: Instant): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE deletedAt IS NULL")
    suspend fun getAllWorkoutsOnce(): List<WorkoutEntity>

    @Query("SELECT * FROM workout_exercises WHERE deletedAt IS NULL")
    suspend fun getAllWorkoutExercisesOnce(): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_sets WHERE deletedAt IS NULL")
    suspend fun getAllWorkoutSetsOnce(): List<WorkoutSetEntity>

    @Query("SELECT * FROM workouts WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findWorkoutByRemoteId(remoteId: String): WorkoutEntity?

    @Query("SELECT * FROM workout_exercises WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findWorkoutExerciseByRemoteId(remoteId: String): WorkoutExerciseEntity?

    @Query("SELECT * FROM workout_sets WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findWorkoutSetByRemoteId(remoteId: String): WorkoutSetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkout(workout: WorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun deleteWorkoutExercisesForWorkout(workoutId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercise(item: WorkoutExerciseEntity): Long

    @Update
    suspend fun updateWorkoutExercise(item: WorkoutExerciseEntity)

    @Query("DELETE FROM workout_exercises WHERE id = :workoutExerciseId")
    suspend fun deleteWorkoutExerciseById(workoutExerciseId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSet(item: WorkoutSetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSets(items: List<WorkoutSetEntity>): List<Long>

    @Update
    suspend fun updateWorkoutSet(item: WorkoutSetEntity)

    @Query("DELETE FROM workout_sets WHERE id = :setId")
    suspend fun deleteWorkoutSetById(setId: Long)

    @Query("DELETE FROM workouts WHERE id = :workoutId")
    suspend fun deleteWorkoutById(workoutId: Long)

    @Query(
        """
        SELECT w.id as workoutId, w.startedAt as workoutStartedAt, w.title as workoutTitle,
               we.id as workoutExerciseId, we.note as note,
               ebm.avgHr as blockAvgHr, ebm.maxHr as blockMaxHr
        FROM workout_exercises we
        INNER JOIN workouts w ON w.id = we.workoutId
        LEFT JOIN exercise_block_metrics ebm ON ebm.workoutExerciseId = we.id
        WHERE we.exerciseId = :exerciseId
          AND w.deletedAt IS NULL AND w.isInProgress = 0 AND w.excludedFromStats = 0
          AND we.deletedAt IS NULL
        ORDER BY w.startedAt ASC
        """
    )
    fun observeExerciseHistoryRows(exerciseId: Long): Flow<List<ExerciseSessionRow>>

    @Query(
        """
        SELECT ws.*
        FROM workout_sets ws
        INNER JOIN workout_exercises we ON we.id = ws.workoutExerciseId
        INNER JOIN workouts w ON w.id = we.workoutId
        WHERE we.exerciseId = :exerciseId
          AND w.deletedAt IS NULL AND w.isInProgress = 0 AND w.excludedFromStats = 0
          AND we.deletedAt IS NULL AND ws.deletedAt IS NULL
        ORDER BY w.startedAt ASC, ws.setIndex ASC
        """
    )
    fun observeSetsForExercise(exerciseId: Long): Flow<List<WorkoutSetEntity>>

    @Query(
        """
        SELECT ws.*
        FROM workout_sets ws
        INNER JOIN workout_exercises we ON we.id = ws.workoutExerciseId
        INNER JOIN workouts w ON w.id = we.workoutId
        WHERE we.exerciseId = :exerciseId
          AND w.deletedAt IS NULL AND w.isInProgress = 0 AND w.excludedFromStats = 0
          AND we.deletedAt IS NULL AND ws.deletedAt IS NULL
        ORDER BY w.startedAt DESC, ws.setIndex ASC
        """
    )
    suspend fun getSetsForExerciseDesc(exerciseId: Long): List<WorkoutSetEntity>

    @Query(
        """
        SELECT ws.*
        FROM workout_sets ws
        INNER JOIN workout_exercises we ON we.id = ws.workoutExerciseId
        INNER JOIN workouts w ON w.id = we.workoutId
        WHERE we.exerciseId = :exerciseId
          AND w.deletedAt IS NULL AND w.isInProgress = 0 AND w.excludedFromStats = 0
          AND we.id = (
              SELECT we2.id FROM workout_exercises we2
              INNER JOIN workouts w2 ON w2.id = we2.workoutId
              WHERE we2.exerciseId = :exerciseId AND w2.isInProgress = 0 AND w2.excludedFromStats = 0 AND w2.deletedAt IS NULL
              ORDER BY w2.startedAt DESC LIMIT 1
          )
        ORDER BY ws.setIndex ASC
        """
    )
    suspend fun getLastCompletedSetsForExercise(exerciseId: Long): List<WorkoutSetEntity>
}

@Dao
interface MetricsDao {
    @Query("SELECT * FROM hr_samples WHERE workoutId = :workoutId ORDER BY time ASC")
    fun observeHrSamples(workoutId: Long): Flow<List<HrSampleEntity>>

    @Query("SELECT * FROM hr_samples WHERE workoutId = :workoutId ORDER BY time ASC")
    suspend fun getHrSamplesOnce(workoutId: Long): List<HrSampleEntity>

    @Query("DELETE FROM hr_samples WHERE workoutId = :workoutId")
    suspend fun deleteHrSamples(workoutId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHrSamples(samples: List<HrSampleEntity>)

    @Query("SELECT * FROM calorie_buckets WHERE workoutId = :workoutId ORDER BY startTime ASC")
    suspend fun getCalorieBucketsOnce(workoutId: Long): List<CalorieBucketEntity>

    @Query("DELETE FROM calorie_buckets WHERE workoutId = :workoutId")
    suspend fun deleteCalorieBuckets(workoutId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalorieBuckets(buckets: List<CalorieBucketEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkoutMetrics(metrics: WorkoutMetricsEntity)

    @Query("SELECT * FROM workout_metrics WHERE workoutId = :workoutId")
    suspend fun getWorkoutMetrics(workoutId: Long): WorkoutMetricsEntity?

    @Query("SELECT * FROM workout_metrics")
    suspend fun getAllWorkoutMetricsOnce(): List<WorkoutMetricsEntity>

    @Query("SELECT * FROM hr_samples ORDER BY workoutId ASC, time ASC")
    suspend fun getAllHrSamplesOnce(): List<HrSampleEntity>

    @Query("SELECT * FROM calorie_buckets ORDER BY workoutId ASC, startTime ASC")
    suspend fun getAllCalorieBucketsOnce(): List<CalorieBucketEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSetMetrics(metrics: List<SetMetricsEntity>)

    @Query(
        """
        SELECT sm.* FROM set_metrics sm
        INNER JOIN workout_sets ws ON ws.id = sm.setId
        INNER JOIN workout_exercises we ON we.id = ws.workoutExerciseId
        WHERE we.workoutId = :workoutId
        """
    )
    fun observeSetMetricsForWorkout(workoutId: Long): Flow<List<SetMetricsEntity>>

    @Query(
        """
        SELECT sm.* FROM set_metrics sm
        INNER JOIN workout_sets ws ON ws.id = sm.setId
        INNER JOIN workout_exercises we ON we.id = ws.workoutExerciseId
        WHERE we.exerciseId = :exerciseId
        """
    )
    fun observeSetMetricsForExercise(exerciseId: Long): Flow<List<SetMetricsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExerciseBlockMetrics(metrics: List<ExerciseBlockMetricsEntity>)
}

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weights WHERE deletedAt IS NULL ORDER BY date DESC")
    fun observeAll(): Flow<List<BodyWeightEntity>>

    @Query("SELECT * FROM body_weights WHERE deletedAt IS NULL ORDER BY date ASC")
    suspend fun getAllAsc(): List<BodyWeightEntity>

    @Query("SELECT * FROM body_weights WHERE deletedAt IS NULL AND flaggedOutlier = 0 ORDER BY date DESC LIMIT 1")
    suspend fun getLatestValid(): BodyWeightEntity?

    @Query("SELECT * FROM body_weights WHERE date = :date AND source = :source LIMIT 1")
    suspend fun findByDateAndSource(date: LocalDate, source: DataSource): BodyWeightEntity?

    @Query("SELECT * FROM body_weights WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: String): BodyWeightEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BodyWeightEntity): Long
}

@Dao
interface ImportRunDao {
    @Query("SELECT * FROM import_runs ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<ImportRunEntity>>

    @Insert
    suspend fun insert(run: ImportRunEntity): Long
}

data class SupplementWithSchedules(
    @Embedded val item: SupplementItemEntity,
    @Relation(parentColumn = "id", entityColumn = "supplementId")
    val schedules: List<SupplementScheduleEntity>
)

@Dao
interface SupplementDao {
    @Transaction
    @Query("SELECT * FROM supplement_items WHERE deletedAt IS NULL ORDER BY id ASC")
    fun observeActiveWithSchedules(): Flow<List<SupplementWithSchedules>>

    @Transaction
    @Query("SELECT * FROM supplement_items WHERE deletedAt IS NULL ORDER BY id ASC")
    suspend fun getActiveWithSchedulesOnce(): List<SupplementWithSchedules>

    @Query("SELECT * FROM supplement_items ORDER BY id ASC")
    suspend fun getAllItemsOnce(): List<SupplementItemEntity>

    @Query("SELECT * FROM supplement_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): SupplementItemEntity?

    @Query("SELECT * FROM supplement_items WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findItemByRemoteId(remoteId: String): SupplementItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: SupplementItemEntity): Long

    @Query("SELECT * FROM supplement_schedules ORDER BY id ASC")
    suspend fun getAllSchedulesOnce(): List<SupplementScheduleEntity>

    @Query("SELECT * FROM supplement_schedules WHERE supplementId = :supplementId AND deletedAt IS NULL")
    suspend fun getSchedulesForItem(supplementId: Long): List<SupplementScheduleEntity>

    @Query("SELECT * FROM supplement_schedules WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findScheduleByRemoteId(remoteId: String): SupplementScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: SupplementScheduleEntity): Long

    @Query("UPDATE supplement_schedules SET deletedAt = :now, updatedAt = :now, syncState = 'PENDING_UPLOAD' WHERE supplementId = :supplementId AND deletedAt IS NULL")
    suspend fun softDeleteSchedulesForItem(supplementId: Long, now: Instant)

    @Query("SELECT * FROM supplement_logs WHERE deletedAt IS NULL AND takenAt >= :since ORDER BY takenAt DESC")
    fun observeLogsSince(since: Instant): Flow<List<SupplementLogEntity>>

    @Query("SELECT * FROM supplement_logs ORDER BY takenAt DESC")
    suspend fun getAllLogsOnce(): List<SupplementLogEntity>

    @Query("SELECT * FROM supplement_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): SupplementLogEntity?

    @Query("SELECT * FROM supplement_logs WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findLogByRemoteId(remoteId: String): SupplementLogEntity?

    @Query(
        """
        SELECT sl.* FROM supplement_logs sl
        INNER JOIN supplement_items si ON si.id = sl.supplementId
        WHERE sl.deletedAt IS NULL AND si.deletedAt IS NULL
          AND si.category = 'PRE_WORKOUT' AND sl.takenAt >= :since
        ORDER BY sl.takenAt DESC LIMIT 1
        """
    )
    fun observeRecentPreWorkoutLog(since: Instant): Flow<SupplementLogEntity?>

    @Query(
        """
        SELECT sl.* FROM supplement_logs sl
        INNER JOIN supplement_items si ON si.id = sl.supplementId
        WHERE sl.deletedAt IS NULL AND si.deletedAt IS NULL
          AND si.category = 'PRE_WORKOUT' AND sl.takenAt >= :since
        ORDER BY sl.takenAt DESC LIMIT 1
        """
    )
    suspend fun findRecentPreWorkoutLog(since: Instant): SupplementLogEntity?

    @Query(
        """
        SELECT sl.* FROM supplement_logs sl
        INNER JOIN supplement_items si ON si.id = sl.supplementId
        WHERE sl.deletedAt IS NULL AND si.category = 'PRE_WORKOUT'
          AND sl.linkedWorkoutId = :workoutId
        ORDER BY sl.takenAt DESC
        """
    )
    suspend fun findPreWorkoutLogsForWorkout(workoutId: Long): List<SupplementLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: SupplementLogEntity): Long
}
