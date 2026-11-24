package com.gymtracker.app.data.health

import com.gymtracker.app.data.db.CalorieKind
import java.time.Instant
import java.time.LocalDate

class FakeHealthDataSource(
    var permissionState: HcPermissionState = HcPermissionState(
        sdkAvailability = HcSdkAvailability.AVAILABLE,
        coreReadGranted = true,
        historyGranted = true,
        historySupported = true,
        backgroundGranted = true,
        backgroundSupported = true
    ),
    var origins: List<HcOriginInfo> = listOf(
        HcOriginInfo("com.heytap.health.international", sessionCount = 5, hrRecordCount = 50, isLikelyOHealth = true)
    ),
    var sessions: MutableList<HcSession> = mutableListOf(),
    var hrSamples: MutableList<HcHrSample> = mutableListOf(),
    var calorieSlices: MutableList<HcCalorieSlice> = mutableListOf(),
    var todaySummary: HcTodaySummary = HcTodaySummary(
        date = LocalDate.now(),
        steps = 4520,
        activeKcal = 310.0,
        workoutMinutes = 43,
        sleepMinutes = 450,
        latestSpo2Percent = 98.0,
        latestRestingHr = 58,
        latestWeightKg = 105.0
    )
) : HealthDataSource {

    override suspend fun getPermissionState(): HcPermissionState = permissionState

    override fun requiredCorePermissions(): Set<String> = emptySet()

    override fun optionalHistoryAndBgPermissions(state: HcPermissionState): Set<String> = emptySet()

    override suspend fun discoverOrigins(daysBack: Long): List<HcOriginInfo> = origins

    override suspend fun readExerciseSessions(
        start: Instant,
        end: Instant,
        originPackage: String?
    ): List<HcSession> = sessions.filter { s ->
        (originPackage == null || s.originPackage == originPackage) &&
            !s.endTime.isBefore(start) && !s.startTime.isAfter(end)
    }

    override suspend fun readHeartRateSamples(
        start: Instant,
        end: Instant,
        originPackage: String?
    ): List<HcHrSample> = hrSamples.filter { !it.time.isBefore(start) && !it.time.isAfter(end) }
        .sortedBy { it.time }

    override suspend fun readCalorieBuckets(
        start: Instant,
        end: Instant,
        originPackage: String?
    ): List<HcCalorieSlice> = calorieSlices.filter { !it.endTime.isBefore(start) && !it.startTime.isAfter(end) }
        .sortedBy { it.startTime }

    override suspend fun readTodaySummary(
        date: LocalDate,
        originPackage: String?
    ): HcTodaySummary = todaySummary.copy(date = date)

    companion object {
        /** Builds realistic 20s HR and 1-min calorie data across [start, end]. */
        fun generateWatchWorkout(
            sessionId: String,
            start: Instant,
            end: Instant,
            originPackage: String = "com.heytap.health.international",
            baseHr: Int = 108,
            kcalPerMin: Double = 6.5
        ): Triple<HcSession, List<HcHrSample>, List<HcCalorieSlice>> {
            val session = HcSession(
                id = sessionId,
                startTime = start,
                endTime = end,
                title = "Силове тренування",
                exerciseTypeName = "STRENGTH_TRAINING",
                originPackage = originPackage
            )
            val hrList = mutableListOf<HcHrSample>()
            var t = start
            var step = 0
            while (!t.isAfter(end.plusSeconds(120))) {
                val bpm = baseHr + ((step % 6) * 4)
                hrList += HcHrSample(t, bpm)
                t = t.plusSeconds(20)
                step++
            }
            val calList = mutableListOf<HcCalorieSlice>()
            var cStart = start
            while (cStart.isBefore(end)) {
                val cEnd = cStart.plusSeconds(60).let { if (it.isAfter(end)) end else it }
                calList += HcCalorieSlice(cStart, cEnd, kcalPerMin, CalorieKind.TOTAL)
                cStart = cEnd
            }
            return Triple(session, hrList, calList)
        }
    }
}
