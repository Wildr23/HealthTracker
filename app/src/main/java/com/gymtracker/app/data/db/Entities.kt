package com.gymtracker.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity(
    tableName = "exercises",
    indices = [
        Index(value = ["slug"], unique = true),
        Index(value = ["jefitEid"])
    ]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slug: String,
    val nameEn: String,
    val nameUk: String,
    val type: ExerciseType,
    val primaryMuscle: Muscle,
    val secondaryMuscles: List<Muscle> = emptyList(),
    val calorieTier: CalorieTier,
    val jefitEid: Int? = null,
    val jefitNameMatch: String? = null,
    val isArchived: Boolean = false,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(
    tableName = "routines",
    indices = [Index(value = ["source", "externalId"], unique = true)]
)
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: DataSource,
    val externalId: String? = null,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(
    tableName = "routine_days",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["routineId"]),
        Index(value = ["source", "externalId"], unique = true)
    ]
)
data class RoutineDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val name: String,
    val orderIndex: Int,
    val source: DataSource,
    val externalId: String? = null,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(
    tableName = "routine_day_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineDayId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["routineDayId"]),
        Index(value = ["exerciseId"])
    ]
)
data class RoutineDayExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineDayId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val targetSets: Int,
    val targetReps: String? = null,
    val restSeconds: Int? = null,
    val supersetGroup: Int? = null,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(
    tableName = "workouts",
    indices = [
        Index(value = ["source", "externalId"], unique = true),
        Index(value = ["startedAt"]),
        Index(value = ["hcStatus"])
    ]
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val routineDayId: Long? = null,
    val title: String? = null,
    val source: DataSource,
    val externalId: String? = null,
    val notes: String? = null,
    val tookPreWorkout: Boolean = false,
    val excludedFromStats: Boolean = false,
    val isInProgress: Boolean = false,
    val hcStatus: HcStatus = HcStatus.NOT_ATTEMPTED,
    val hcSessionId: String? = null,
    val hcSessionStart: Instant? = null,
    val hcSessionEnd: Instant? = null,
    val hcExerciseType: String? = null,
    val hcLastAttemptAt: Instant? = null,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["workoutId"]),
        Index(value = ["exerciseId"])
    ]
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val note: String? = null,
    val supersetGroup: Int? = null,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["workoutExerciseId"]),
        Index(value = ["completedAt"])
    ]
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val setIndex: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val rpe: Float? = null,
    val rir: Int? = null,
    val durationSec: Int? = null,
    val distanceKm: Double? = null,
    val calories: Double? = null,
    val setType: SetType = SetType.WORK,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val isEstimatedTime: Boolean = false,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(
    tableName = "hr_samples",
    primaryKeys = ["workoutId", "time"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workoutId"])]
)
data class HrSampleEntity(
    val workoutId: Long,
    val time: Instant,
    val bpm: Int
)

@Entity(
    tableName = "calorie_buckets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workoutId"])]
)
data class CalorieBucketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val startTime: Instant,
    val endTime: Instant,
    val kcal: Double,
    val kind: CalorieKind
)

