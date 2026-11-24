package com.gymtracker.app.data.health

import com.gymtracker.app.data.db.CalorieKind
import java.time.Instant
import java.time.LocalDate

enum class HcSdkAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE
}

data class HcPermissionState(
    val sdkAvailability: HcSdkAvailability,
    val coreReadGranted: Boolean,
    val historyGranted: Boolean,
    val historySupported: Boolean,
    val backgroundGranted: Boolean,
    val backgroundSupported: Boolean
)

data class HcOriginInfo(
    val packageName: String,
    val sessionCount: Int,
    val hrRecordCount: Int,
    val isLikelyOHealth: Boolean
)

data class HcSession(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val title: String?,
    val exerciseTypeName: String,
    val originPackage: String
)

data class HcHrSample(
    val time: Instant,
    val bpm: Int
)

data class HcCalorieSlice(
    val startTime: Instant,
    val endTime: Instant,
    val kcal: Double,
    val kind: CalorieKind
)

data class HcTodaySummary(
    val date: LocalDate,
    val steps: Long?,
    val activeKcal: Double?,
    val workoutMinutes: Long?,
    val sleepMinutes: Long?,
    val latestSpo2Percent: Double?,
    val latestRestingHr: Int?,
    val latestWeightKg: Double?
)

interface HealthDataSource {
    suspend fun getPermissionState(): HcPermissionState
    fun requiredCorePermissions(): Set<String>
    fun optionalHistoryAndBgPermissions(state: HcPermissionState): Set<String>
    suspend fun discoverOrigins(daysBack: Long = 30): List<HcOriginInfo>
    suspend fun readExerciseSessions(start: Instant, end: Instant, originPackage: String?): List<HcSession>
    suspend fun readHeartRateSamples(start: Instant, end: Instant, originPackage: String?): List<HcHrSample>
    suspend fun readCalorieBuckets(start: Instant, end: Instant, originPackage: String?): List<HcCalorieSlice>
    suspend fun readTodaySummary(date: LocalDate, originPackage: String?): HcTodaySummary
}
