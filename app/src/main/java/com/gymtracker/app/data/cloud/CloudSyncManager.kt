package com.gymtracker.app.data.cloud

import androidx.room.withTransaction
import com.gymtracker.app.data.db.BodyWeightEntity
import com.gymtracker.app.data.db.CalorieBucketEntity
import com.gymtracker.app.data.db.CalorieKind
import com.gymtracker.app.data.db.CalorieTier
import com.gymtracker.app.data.db.DataSource
import com.gymtracker.app.data.db.ExerciseCatalogSeeder
import com.gymtracker.app.data.db.ExerciseEntity
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.GymTrackerDatabase
import com.gymtracker.app.data.db.HcStatus
import com.gymtracker.app.data.db.HrSampleEntity
import com.gymtracker.app.data.db.Muscle
import com.gymtracker.app.data.db.OneRmFormula
import com.gymtracker.app.data.db.RoutineDayEntity
import com.gymtracker.app.data.db.RoutineDayExerciseEntity
import com.gymtracker.app.data.db.RoutineEntity
import com.gymtracker.app.data.db.SetType
import com.gymtracker.app.data.db.SupplementCategory
import com.gymtracker.app.data.db.SupplementItemEntity
import com.gymtracker.app.data.db.SupplementLogEntity
import com.gymtracker.app.data.db.SupplementScheduleEntity
import com.gymtracker.app.data.db.SupplementTrigger
import com.gymtracker.app.data.db.SupplementUnit
import com.gymtracker.app.data.db.SyncState
import com.gymtracker.app.data.db.WorkoutEntity
import com.gymtracker.app.data.db.WorkoutExerciseEntity
import com.gymtracker.app.data.db.WorkoutSetEntity
import com.gymtracker.app.data.health.WatchActivityRepository
import com.gymtracker.app.data.prefs.UserPreferences
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.WorkoutMetricsRecomputer
import com.gymtracker.app.data.scale.ScaleReadingSnapshot
import com.gymtracker.app.data.scale.ScaleReadingsRepository
import com.gymtracker.app.work.StackReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CloudSyncSummary(
    val workoutsUploaded: Int,
    val setsUploaded: Int,
    val bodyWeightsUploaded: Int,
    val scaleReadingsUploaded: Int
)

data class CloudRestoreSummary(
    val exercisesRestored: Int,
    val workoutsRestored: Int,
    val bodyWeightsRestored: Int,
    val scaleReadingsRestored: Int
)

