package com.gymtracker.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineDayEntity::class,
        RoutineDayExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        HrSampleEntity::class,
        CalorieBucketEntity::class,
        WorkoutMetricsEntity::class,
        SetMetricsEntity::class,
        ExerciseBlockMetricsEntity::class,
        BodyWeightEntity::class,
        ImportRunEntity::class,
        SupplementItemEntity::class,
        SupplementScheduleEntity::class,
        SupplementLogEntity::class,
        LabReportEntity::class,
        LabMarkerEntity::class,
        DailyVitalsEntity::class,
        HealthEventEntity::class,
        BodyPainLogEntity::class,
        PeriodicReportEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class GymTrackerDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun metricsDao(): MetricsDao
    abstract fun bodyWeightDao(): BodyWeightDao
    abstract fun importRunDao(): ImportRunDao
    abstract fun supplementDao(): SupplementDao
}