@Entity(
    tableName = "workout_metrics",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WorkoutMetricsEntity(
    @PrimaryKey val workoutId: Long,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val minHr: Int? = null,
    val kcalWatch: Double = 0.0,
    val kcalAnaerobicDelta: Double = 0.0,
    val kcalEpoc: Double = 0.0,
    val kcalSmartTotal: Double = 0.0,
    val kcalActive: Double? = null,
    val zoneSeconds: IntArray = intArrayOf(0, 0, 0, 0, 0),
    val edwardsTrimp: Double = 0.0,
    val volumeKg: Double = 0.0,
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    val durationSec: Int = 0,
    val hasWatchCalories: Boolean = false
)

@Entity(
    tableName = "set_metrics",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SetMetricsEntity(
    @PrimaryKey val setId: Long,
    val peakHr: Int? = null,
    val endHr: Int? = null,
    val restSec: Int? = null,
    val restAvgHr: Int? = null,
    val hrRecovery60: Int? = null,
    val sampleCount: Int = 0,
    val e1rmEpley: Double? = null,
    val e1rmBrzycki: Double? = null,
    val e1rmLombardi: Double? = null
)

@Entity(
    tableName = "exercise_block_metrics",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ExerciseBlockMetricsEntity(
    @PrimaryKey val workoutExerciseId: Long,
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val kcalWatch: Double = 0.0,
    val kcalSmart: Double = 0.0
)

@Entity(
    tableName = "body_weights",
    indices = [Index(value = ["date", "source"], unique = true)]
)
data class BodyWeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val kg: Double,
    val source: DataSource,
    val fatPercent: Double? = null,
    val chestCm: Double? = null,
    val armsCm: Double? = null,
    val waistCm: Double? = null,
    val hipsCm: Double? = null,
    val thighsCm: Double? = null,
    val calvesCm: Double? = null,
    val shouldersCm: Double? = null,
    val neckCm: Double? = null,
    val forearmsCm: Double? = null,
    val flaggedOutlier: Boolean = false,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(tableName = "import_runs")
data class ImportRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val importedAt: Instant,
    val fileName: String,
    val reportJson: String
)

// =========================================================================
// Phase 2-4 Forward-Compatible Tables (Schema seeded from v1)
// =========================================================================

@Entity(tableName = "supplement_items")
data class SupplementItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nameEn: String,
    val nameUk: String,
    val category: SupplementCategory,
    val defaultDose: Double,
    val unit: SupplementUnit,
    val caffeineMgPerServing: Int? = null,
    val servingsRemaining: Double? = null,
    val lowStockThreshold: Double? = null,
    val cycleDaysOn: Int? = null,
    val cycleDaysOff: Int? = null,
    val cycleStartDate: LocalDate? = null,
    val notes: String? = null,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(
    tableName = "supplement_schedules",
    foreignKeys = [
        ForeignKey(
            entity = SupplementItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplementId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["supplementId"])]
)
data class SupplementScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplementId: Long,
    val triggerType: SupplementTrigger,
    val timeOfDay: LocalTime? = null,
    val offsetMinutes: Int? = null,
    val daysOfWeekMask: Int = 0b1111111,
    val enabled: Boolean = true,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(
    tableName = "supplement_logs",
    foreignKeys = [
        ForeignKey(
            entity = SupplementItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplementId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["supplementId"]), Index(value = ["takenAt"])]
)
data class SupplementLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplementId: Long,
    val takenAt: Instant,
    val dose: Double,
    val caffeineMg: Int? = null,
    val linkedWorkoutId: Long? = null,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(tableName = "lab_reports")
data class LabReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportDate: LocalDate,
    val labName: String? = null,
    val notes: String? = null,
    val attachmentPath: String? = null,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(
    tableName = "lab_markers",
    foreignKeys = [
        ForeignKey(
            entity = LabReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["labReportId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["labReportId"]), Index(value = ["markerCode"])]
)
data class LabMarkerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val labReportId: Long,
    val markerCode: String,
    val markerName: String,
    val value: Double,
    val unit: String,
    val labRefMin: Double? = null,
    val labRefMax: Double? = null,
    val athleteOptMin: Double? = null,
    val athleteOptMax: Double? = null,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(
    tableName = "daily_vitals",
    indices = [Index(value = ["date"], unique = true)]
)
data class DailyVitalsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val sleepMinutes: Int? = null,
    val restingHr: Int? = null,
    val hrvMs: Double? = null,
    val spo2Avg: Double? = null,
    val wristTempDeltaC: Double? = null,
    val vascularAge: Int? = null,
    val stressScore: Int? = null,
    val steps: Int? = null,
    val activeKcal: Double? = null,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(tableName = "health_events")
data class HealthEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: HealthEventType,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val severity: Int? = null,
    val symptoms: String? = null,
    val medications: String? = null,
    val notes: String? = null,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(tableName = "body_pain_logs")
data class BodyPainLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loggedAt: Instant,
    val bodyZone: BodyZone,
    val painLevel1To10: Int,
    val note: String? = null,
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)

@Entity(tableName = "periodic_reports")
data class PeriodicReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodType: ReportPeriodType,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val metricsJson: String,
    val aiSummaryMarkdown: String,
    val actionItemsJson: String,
    val createdAt: Instant = Instant.now(),
    val remoteId: String = UUID.randomUUID().toString(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
    val syncState: SyncState = SyncState.PENDING_UPLOAD
)