@Singleton
class CloudSyncManager @Inject constructor(
    private val database: GymTrackerDatabase,
    private val prefsRepo: UserPreferencesRepository,
    private val scaleReadingsRepository: ScaleReadingsRepository,
    private val watchActivityRepository: WatchActivityRepository,
    private val http: SupabaseHttpClient,
    private val metricsRecomputer: WorkoutMetricsRecomputer,
    private val seeder: ExerciseCatalogSeeder,
    private val stackReminderScheduler: StackReminderScheduler
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun signUp(email: String, password: String): SupabaseAuthResult {
        val prefs = prefsRepo.getOnce()
        val (url, key) = requireConfig(prefs)
        val result = http.signUp(url, key, email, password)
        if (result is SupabaseAuthResult.Session) {
            prefsRepo.setSupabaseSession(
                userId = result.userId,
                email = result.email,
                accessToken = result.accessToken,
                refreshToken = result.refreshToken,
                expiresAtSec = result.expiresAtSec
            )
        }
        return result
    }

    suspend fun signIn(email: String, password: String): SupabaseAuthResult.Session {
        val prefs = prefsRepo.getOnce()
        val (url, key) = requireConfig(prefs)
        val session = http.signIn(url, key, email, password)
        prefsRepo.setSupabaseSession(
            userId = session.userId,
            email = session.email,
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            expiresAtSec = session.expiresAtSec
        )
        return session
    }

    suspend fun signOut() {
        prefsRepo.clearSupabaseSession()
    }

    suspend fun syncNow(): CloudSyncSummary = withContext(Dispatchers.IO) {
        val auth = ensureValidSession()
        seeder.ensureSeeded()

        val exDao = database.exerciseDao()
        val routineDao = database.routineDao()
        val workoutDao = database.workoutDao()
        val metricsDao = database.metricsDao()
        val bwDao = database.bodyWeightDao()

        // Unify local seeded exercise remoteIds with any already existing cloud exercises by slug
        val remoteExercises = http.selectAllRows(
            baseUrl = auth.url,
            publishableKey = auth.key,
            accessToken = auth.accessToken,
            table = "exercises",
            select = "remote_id,slug"
        )
        val cloudRemoteIdBySlug = remoteExercises.mapNotNull { row ->
            val slug = row.str("slug") ?: return@mapNotNull null
            val rid = row.str("remote_id") ?: return@mapNotNull null
            slug to rid
        }.toMap()

        var localExercises = exDao.getAllOnce()
        for (ex in localExercises) {
            val cloudRid = cloudRemoteIdBySlug[ex.slug]
            if (cloudRid != null && cloudRid != ex.remoteId) {
                exDao.update(ex.copy(remoteId = cloudRid))
            }
        }
        localExercises = exDao.getAllOnce()
        val exById = localExercises.associateBy { it.id }

        // 1. Exercises
        uploadBatched(auth, "exercises", "remote_id", localExercises.map { ex ->
            buildJsonObject {
                put("remote_id", ex.remoteId)
                put("user_id", auth.userId)
                put("slug", ex.slug)
                put("name_en", ex.nameEn)
                put("name_uk", ex.nameUk)
                put("exercise_type", ex.type.name)
                put("primary_muscle", ex.primaryMuscle.name)
                put("secondary_muscles", JsonArray(ex.secondaryMuscles.map { JsonPrimitive(it.name) }))
                put("calorie_tier", ex.calorieTier.name)
                putNullableInt("jefit_eid", ex.jefitEid)
                putNullableString("jefit_name_match", ex.jefitNameMatch)
                put("is_archived", ex.isArchived)
                put("updated_at", ex.updatedAt.toString())
                putNullableString("deleted_at", ex.deletedAt?.toString())
            }
        })

        // 2. Routines, Days, DayExercises
        val routines = routineDao.getAllRoutinesOnce()
        val routineById = routines.associateBy { it.id }
        uploadBatched(auth, "routines", "remote_id", routines.map { r ->
            buildJsonObject {
                put("remote_id", r.remoteId)
                put("user_id", auth.userId)
                put("name", r.name)
                put("source", r.source.name)
                putNullableString("external_id", r.externalId)
                put("updated_at", r.updatedAt.toString())
                putNullableString("deleted_at", r.deletedAt?.toString())
            }
        })

        val days = routineDao.getAllDaysOnce()
        val dayById = days.associateBy { it.id }
        uploadBatched(auth, "routine_days", "remote_id", days.mapNotNull { d ->
            val parent = routineById[d.routineId] ?: return@mapNotNull null
            buildJsonObject {
                put("remote_id", d.remoteId)
                put("user_id", auth.userId)
                put("routine_remote_id", parent.remoteId)
                put("name", d.name)
                put("order_index", d.orderIndex)
                putNullableString("external_id", d.externalId)
                put("updated_at", d.updatedAt.toString())
                putNullableString("deleted_at", d.deletedAt?.toString())
            }
        })

        val dayExercises = routineDao.getAllDayExercisesOnce()
        uploadBatched(auth, "routine_day_exercises", "remote_id", dayExercises.mapNotNull { de ->
            val parentDay = dayById[de.routineDayId] ?: return@mapNotNull null
            val ex = exById[de.exerciseId] ?: return@mapNotNull null
            buildJsonObject {
                put("remote_id", de.remoteId)
                put("user_id", auth.userId)
                put("routine_day_remote_id", parentDay.remoteId)
                put("exercise_remote_id", ex.remoteId)
                put("order_index", de.orderIndex)
                put("target_sets", de.targetSets)
                putNullableString("target_reps", de.targetReps)
                putNullableInt("rest_seconds", de.restSeconds)
                putNullableInt("superset_group", de.supersetGroup)
                put("updated_at", de.updatedAt.toString())
                putNullableString("deleted_at", de.deletedAt?.toString())
            }
        })

        // 3. Workouts, WorkoutExercises, WorkoutSets, Compressed HR Streams
        val workouts = workoutDao.getAllWorkoutsOnce().filter { !it.isInProgress }
        val workoutById = workouts.associateBy { it.id }
        val metricsByWorkout = metricsDao.getAllWorkoutMetricsOnce().associateBy { it.workoutId }

        uploadBatched(auth, "workouts", "remote_id", workouts.map { w ->
            val wm = metricsByWorkout[w.id]
            buildJsonObject {
                put("remote_id", w.remoteId)
                put("user_id", auth.userId)
                put("started_at", w.startedAt.toString())
                putNullableString("ended_at", w.endedAt?.toString())
                putNullableString("routine_day_remote_id", w.routineDayId?.let { dayById[it]?.remoteId })
                putNullableString("title", w.title)
                put("source", w.source.name)
                putNullableString("external_id", w.externalId)
                putNullableString("notes", w.notes)
                put("took_pre_workout", w.tookPreWorkout)
                put("excluded_from_stats", w.excludedFromStats)
                put("hc_status", w.hcStatus.name)
                putNullableString("hc_session_id", w.hcSessionId)
                putNullableString("hc_session_start", w.hcSessionStart?.toString())
                putNullableString("hc_session_end", w.hcSessionEnd?.toString())
                putNullableString("hc_exercise_type", w.hcExerciseType)
                putNullableDouble("kcal_watch", wm?.kcalWatch)
                putNullableDouble("kcal_smart_total", wm?.kcalSmartTotal)
                putNullableInt("avg_hr", wm?.avgHr)
                putNullableInt("max_hr", wm?.maxHr)
                putNullableDouble("volume_kg", wm?.volumeKg)
                put("updated_at", w.updatedAt.toString())
                putNullableString("deleted_at", w.deletedAt?.toString())
            }
        })

        val workoutExercises = workoutDao.getAllWorkoutExercisesOnce()
            .filter { workoutById.containsKey(it.workoutId) }
        val weById = workoutExercises.associateBy { it.id }
        uploadBatched(auth, "workout_exercises", "remote_id", workoutExercises.mapNotNull { we ->
            val parentWorkout = workoutById[we.workoutId] ?: return@mapNotNull null
            val ex = exById[we.exerciseId] ?: return@mapNotNull null
            buildJsonObject {
                put("remote_id", we.remoteId)
                put("user_id", auth.userId)
                put("workout_remote_id", parentWorkout.remoteId)
                put("exercise_remote_id", ex.remoteId)
                put("order_index", we.orderIndex)
                putNullableString("note", we.note)
                putNullableInt("superset_group", we.supersetGroup)
                put("updated_at", we.updatedAt.toString())
                putNullableString("deleted_at", we.deletedAt?.toString())
            }
        })

        val workoutSets = workoutDao.getAllWorkoutSetsOnce()
            .filter { weById.containsKey(it.workoutExerciseId) }
        uploadBatched(auth, "workout_sets", "remote_id", workoutSets.mapNotNull { s ->
            val parentWe = weById[s.workoutExerciseId] ?: return@mapNotNull null
            buildJsonObject {
                put("remote_id", s.remoteId)
                put("user_id", auth.userId)
                put("workout_exercise_remote_id", parentWe.remoteId)
                put("set_index", s.setIndex)
                putNullableDouble("weight_kg", s.weightKg)
                putNullableInt("reps", s.reps)
                putNullableDouble("rpe", s.rpe?.toDouble())
                putNullableInt("rir", s.rir)
                putNullableInt("duration_sec", s.durationSec)
                putNullableDouble("distance_km", s.distanceKm)
                putNullableDouble("calories", s.calories)
                put("set_type", s.setType.name)
                putNullableString("completed_at", s.completedAt?.toString())
                put("is_estimated_time", s.isEstimatedTime)
                put("updated_at", s.updatedAt.toString())
                putNullableString("deleted_at", s.deletedAt?.toString())
            }
        })

        // 4. Compressed HR & Calorie Streams
        val hrByWorkout = metricsDao.getAllHrSamplesOnce().groupBy { it.workoutId }
        val calByWorkout = metricsDao.getAllCalorieBucketsOnce().groupBy { it.workoutId }
        val streamRows = workouts.mapNotNull { w ->
            val hrs = hrByWorkout[w.id].orEmpty()
            val cals = calByWorkout[w.id].orEmpty()
            if (hrs.isEmpty() && cals.isEmpty()) return@mapNotNull null
            val baseSec = w.startedAt.epochSecond
            buildJsonObject {
                put("workout_remote_id", w.remoteId)
                put("user_id", auth.userId)
                put("hr_samples_json", buildJsonArray {
                    hrs.forEach { h ->
                        add(buildJsonArray {
                            add(JsonPrimitive(h.time.epochSecond - baseSec))
                            add(JsonPrimitive(h.bpm))
                        })
                    }
                })
                put("calorie_buckets_json", buildJsonArray {
                    cals.forEach { c ->
                        add(buildJsonArray {
                            add(JsonPrimitive(c.startTime.epochSecond - baseSec))
                            add(JsonPrimitive(c.endTime.epochSecond - baseSec))
                            add(JsonPrimitive(c.kcal))
                            add(JsonPrimitive(c.kind.name))
                        })
                    }
                })
                put("updated_at", w.updatedAt.toString())
            }
        }
        uploadBatched(auth, "workout_hr_streams", "workout_remote_id", streamRows, chunkSize = 40)

        // 5. Body Weights & Xiaomi Scale Snapshots
        val bodyWeights = bwDao.getAllAsc()
        uploadBatched(auth, "body_weights", "user_id,measured_date,source", bodyWeights.map { bw ->
            buildJsonObject {
                put("remote_id", bw.remoteId)
                put("user_id", auth.userId)
                put("measured_date", bw.date.toString())
                put("kg", bw.kg)
                put("source", bw.source.name)
                putNullableDouble("fat_percent", bw.fatPercent)
                putNullableDouble("chest_cm", bw.chestCm)
                putNullableDouble("arms_cm", bw.armsCm)
                putNullableDouble("waist_cm", bw.waistCm)
                putNullableDouble("hips_cm", bw.hipsCm)
                putNullableDouble("thighs_cm", bw.thighsCm)
                putNullableDouble("calves_cm", bw.calvesCm)
                putNullableDouble("shoulders_cm", bw.shouldersCm)
                putNullableDouble("neck_cm", bw.neckCm)
                putNullableDouble("forearms_cm", bw.forearmsCm)
                put("flagged_outlier", bw.flaggedOutlier)
                put("updated_at", bw.updatedAt.toString())
                putNullableString("deleted_at", bw.deletedAt?.toString())
            }
        })

        val scaleSnapshots = scaleReadingsRepository.readingsByDate.first().values.toList()
        uploadBatched(auth, "scale_readings", "user_id,measured_date", scaleSnapshots.map { snap ->
            val stableUuid = UUID.nameUUIDFromBytes("scale_${snap.dateIso}".toByteArray()).toString()
            val capturedInstant = Instant.ofEpochSecond(snap.capturedAtEpochSec).toString()
            buildJsonObject {
                put("remote_id", stableUuid)
                put("user_id", auth.userId)
                put("measured_date", snap.dateIso)
                put("captured_at", capturedInstant)
                put("weight_kg", snap.weightKg)
                putNullableInt("impedance_ohm", snap.impedanceOhm)
                put("bmi", snap.bmi)
                put("ideal_weight_kg", snap.idealWeightKg)
                put("bmr_kcal", snap.bmrKcal)
                put("visceral_fat", snap.visceralFat)
                putNullableDouble("fat_percent", snap.fatPercent)
                putNullableDouble("lean_body_mass_kg", snap.leanBodyMassKg)
                putNullableDouble("water_percent", snap.waterPercent)
                putNullableDouble("muscle_mass_kg", snap.muscleMassKg)
                putNullableDouble("bone_mass_kg", snap.boneMassKg)
                putNullableDouble("protein_percent", snap.proteinPercent)
                putNullableInt("metabolic_age", snap.metabolicAge)
                putNullableString("body_type", snap.bodyTypeName)
                putNullableString("device_name", snap.deviceName)
                putNullableString("mac_address", snap.macAddress)
                put("updated_at", capturedInstant)
            }
        })

        // 6. Watch Activities (non-gym sessions from Health Connect / OHealth)
        val watchSessions = watchActivityRepository.watchActivities.first()
        uploadBatched(auth, "watch_activities", "user_id,hc_session_id", watchSessions.map { wa ->
            val stableUuid = UUID.nameUUIDFromBytes("watch_${wa.id}".toByteArray()).toString()
            buildJsonObject {
                put("remote_id", stableUuid)
                put("user_id", auth.userId)
                put("hc_session_id", wa.id)
                put("started_at", Instant.ofEpochSecond(wa.startEpochSec).toString())
                put("ended_at", Instant.ofEpochSecond(wa.endEpochSec).toString())
                put("duration_sec", wa.durationSec.toInt())
                putNullableString("title", wa.title)
                put("exercise_type", wa.exerciseTypeName)
                put("origin_package", wa.originPackage)
                putNullableDouble("distance_km", wa.distanceKm)
                putNullableDouble("active_kcal", wa.activeKcal)
                putNullableDouble("total_kcal", wa.totalKcal)
                putNullableInt("avg_hr", wa.avgHr)
                putNullableInt("max_hr", wa.maxHr)
                putNullableInt("steps", wa.steps?.toInt())
                put("updated_at", Instant.ofEpochSecond(wa.endEpochSec).toString())
            }
        })

        // 7. Synced Preferences (convert exerciseGoalsKg keys to slug so goals transfer across phones)
        val prefs = prefsRepo.getOnce()
        val slugGoals = prefs.exerciseGoalsKg.mapNotNull { (exId, goal) ->
            exById[exId]?.slug?.let { it to goal }
        }.toMap()
        val prefRow = buildJsonObject {
            put("user_id", auth.userId)
            put("height_cm", prefs.heightCm)
            put("birth_year", prefs.birthYear)
            put("is_male", prefs.isMale)
            put("daily_step_goal", prefs.dailyStepGoal)
            put("hr_max", prefs.hrMax)
            put("preferred_formula", prefs.preferredFormula.name)
            put("exercise_goals_json", buildJsonObject {
                slugGoals.forEach { (slug, kg) -> put(slug, kg) }
            })
            put("injured_muscles_json", buildJsonObject {
                prefs.injuredMuscles.forEach { (k, v) -> put(k, v) }
            })
            putNullableDouble("scale_min_weight_kg", prefs.scaleMinWeightKg)
            putNullableDouble("scale_max_weight_kg", prefs.scaleMaxWeightKg)
            put("updated_at", Instant.now().toString())
        }
        uploadBatched(auth, "user_preferences_sync", "user_id", listOf(prefRow))

        // 8. Stack Supplements, Schedules & Logs
        val suppDao = database.supplementDao()
        val suppItems = suppDao.getAllItemsOnce()
        val suppItemRemoteById = suppItems.associate { it.id to it.remoteId }
        val workoutRemoteByIdMap = workouts.associate { it.id to it.remoteId }

        uploadBatched(auth, "supplement_items", "remote_id", suppItems.map { si ->
            buildJsonObject {
                put("remote_id", si.remoteId)
                put("user_id", auth.userId)
                put("name_en", si.nameEn)
                put("name_uk", si.nameUk)
                put("category", si.category.name)
                put("default_dose", si.defaultDose)
                put("unit", si.unit.name)
                putNullableInt("caffeine_mg_per_serving", si.caffeineMgPerServing)
                putNullableDouble("servings_remaining", si.servingsRemaining)
                putNullableDouble("low_stock_threshold", si.lowStockThreshold)
                putNullableInt("cycle_days_on", si.cycleDaysOn)
                putNullableInt("cycle_days_off", si.cycleDaysOff)
                putNullableString("cycle_start_date", si.cycleStartDate?.toString())
                putNullableString("notes", si.notes)
                put("updated_at", si.updatedAt.toString())
                putNullableString("deleted_at", si.deletedAt?.toString())
            }
        })

        val suppSchedules = suppDao.getAllSchedulesOnce()
        runCatching {
            uploadBatched(auth, "supplement_schedules", "remote_id", suppSchedules.mapNotNull { sc ->
                val suppRid = suppItemRemoteById[sc.supplementId] ?: return@mapNotNull null
                buildJsonObject {
                    put("remote_id", sc.remoteId)
                    put("user_id", auth.userId)
                    put("supplement_remote_id", suppRid)
                    put("trigger_type", sc.triggerType.name)
                    putNullableString("time_of_day", sc.timeOfDay?.toString())
                    putNullableInt("offset_minutes", sc.offsetMinutes)
                    put("days_of_week_mask", sc.daysOfWeekMask)
                    put("enabled", sc.enabled)
                    put("updated_at", sc.updatedAt.toString())
                    putNullableString("deleted_at", sc.deletedAt?.toString())
                }
            })
        }

        val suppLogs = suppDao.getAllLogsOnce()
        uploadBatched(auth, "supplement_logs", "remote_id", suppLogs.mapNotNull { sl ->
            val suppRid = suppItemRemoteById[sl.supplementId] ?: return@mapNotNull null
            val workoutRid = sl.linkedWorkoutId?.let { workoutRemoteByIdMap[it] }
            buildJsonObject {
                put("remote_id", sl.remoteId)
                put("user_id", auth.userId)
                put("supplement_remote_id", suppRid)
                put("taken_at", sl.takenAt.toString())
                put("dose", sl.dose)
                putNullableInt("caffeine_mg", sl.caffeineMg)
                putNullableString("linked_workout_remote_id", workoutRid)
                put("updated_at", sl.updatedAt.toString())
                putNullableString("deleted_at", sl.deletedAt?.toString())
            }
        })

        prefsRepo.setSupabaseLastSync(Instant.now().epochSecond)
        CloudSyncSummary(
            workoutsUploaded = workouts.size,
            setsUploaded = workoutSets.size,
            bodyWeightsUploaded = bodyWeights.size,
            scaleReadingsUploaded = scaleSnapshots.size
        )
    }

    suspend fun restoreFromCloud(): CloudRestoreSummary = withContext(Dispatchers.IO) {
        val auth = ensureValidSession()
        seeder.ensureSeeded()

        val remoteExercises = http.selectAllRows(auth.url, auth.key, auth.accessToken, "exercises")
        val remoteRoutines = http.selectAllRows(auth.url, auth.key, auth.accessToken, "routines")
        val remoteDays = http.selectAllRows(auth.url, auth.key, auth.accessToken, "routine_days")
        val remoteDayEx = http.selectAllRows(auth.url, auth.key, auth.accessToken, "routine_day_exercises")
        val remoteWorkouts = http.selectAllRows(auth.url, auth.key, auth.accessToken, "workouts", orderBy = "started_at.asc")
        val remoteWorkoutEx = http.selectAllRows(auth.url, auth.key, auth.accessToken, "workout_exercises")
        val remoteSets = http.selectAllRows(auth.url, auth.key, auth.accessToken, "workout_sets")
        val remoteStreams = http.selectAllRows(auth.url, auth.key, auth.accessToken, "workout_hr_streams")
        val remoteBw = http.selectAllRows(auth.url, auth.key, auth.accessToken, "body_weights")
        val remoteScale = http.selectAllRows(auth.url, auth.key, auth.accessToken, "scale_readings")
        val remotePrefs = http.selectAllRows(auth.url, auth.key, auth.accessToken, "user_preferences_sync")
        val remoteSuppItems = runCatching {
            http.selectAllRows(auth.url, auth.key, auth.accessToken, "supplement_items")
        }.getOrDefault(emptyList())
        val remoteSuppScheds = runCatching {
            http.selectAllRows(auth.url, auth.key, auth.accessToken, "supplement_schedules")
        }.getOrDefault(emptyList())
        val remoteSuppLogs = runCatching {
            http.selectAllRows(auth.url, auth.key, auth.accessToken, "supplement_logs")
        }.getOrDefault(emptyList())

        val restoredWorkoutIds = mutableListOf<Long>()
        var exCount = 0
        var workoutCount = 0
        var bwCount = 0
        val scaleSnaps = mutableListOf<ScaleReadingSnapshot>()

        database.withTransaction {
            val exDao = database.exerciseDao()
            val routineDao = database.routineDao()
            val workoutDao = database.workoutDao()
            val metricsDao = database.metricsDao()
            val bwDao = database.bodyWeightDao()

            val exIdByRemoteId = mutableMapOf<String, Long>()
            for (row in remoteExercises) {
                val rId = row.str("remote_id") ?: continue
                val slug = row.str("slug") ?: "custom_${rId.take(8)}"
                val existing = exDao.getByRemoteId(rId) ?: exDao.getBySlug(slug)
                val secList = row["secondary_muscles"]?.let { elem ->
                    runCatching {
                        elem.jsonArray.mapNotNull { runCatching { Muscle.valueOf(it.jsonPrimitive.content) }.getOrNull() }
                    }.getOrDefault(emptyList())
                } ?: emptyList()

                val entity = ExerciseEntity(
                    id = existing?.id ?: 0,
                    slug = slug,
                    nameEn = row.str("name_en") ?: slug,
                    nameUk = row.str("name_uk") ?: (row.str("name_en") ?: slug),
                    type = row.enum("exercise_type", ExerciseType.WEIGHTED),
                    primaryMuscle = row.enum("primary_muscle", Muscle.CHEST),
                    secondaryMuscles = secList,
                    calorieTier = row.enum("calorie_tier", CalorieTier.TIER_2_UPPER_COMPOUND),
                    jefitEid = row.int("jefit_eid"),
                    jefitNameMatch = row.str("jefit_name_match"),
                    isArchived = row.bool("is_archived") ?: false,
                    remoteId = rId,
                    updatedAt = row.instant("updated_at") ?: Instant.now(),
                    deletedAt = row.instant("deleted_at"),
                    syncState = SyncState.SYNCED
                )
                val localId = if (existing == null) {
                    exDao.insertReplace(entity)
                } else {
                    exDao.update(entity)
                    existing.id
                }
                exIdByRemoteId[rId] = localId
                exCount++
            }

            val routineIdByRemoteId = mutableMapOf<String, Long>()
            val routineSourceByRemoteId = mutableMapOf<String, DataSource>()
            for (row in remoteRoutines) {
                val rId = row.str("remote_id") ?: continue
                val src = row.enum("source", DataSource.APP)
                val extId = row.str("external_id")
                val existing = routineDao.findRoutineByRemoteId(rId)
                    ?: extId?.let { routineDao.findRoutineByExternalId(src, it) }
                val entity = RoutineEntity(
                    id = existing?.id ?: 0,
                    name = row.str("name") ?: "Routine",
                    source = src,
                    externalId = extId,
                    remoteId = rId,
                    updatedAt = row.instant("updated_at") ?: Instant.now(),
                    deletedAt = row.instant("deleted_at"),
                    syncState = SyncState.SYNCED
                )
                val localId = if (existing == null) routineDao.insertRoutine(entity) else {
                    routineDao.updateRoutine(entity); existing.id
                }
                routineIdByRemoteId[rId] = localId
                routineSourceByRemoteId[rId] = src
            }

            val dayIdByRemoteId = mutableMapOf<String, Long>()
            for (row in remoteDays) {
                val rId = row.str("remote_id") ?: continue
                val routineRid = row.str("routine_remote_id") ?: continue
                val routineLocalId = routineIdByRemoteId[routineRid] ?: continue
                val src = routineSourceByRemoteId[routineRid] ?: DataSource.APP
                val extId = row.str("external_id")
                val existing = routineDao.findDayByRemoteId(rId)
                    ?: extId?.let { routineDao.findDayByExternalId(src, it) }
                val entity = RoutineDayEntity(
                    id = existing?.id ?: 0,
                    routineId = routineLocalId,
                    name = row.str("name") ?: "Day",
                    orderIndex = row.int("order_index") ?: 0,
                    source = src,
                    externalId = extId,
                    remoteId = rId,
                    updatedAt = row.instant("updated_at") ?: Instant.now(),
                    deletedAt = row.instant("deleted_at"),
                    syncState = SyncState.SYNCED
                )
                val localId = if (existing == null) routineDao.insertDay(entity) else {
                    routineDao.updateDay(entity); existing.id
                }
                dayIdByRemoteId[rId] = localId
            }

            for (row in remoteDayEx) {
                val rId = row.str("remote_id") ?: continue
                val dayRid = row.str("routine_day_remote_id") ?: continue
                val exRid = row.str("exercise_remote_id") ?: continue
                val dayLocalId = dayIdByRemoteId[dayRid] ?: continue
                val exLocalId = exIdByRemoteId[exRid] ?: exDao.getByRemoteId(exRid)?.id ?: continue
                val existing = routineDao.findDayExerciseByRemoteId(rId)
                routineDao.upsertDayExercise(
                    RoutineDayExerciseEntity(
                        id = existing?.id ?: 0,
                        routineDayId = dayLocalId,
                        exerciseId = exLocalId,
                        orderIndex = row.int("order_index") ?: 0,
                        targetSets = row.int("target_sets") ?: 3,
                        targetReps = row.str("target_reps"),
                        restSeconds = row.int("rest_seconds"),
                        supersetGroup = row.int("superset_group"),
                        remoteId = rId,
                        updatedAt = row.instant("updated_at") ?: Instant.now(),
                        deletedAt = row.instant("deleted_at"),
                        syncState = SyncState.SYNCED
                    )
                )
            }

            val workoutIdByRemoteId = mutableMapOf<String, Long>()
            val workoutStartByRemoteId = mutableMapOf<String, Instant>()
            for (row in remoteWorkouts) {
                val rId = row.str("remote_id") ?: continue
                val startedAt = row.instant("started_at") ?: continue
                val src = row.enum("source", DataSource.APP)
                val extId = row.str("external_id")
                val existing = workoutDao.findWorkoutByRemoteId(rId)
                    ?: extId?.let { workoutDao.findByExternalId(src, it) }
                val entity = WorkoutEntity(
                    id = existing?.id ?: 0,
                    startedAt = startedAt,
                    endedAt = row.instant("ended_at"),
                    routineDayId = row.str("routine_day_remote_id")?.let { dayIdByRemoteId[it] },
                    title = row.str("title"),
                    source = src,
                    externalId = extId,
                    notes = row.str("notes"),
                    tookPreWorkout = row.bool("took_pre_workout") ?: false,
                    excludedFromStats = row.bool("excluded_from_stats") ?: false,
                    isInProgress = false,
                    hcStatus = row.enum("hc_status", HcStatus.NOT_ATTEMPTED),
                    hcSessionId = row.str("hc_session_id"),
                    hcSessionStart = row.instant("hc_session_start"),
                    hcSessionEnd = row.instant("hc_session_end"),
                    hcExerciseType = row.str("hc_exercise_type"),
                    hcLastAttemptAt = existing?.hcLastAttemptAt,
                    remoteId = rId,
                    updatedAt = row.instant("updated_at") ?: Instant.now(),
                    deletedAt = row.instant("deleted_at"),
                    syncState = SyncState.SYNCED
                )
                val localId = if (existing == null) workoutDao.insertWorkout(entity) else {
                    workoutDao.updateWorkout(entity); existing.id
                }
                workoutIdByRemoteId[rId] = localId
                workoutStartByRemoteId[rId] = startedAt
                restoredWorkoutIds += localId
                workoutCount++
            }

            val weIdByRemoteId = mutableMapOf<String, Long>()
            for (row in remoteWorkoutEx) {
                val rId = row.str("remote_id") ?: continue
                val wRid = row.str("workout_remote_id") ?: continue
                val exRid = row.str("exercise_remote_id") ?: continue
                val wLocalId = workoutIdByRemoteId[wRid] ?: continue
                val exLocalId = exIdByRemoteId[exRid] ?: exDao.getByRemoteId(exRid)?.id ?: continue
                val existing = workoutDao.findWorkoutExerciseByRemoteId(rId)
                val entity = WorkoutExerciseEntity(
                    id = existing?.id ?: 0,
                    workoutId = wLocalId,
                    exerciseId = exLocalId,
                    orderIndex = row.int("order_index") ?: 0,
                    note = row.str("note"),
                    supersetGroup = row.int("superset_group"),
                    remoteId = rId,
                    updatedAt = row.instant("updated_at") ?: Instant.now(),
                    deletedAt = row.instant("deleted_at"),
                    syncState = SyncState.SYNCED
                )
                val localId = if (existing == null) workoutDao.insertWorkoutExercise(entity) else {
                    workoutDao.updateWorkoutExercise(entity); existing.id
                }
                weIdByRemoteId[rId] = localId
            }

            for (row in remoteSets) {
                val rId = row.str("remote_id") ?: continue
                val weRid = row.str("workout_exercise_remote_id") ?: continue
                val weLocalId = weIdByRemoteId[weRid] ?: continue
                val existing = workoutDao.findWorkoutSetByRemoteId(rId)
                val entity = WorkoutSetEntity(
                    id = existing?.id ?: 0,
                    workoutExerciseId = weLocalId,
                    setIndex = row.int("set_index") ?: 1,
                    weightKg = row.double("weight_kg"),
                    reps = row.int("reps"),
                    rpe = row.double("rpe")?.toFloat(),
                    rir = row.int("rir"),
                    durationSec = row.int("duration_sec"),
                    distanceKm = row.double("distance_km"),
                    calories = row.double("calories"),
                    setType = row.enum("set_type", SetType.WORK),
                    completedAt = row.instant("completed_at"),
                    isEstimatedTime = row.bool("is_estimated_time") ?: false,
                    remoteId = rId,
                    updatedAt = row.instant("updated_at") ?: Instant.now(),
                    deletedAt = row.instant("deleted_at"),
                    syncState = SyncState.SYNCED
                )
                if (existing == null) workoutDao.insertWorkoutSet(entity) else workoutDao.updateWorkoutSet(entity)
            }

            for (row in remoteStreams) {
                val wRid = row.str("workout_remote_id") ?: continue
                val wLocalId = workoutIdByRemoteId[wRid] ?: continue
                val baseStart = workoutStartByRemoteId[wRid] ?: continue
                val hrSamples = row["hr_samples_json"]?.jsonArray?.mapNotNull { pair ->
                    val arr = pair.jsonArray
                    val off = arr.getOrNull(0)?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
                    val bpm = arr.getOrNull(1)?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    HrSampleEntity(wLocalId, baseStart.plusSeconds(off), bpm)
                }.orEmpty()
                if (hrSamples.isNotEmpty()) {
                    metricsDao.deleteHrSamples(wLocalId)
                    metricsDao.insertHrSamples(hrSamples)
                }
                val buckets = row["calorie_buckets_json"]?.jsonArray?.mapNotNull { tup ->
                    val arr = tup.jsonArray
                    val sOff = arr.getOrNull(0)?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
                    val eOff = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
                    val kcal = arr.getOrNull(2)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val kind = arr.getOrNull(3)?.jsonPrimitive?.contentOrNull
                        ?.let { runCatching { CalorieKind.valueOf(it) }.getOrNull() } ?: CalorieKind.TOTAL
                    CalorieBucketEntity(
                        workoutId = wLocalId,
                        startTime = baseStart.plusSeconds(sOff),
                        endTime = baseStart.plusSeconds(eOff),
                        kcal = kcal,
                        kind = kind
                    )
                }.orEmpty()
                if (buckets.isNotEmpty()) {
                    metricsDao.deleteCalorieBuckets(wLocalId)
                    metricsDao.insertCalorieBuckets(buckets)
                }
            }

            for (row in remoteBw) {
                val rId = row.str("remote_id") ?: continue
                val date = row.str("measured_date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: continue
                val src = row.enum("source", DataSource.APP)
                val existing = bwDao.findByRemoteId(rId) ?: bwDao.findByDateAndSource(date, src)
                bwDao.upsert(
                    BodyWeightEntity(
                        id = existing?.id ?: 0,
                        date = date,
                        kg = row.double("kg") ?: continue,
                        source = src,
                        fatPercent = row.double("fat_percent"),
                        chestCm = row.double("chest_cm"),
                        armsCm = row.double("arms_cm"),
                        waistCm = row.double("waist_cm"),
                        hipsCm = row.double("hips_cm"),
                        thighsCm = row.double("thighs_cm"),
                        calvesCm = row.double("calves_cm"),
                        shouldersCm = row.double("shoulders_cm"),
                        neckCm = row.double("neck_cm"),
                        forearmsCm = row.double("forearms_cm"),
                        flaggedOutlier = row.bool("flagged_outlier") ?: false,
                        remoteId = rId,
                        updatedAt = row.instant("updated_at") ?: Instant.now(),
                        deletedAt = row.instant("deleted_at"),
                        syncState = SyncState.SYNCED
                    )
                )
                bwCount++
            }

            // Restore Stack Supplements, Schedules & Logs
            val suppDao = database.supplementDao()
            val suppLocalIdByRemoteId = mutableMapOf<String, Long>()
            for (row in remoteSuppItems) {
                val rId = row.str("remote_id") ?: continue
                val existing = suppDao.findItemByRemoteId(rId)
                val localId = suppDao.upsertItem(
                    SupplementItemEntity(
                        id = existing?.id ?: 0L,
                        nameEn = row.str("name_en") ?: "Supplement",
                        nameUk = row.str("name_uk") ?: (row.str("name_en") ?: "Добавка"),
                        category = row.enum("category", SupplementCategory.OTHER),
                        defaultDose = row.double("default_dose") ?: 1.0,
                        unit = row.enum("unit", SupplementUnit.CAPSULE),
                        caffeineMgPerServing = row.int("caffeine_mg_per_serving"),
                        servingsRemaining = row.double("servings_remaining"),
                        lowStockThreshold = row.double("low_stock_threshold"),
                        cycleDaysOn = row.int("cycle_days_on"),
                        cycleDaysOff = row.int("cycle_days_off"),
                        cycleStartDate = row.str("cycle_start_date")?.let { raw ->
                            runCatching { LocalDate.parse(raw) }.getOrNull()
                        },
                        notes = row.str("notes"),
                        remoteId = rId,
                        updatedAt = row.instant("updated_at") ?: Instant.now(),
                        deletedAt = row.instant("deleted_at"),
                        syncState = SyncState.SYNCED
                    )
                )
                suppLocalIdByRemoteId[rId] = localId
            }

            for (row in remoteSuppScheds) {
                val rId = row.str("remote_id") ?: continue
                val suppRid = row.str("supplement_remote_id") ?: continue
                val suppLocalId = suppLocalIdByRemoteId[suppRid]
                    ?: suppDao.findItemByRemoteId(suppRid)?.id ?: continue
                val existing = suppDao.findScheduleByRemoteId(rId)
                suppDao.upsertSchedule(
                    SupplementScheduleEntity(
                        id = existing?.id ?: 0L,
                        supplementId = suppLocalId,
                        triggerType = row.enum("trigger_type", SupplementTrigger.FIXED_TIME),
                        timeOfDay = row.str("time_of_day")?.let { raw ->
                            runCatching { LocalTime.parse(raw) }.getOrNull()
                        },
                        offsetMinutes = row.int("offset_minutes"),
                        daysOfWeekMask = row.int("days_of_week_mask") ?: 0b1111111,
                        enabled = row.bool("enabled") ?: true,
                        remoteId = rId,
                        updatedAt = row.instant("updated_at") ?: Instant.now(),
                        deletedAt = row.instant("deleted_at"),
                        syncState = SyncState.SYNCED
                    )
                )
            }

            for (row in remoteSuppLogs) {
                val rId = row.str("remote_id") ?: continue
                val suppRid = row.str("supplement_remote_id") ?: continue
                val suppLocalId = suppLocalIdByRemoteId[suppRid]
                    ?: suppDao.findItemByRemoteId(suppRid)?.id ?: continue
                val takenAt = row.instant("taken_at") ?: continue
                val linkedWId = row.str("linked_workout_remote_id")?.let { wRid ->
                    workoutIdByRemoteId[wRid] ?: workoutDao.findWorkoutByRemoteId(wRid)?.id
                }
                val existing = suppDao.findLogByRemoteId(rId)
                suppDao.upsertLog(
                    SupplementLogEntity(
                        id = existing?.id ?: 0L,
                        supplementId = suppLocalId,
                        takenAt = takenAt,
                        dose = row.double("dose") ?: 1.0,
                        caffeineMg = row.int("caffeine_mg"),
                        linkedWorkoutId = linkedWId,
                        remoteId = rId,
                        updatedAt = row.instant("updated_at") ?: Instant.now(),
                        deletedAt = row.instant("deleted_at"),
                        syncState = SyncState.SYNCED
                    )
                )
            }
        }

        for (row in remoteScale) {
            val dateIso = row.str("measured_date") ?: continue
            val captured = row.instant("captured_at")?.epochSecond ?: Instant.now().epochSecond
            scaleSnaps += ScaleReadingSnapshot(
                dateIso = dateIso,
                capturedAtEpochSec = captured,
                weightKg = row.double("weight_kg") ?: continue,
                impedanceOhm = row.int("impedance_ohm"),
                bmi = row.double("bmi") ?: continue,
                idealWeightKg = row.double("ideal_weight_kg") ?: 75.0,
                bmrKcal = row.int("bmr_kcal") ?: 1800,
                visceralFat = row.int("visceral_fat") ?: 10,
                fatPercent = row.double("fat_percent"),
                leanBodyMassKg = row.double("lean_body_mass_kg"),
                waterPercent = row.double("water_percent"),
                muscleMassKg = row.double("muscle_mass_kg"),
                boneMassKg = row.double("bone_mass_kg"),
                proteinPercent = row.double("protein_percent"),
                metabolicAge = row.int("metabolic_age"),
                bodyTypeName = row.str("body_type"),
                deviceName = row.str("device_name"),
                macAddress = row.str("mac_address")
            )
        }
        scaleReadingsRepository.upsertSnapshots(scaleSnaps)

        remotePrefs.firstOrNull()?.let { pref ->
            val allLocalEx = database.exerciseDao().getAllOnce().associateBy { it.slug }
            val goalsBySlug: Map<String, Double> = pref["exercise_goals_json"]?.let { el ->
                runCatching {
                    el.jsonObject.entries.mapNotNull { (slug, valEl) ->
                        val localId = allLocalEx[slug]?.id ?: return@mapNotNull null
                        val kg = valEl.jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                        localId.toString() to kg
                    }.toMap()
                }.getOrDefault(emptyMap())
            } ?: emptyMap()

            val injuredMap: Map<String, String> = pref["injured_muscles_json"]?.let { el ->
                runCatching {
                    el.jsonObject.entries.associate { (k, v) -> k to v.jsonPrimitive.content }
                }.getOrDefault(emptyMap())
            } ?: emptyMap()

            prefsRepo.applyCloudSyncedPrefs(
                heightCm = pref.int("height_cm") ?: 182,
                birthYear = pref.int("birth_year") ?: 1993,
                isMale = pref.bool("is_male") ?: true,
                dailyStepGoal = pref.int("daily_step_goal") ?: 10000,
                hrMax = pref.int("hr_max") ?: 185,
                preferredFormula = pref.enum("preferred_formula", OneRmFormula.EPLEY),
                exerciseGoalsJson = json.encodeToString(goalsBySlug),
                injuredMusclesJson = json.encodeToString(injuredMap),
                scaleMinKg = pref.double("scale_min_weight_kg"),
                scaleMaxKg = pref.double("scale_max_weight_kg")
            )
        }

        for (wId in restoredWorkoutIds) {
            metricsRecomputer.recompute(wId)
        }

        stackReminderScheduler.rescheduleAll()
        prefsRepo.setSupabaseLastSync(Instant.now().epochSecond)
        CloudRestoreSummary(
            exercisesRestored = exCount,
            workoutsRestored = workoutCount,
            bodyWeightsRestored = bwCount,
            scaleReadingsRestored = scaleSnaps.size
        )
    }

    private data class AuthContext(
        val url: String,
        val key: String,
        val userId: String,
        val accessToken: String
    )

    private suspend fun ensureValidSession(): AuthContext {
        val prefs = prefsRepo.getOnce()
        val (url, key) = requireConfig(prefs)
        val userId = prefs.supabaseUserId
        val token = prefs.supabaseAccessToken
        val refresh = prefs.supabaseRefreshToken
        if (userId.isNullOrBlank() || token.isNullOrBlank()) {
            throw IOException("Sign in to Supabase first")
        }
        val nowSec = Instant.now().epochSecond
        if (prefs.supabaseAccessExpiresAtSec > 0L && nowSec >= prefs.supabaseAccessExpiresAtSec - 60L) {
            if (!refresh.isNullOrBlank()) {
                val refreshed = http.refreshToken(url, key, refresh)
                prefsRepo.setSupabaseSession(
                    userId = refreshed.userId,
                    email = refreshed.email ?: prefs.supabaseUserEmail,
                    accessToken = refreshed.accessToken,
                    refreshToken = refreshed.refreshToken ?: refresh,
                    expiresAtSec = refreshed.expiresAtSec
                )
                return AuthContext(url, key, refreshed.userId, refreshed.accessToken)
            }
        }
        return AuthContext(url, key, userId, token)
    }

    private fun requireConfig(prefs: UserPreferences): Pair<String, String> {
        val url = prefs.supabaseUrl.trim().trimEnd('/')
        val key = prefs.supabasePublishableKey.trim()
        if (url.isBlank() || key.isBlank()) {
            throw IOException("Configure Supabase URL & Publishable Key first")
        }
        return url to key
    }

    private suspend fun uploadBatched(
        auth: AuthContext,
        table: String,
        onConflict: String,
        rows: List<JsonObject>,
        chunkSize: Int = 100
    ) {
        if (rows.isEmpty()) return
        rows.chunked(chunkSize).forEach { chunk ->
            http.upsertRows(
                baseUrl = auth.url,
                publishableKey = auth.key,
                accessToken = auth.accessToken,
                table = table,
                rowsJson = JsonArray(chunk),
                onConflict = onConflict
            )
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull

    private fun JsonObject.instant(key: String): Instant? =
        str(key)?.let { raw ->
            runCatching { OffsetDateTime.parse(raw).toInstant() }
                .getOrElse { runCatching { Instant.parse(raw) }.getOrNull() }
        }

    private inline fun <reified T : Enum<T>> JsonObject.enum(key: String, default: T): T =
        str(key)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableString(key: String, value: String?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableInt(key: String, value: Int?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableDouble(key: String, value: Double?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }
}
