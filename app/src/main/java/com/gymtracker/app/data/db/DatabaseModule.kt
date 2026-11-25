package com.gymtracker.app.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GymTrackerDatabase {
        return Room.databaseBuilder(
            context,
            GymTrackerDatabase::class.java,
            "gym_tracker.db"
        ).build()
    }

    @Provides fun provideExerciseDao(db: GymTrackerDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideRoutineDao(db: GymTrackerDatabase): RoutineDao = db.routineDao()
    @Provides fun provideWorkoutDao(db: GymTrackerDatabase): WorkoutDao = db.workoutDao()
    @Provides fun provideMetricsDao(db: GymTrackerDatabase): MetricsDao = db.metricsDao()
    @Provides fun provideBodyWeightDao(db: GymTrackerDatabase): BodyWeightDao = db.bodyWeightDao()
    @Provides fun provideImportRunDao(db: GymTrackerDatabase): ImportRunDao = db.importRunDao()
    @Provides fun provideSupplementDao(db: GymTrackerDatabase): SupplementDao = db.supplementDao()
}
