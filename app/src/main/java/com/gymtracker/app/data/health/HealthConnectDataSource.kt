package com.gymtracker.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.gymtracker.app.data.db.CalorieKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class HealthConnectDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : HealthDataSource {

    private fun clientOrNull(): HealthConnectClient? {
        return if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else null
    }

    override fun requiredCorePermissions(): Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(SkinTemperatureRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class)
    )

    override fun optionalHistoryAndBgPermissions(state: HcPermissionState): Set<String> = buildSet {
        if (state.historySupported) add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
        if (state.backgroundSupported) add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
    }

    override suspend fun getPermissionState(): HcPermissionState = withContext(Dispatchers.IO) {
        val status = when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HcSdkAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcSdkAvailability.UPDATE_REQUIRED
            else -> HcSdkAvailability.UNAVAILABLE
        }
        val client = if (status == HcSdkAvailability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null
        if (client == null) {
            return@withContext HcPermissionState(
                sdkAvailability = status,
                coreReadGranted = false,
                historyGranted = false,
                historySupported = false,
                backgroundGranted = false,
                backgroundSupported = false
            )
        }

        val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        val essential = setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        )
        val historySupported = runCatching {
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        }.getOrDefault(false)
        val bgSupported = runCatching {
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        }.getOrDefault(false)

        HcPermissionState(
            sdkAvailability = status,
            coreReadGranted = granted.containsAll(essential),
            historyGranted = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted,
            historySupported = historySupported,
            backgroundGranted = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted,
            backgroundSupported = bgSupported,
            distanceReadGranted = HealthPermission.getReadPermission(DistanceRecord::class) in granted
        )
    }

    override suspend fun discoverOrigins(daysBack: Long): List<HcOriginInfo> = withContext(Dispatchers.IO) {
        val client = clientOrNull() ?: return@withContext emptyList()
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(daysBack))
        val sessionCounts = mutableMapOf<String, Int>()
        val hrCounts = mutableMapOf<String, Int>()

        runCatching {
            readPaged(client, ExerciseSessionRecord::class, start, end, null).forEach { r ->
                val pkg = r.metadata.dataOrigin.packageName
                if (!isIgnoredOrigin(pkg)) {
                    sessionCounts[pkg] = (sessionCounts[pkg] ?: 0) + 1
                }
            }
        }
        runCatching {
            readPaged(client, HeartRateRecord::class, start, end, null).forEach { r ->
                val pkg = r.metadata.dataOrigin.packageName
                if (!isIgnoredOrigin(pkg)) {
                    hrCounts[pkg] = (hrCounts[pkg] ?: 0) + 1
                }
            }
        }

        val allPkgs = (sessionCounts.keys + hrCounts.keys).distinct()
        allPkgs.map { pkg ->
            HcOriginInfo(
                packageName = pkg,
                sessionCount = sessionCounts[pkg] ?: 0,
                hrRecordCount = hrCounts[pkg] ?: 0,
                isLikelyOHealth = isLikelyOHealth(pkg)
            )
        }.sortedWith(
            compareByDescending<HcOriginInfo> { it.isLikelyOHealth }
                .thenByDescending { it.sessionCount + it.hrRecordCount }
        )
    }

    override suspend fun readExerciseSessions(
        start: Instant,
        end: Instant,
        originPackage: String?
    ): List<HcSession> = withContext(Dispatchers.IO) {
        val client = clientOrNull() ?: return@withContext emptyList()
        val filter = originPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()
        runCatching {
            readPaged(client, ExerciseSessionRecord::class, start, end, filter)
                .filter { !isIgnoredOrigin(it.metadata.dataOrigin.packageName) }
                .map { rec ->
                    HcSession(
                        id = rec.metadata.id,
                        startTime = rec.startTime,
                        endTime = rec.endTime,
                        title = rec.title,
                        exerciseTypeName = exerciseTypeLabel(rec.exerciseType),
                        originPackage = rec.metadata.dataOrigin.packageName
                    )
                }
        }.getOrDefault(emptyList())
    }

    override suspend fun readHeartRateSamples(
        start: Instant,
        end: Instant,
        originPackage: String?
    ): List<HcHrSample> = withContext(Dispatchers.IO) {
        val client = clientOrNull() ?: return@withContext emptyList()
        val filter = originPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()
        runCatching {
            readPaged(client, HeartRateRecord::class, start, end, filter)
                .filter { !isIgnoredOrigin(it.metadata.dataOrigin.packageName) }
                .flatMap { rec ->
                    rec.samples.map { s ->
                        HcHrSample(time = s.time, bpm = s.beatsPerMinute.toInt())
                    }
                }
                .filter { !it.time.isBefore(start) && !it.time.isAfter(end) }
                .sortedBy { it.time }
                .distinctBy { it.time }
        }.getOrDefault(emptyList())
    }

    override suspend fun readCalorieBuckets(
        start: Instant,
        end: Instant,
        originPackage: String?
    ): List<HcCalorieSlice> = withContext(Dispatchers.IO) {
        val client = clientOrNull() ?: return@withContext emptyList()
        val filter = originPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()
        val result = mutableListOf<HcCalorieSlice>()
        runCatching {
            readPaged(client, TotalCaloriesBurnedRecord::class, start, end, filter)
                .filter { !isIgnoredOrigin(it.metadata.dataOrigin.packageName) }
                .forEach { rec ->
                    result += HcCalorieSlice(
                        startTime = rec.startTime,
                        endTime = rec.endTime,
                        kcal = rec.energy.inKilocalories,
                        kind = CalorieKind.TOTAL
                    )
                }
        }
        runCatching {
            readPaged(client, ActiveCaloriesBurnedRecord::class, start, end, filter)
                .filter { !isIgnoredOrigin(it.metadata.dataOrigin.packageName) }
                .forEach { rec ->
                    result += HcCalorieSlice(
                        startTime = rec.startTime,
                        endTime = rec.endTime,
                        kcal = rec.energy.inKilocalories,
                        kind = CalorieKind.ACTIVE
                    )
                }
        }
        result.sortedBy { it.startTime }
    }

    override suspend fun readTodaySummary(
        date: LocalDate,
        originPackage: String?
    ): HcTodaySummary = withContext(Dispatchers.IO) {
        val client = clientOrNull() ?: return@withContext HcTodaySummary(
            date = date,
            steps = null,
            activeKcal = null,
            workoutMinutes = null,
            sleepMinutes = null,
            latestSpo2Percent = null,
            latestRestingHr = null,
            latestWeightKg = null
        )
        val zone = ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val sleepStart = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val filter = originPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()

        val aggDay = runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        ExerciseSessionRecord.EXERCISE_DURATION_TOTAL
                    ),
                    timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd),
                    dataOriginFilter = filter
                )
            )
        }.getOrNull()

        val sleepDurationMin = runCatching {
            val sleepAgg = client.aggregate(
                AggregateRequest(
                    metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(sleepStart, dayEnd),
                    dataOriginFilter = filter
                )
            )
            sleepAgg[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()
        }.getOrNull()

        val latestSpo2 = runCatching {
            readPaged(client, OxygenSaturationRecord::class, dayStart.minus(Duration.ofHours(12)), dayEnd, filter)
                .maxByOrNull { it.time }?.percentage?.value
        }.getOrNull()

        val latestRhr = runCatching {
            readPaged(client, RestingHeartRateRecord::class, dayStart.minus(Duration.ofDays(3)), dayEnd, filter)
                .maxByOrNull { it.time }?.beatsPerMinute?.toInt()
        }.getOrNull()

        val latestWeight = runCatching {
            readPaged(client, WeightRecord::class, dayStart.minus(Duration.ofDays(30)), dayEnd, emptySet())
                .maxByOrNull { it.time }?.weight?.inKilograms
        }.getOrNull()

        HcTodaySummary(
            date = date,
            steps = aggDay?.get(StepsRecord.COUNT_TOTAL),
            distanceKm = aggDay?.get(DistanceRecord.DISTANCE_TOTAL)?.inKilometers,
            activeKcal = aggDay?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
            workoutMinutes = aggDay?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)?.toMinutes(),
            sleepMinutes = sleepDurationMin,
            latestSpo2Percent = latestSpo2,
            latestRestingHr = latestRhr,
            latestWeightKg = latestWeight
        )
    }

    override suspend fun readDailyActivityHistory(
        days: Int,
        originPackage: String?
    ): List<HcDailyActivity> = withContext(Dispatchers.IO) {
        val client = clientOrNull() ?: return@withContext emptyList()
        val safeDays = days.coerceIn(1, 90)
        val today = LocalDate.now()
        val startDate = today.minusDays((safeDays - 1).toLong())
        val filter = originPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()

        val bucketsByDate = runCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        ExerciseSessionRecord.EXERCISE_DURATION_TOTAL
                    ),
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.atStartOfDay(),
                        today.plusDays(1).atStartOfDay()
                    ),
                    timeRangeSlicer = Period.ofDays(1),
                    dataOriginFilter = filter
                )
            ).associateBy { it.startTime.toLocalDate() }
        }.getOrDefault(emptyMap())

        (0 until safeDays).map { offset ->
            val d = startDate.plusDays(offset.toLong())
            val res = bucketsByDate[d]?.result
            HcDailyActivity(
                date = d,
                steps = res?.get(StepsRecord.COUNT_TOTAL) ?: 0L,
                distanceKm = res?.get(DistanceRecord.DISTANCE_TOTAL)?.inKilometers ?: 0.0,
                activeKcal = res?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories ?: 0.0,
                workoutMinutes = res?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)?.toMinutes() ?: 0L
            )
        }
    }

    override suspend fun readWatchActivities(
        start: Instant,
        end: Instant,
        originPackage: String?
    ): List<HcWatchActivity> = withContext(Dispatchers.IO) {
        val client = clientOrNull() ?: return@withContext emptyList()
        val filter = originPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()
        val sessions = runCatching {
            readPaged(client, ExerciseSessionRecord::class, start, end, filter)
                .filter { !isIgnoredOrigin(it.metadata.dataOrigin.packageName) }
        }.getOrDefault(emptyList())

        sessions.sortedByDescending { it.startTime }.map { rec ->
            val agg = runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            DistanceRecord.DISTANCE_TOTAL,
                            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                            TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                            StepsRecord.COUNT_TOTAL,
                            HeartRateRecord.BPM_AVG,
                            HeartRateRecord.BPM_MAX
                        ),
                        timeRangeFilter = TimeRangeFilter.between(rec.startTime, rec.endTime),
                        dataOriginFilter = setOf(rec.metadata.dataOrigin)
                    )
                )
            }.getOrNull()

            HcWatchActivity(
                id = rec.metadata.id,
                startTime = rec.startTime,
                endTime = rec.endTime,
                durationSec = Duration.between(rec.startTime, rec.endTime).seconds.coerceAtLeast(1L),
                title = rec.title,
                exerciseTypeName = exerciseTypeLabel(rec.exerciseType),
                originPackage = rec.metadata.dataOrigin.packageName,
                distanceKm = agg?.get(DistanceRecord.DISTANCE_TOTAL)?.inKilometers?.takeIf { it > 0.005 },
                activeKcal = agg?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories?.takeIf { it > 0.5 },
                totalKcal = agg?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.takeIf { it > 0.5 },
                avgHr = agg?.get(HeartRateRecord.BPM_AVG)?.toInt(),
                maxHr = agg?.get(HeartRateRecord.BPM_MAX)?.toInt(),
                steps = agg?.get(StepsRecord.COUNT_TOTAL)?.takeIf { it > 0L }
            )
        }
    }

    private suspend fun <T : Record> readPaged(
        client: HealthConnectClient,
        recordType: KClass<T>,
        start: Instant,
        end: Instant,
        originFilter: Set<DataOrigin>?
    ): List<T> {
        val all = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    dataOriginFilter = originFilter ?: emptySet(),
                    pageToken = token
                )
            )
            all += response.records
            token = response.pageToken
        } while (token != null)
        return all
    }

    companion object {
        fun isIgnoredOrigin(pkg: String): Boolean {
            val lower = pkg.lowercase()
            return lower.contains("jefit") || lower == "je.fit"
        }

        fun isLikelyOHealth(pkg: String): Boolean {
            val lower = pkg.lowercase()
            return lower.contains("heytap") ||
                lower.contains("oplus") ||
                lower.contains("oneplus") ||
                lower.contains("ohealth")
        }

        fun exerciseTypeLabel(type: Int): String = when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "STRENGTH_TRAINING"
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "WEIGHTLIFTING"
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "CALISTHENICS"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "RUNNING"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "RUNNING_TREADMILL"
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "WALKING"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "HIKING"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "BIKING"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "BIKING_STATIONARY"
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "ROWING_MACHINE"
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "ELLIPTICAL"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "SWIMMING_POOL"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "SWIMMING_OPEN_WATER"
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "STAIR_CLIMBING"
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "STAIR_CLIMBING"
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "YOGA"
            ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "BADMINTON"
            ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "SOCCER"
            ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "TENNIS"
            ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> "SKIING"
            ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> "WORKOUT"
            else -> "TYPE_$type"
        }
    }
}
