package com.gymtracker.app.data.health

import androidx.room.withTransaction
import com.gymtracker.app.data.db.CalorieBucketEntity
import com.gymtracker.app.data.db.GymTrackerDatabase
import com.gymtracker.app.data.db.HcStatus
import com.gymtracker.app.data.db.HrSampleEntity
import com.gymtracker.app.data.db.SyncState
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.WorkoutMetricsRecomputer
import com.gymtracker.app.domain.HrAnalyticsEngine
import com.gymtracker.app.domain.HrPoint
import com.gymtracker.app.domain.SessionCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class BackfillProgress(
    val done: Int,
    val total: Int,
    val matched: Int,
    val hrOnly: Int,
    val noData: Int
)

@Singleton
class HealthSyncRepository @Inject constructor(
    private val database: GymTrackerDatabase,
    private val healthDataSource: HealthDataSource,
    private val prefsRepo: UserPreferencesRepository,
    private val metricsRecomputer: WorkoutMetricsRecomputer
) {
    suspend fun resolveWatchOrigin(): String? {
        val stored = prefsRepo.getOnce().watchSourcePackage
        if (stored != null) return stored
        val origins = healthDataSource.discoverOrigins(30)
        val best = origins.firstOrNull { it.isLikelyOHealth } ?: origins.firstOrNull()
        if (best != null) {
            prefsRepo.setWatchSourcePackage(best.packageName)
        }
        return best?.packageName
    }

    suspend fun getTodaySummary(date: LocalDate = LocalDate.now()): HcTodaySummary {
        val origin = resolveWatchOrigin()
        return healthDataSource.readTodaySummary(date, origin)
    }

    suspend fun listCandidateSessions(workoutId: Long): List<HcSession> = withContext(Dispatchers.IO) {
        val workout = database.workoutDao().getWorkoutById(workoutId) ?: return@withContext emptyList()
        val origin = resolveWatchOrigin()
        val end = workout.endedAt ?: workout.startedAt.plusSeconds(3600)
        healthDataSource.readExerciseSessions(
            start = workout.startedAt.minusSeconds(3 * 3600L),
            end = end.plusSeconds(3 * 3600L),
            originPackage = origin
        )
    }

    suspend fun unlinkHealthSession(workoutId: Long) = withContext(Dispatchers.IO) {
        val workout = database.workoutDao().getWorkoutById(workoutId) ?: return@withContext
        database.withTransaction {
            database.metricsDao().deleteHrSamples(workoutId)
            database.metricsDao().deleteCalorieBuckets(workoutId)
            database.workoutDao().updateWorkout(
                workout.copy(
                    hcStatus = HcStatus.NOT_ATTEMPTED,
                    hcSessionId = null,
                    hcSessionStart = null,
                    hcSessionEnd = null,
                    hcExerciseType = null,
                    hcLastAttemptAt = Instant.now(),
                    updatedAt = Instant.now(),
                    syncState = SyncState.PENDING_UPLOAD
                )
            )
        }
        metricsRecomputer.recompute(workoutId)
    }

    suspend fun syncWorkout(
        workoutId: Long,
        manualSessionId: String? = null
    ): HcStatus = withContext(Dispatchers.IO) {
        val permState = healthDataSource.getPermissionState()
        if (!permState.coreReadGranted) {
            return@withContext HcStatus.NOT_ATTEMPTED
        }

        val workoutDao = database.workoutDao()
        val workout = workoutDao.getWorkoutById(workoutId) ?: return@withContext HcStatus.ERROR
        if (workout.isInProgress) return@withContext HcStatus.NOT_ATTEMPTED

        val origin = resolveWatchOrigin()
        val wStart = workout.startedAt
        val wEnd = workout.endedAt ?: wStart.plusSeconds(45 * 60L)

        val candidates = healthDataSource.readExerciseSessions(
            start = wStart.minusSeconds(3 * 3600L),
            end = wEnd.plusSeconds(3 * 3600L),
            originPackage = origin
        )

        val rawWinStart = wStart.minusSeconds(60)
        val rawWinEnd = wEnd.plusSeconds(180)
        val initialHr = healthDataSource.readHeartRateSamples(rawWinStart, rawWinEnd, origin)

        val outcome = if (manualSessionId != null) {
            val chosen = candidates.firstOrNull { it.id == manualSessionId }
            if (chosen != null) {
                val c = SessionCandidate(chosen.id, chosen.startTime, chosen.endTime, chosen.title, chosen.exerciseTypeName)
                HrAnalyticsEngine.matchSession(
                    workoutStart = wStart,
                    workoutEnd = wEnd,
                    candidates = listOf(c),
                    rawHrPoints = initialHr.map { HrPoint(it.time, it.bpm) }
                ).copy(
                    status = HcStatus.MATCHED,
                    matchedCandidate = c,
                    analysisWindowStart = minOf(wStart, chosen.startTime).minusSeconds(60),
                    analysisWindowEnd = maxOf(wEnd, chosen.endTime).plusSeconds(180)
                )
            } else {
                HrAnalyticsEngine.matchSession(
                    workoutStart = wStart,
                    workoutEnd = wEnd,
                    candidates = candidates.map {
                        SessionCandidate(it.id, it.startTime, it.endTime, it.title, it.exerciseTypeName)
                    },
                    rawHrPoints = initialHr.map { HrPoint(it.time, it.bpm) }
                )
            }
        } else {
            HrAnalyticsEngine.matchSession(
                workoutStart = wStart,
                workoutEnd = wEnd,
                candidates = candidates.map {
                    SessionCandidate(it.id, it.startTime, it.endTime, it.title, it.exerciseTypeName)
                },
                rawHrPoints = initialHr.map { HrPoint(it.time, it.bpm) }
            )
        }

        val finalHr = if (outcome.status == HcStatus.MATCHED) {
            healthDataSource.readHeartRateSamples(
                outcome.analysisWindowStart,
                outcome.analysisWindowEnd,
                origin
            )
        } else {
            initialHr
        }

        val calSlices = if (outcome.status == HcStatus.MATCHED || outcome.status == HcStatus.HR_ONLY) {
            healthDataSource.readCalorieBuckets(
                outcome.analysisWindowStart,
                outcome.analysisWindowEnd,
                origin
            )
        } else {
            emptyList()
        }

        val now = Instant.now()
        database.withTransaction {
            val mDao = database.metricsDao()
            mDao.deleteHrSamples(workoutId)
            mDao.deleteCalorieBuckets(workoutId)
            if (finalHr.isNotEmpty()) {
                mDao.insertHrSamples(finalHr.map { HrSampleEntity(workoutId, it.time, it.bpm) })
            }
            if (calSlices.isNotEmpty()) {
                mDao.insertCalorieBuckets(
                    calSlices.map {
                        CalorieBucketEntity(
                            workoutId = workoutId,
                            startTime = it.startTime,
                            endTime = it.endTime,
                            kcal = it.kcal,
                            kind = it.kind
                        )
                    }
                )
            }
            workoutDao.updateWorkout(
                workout.copy(
                    hcStatus = outcome.status,
                    hcSessionId = outcome.matchedCandidate?.id,
                    hcSessionStart = outcome.matchedCandidate?.startTime,
                    hcSessionEnd = outcome.matchedCandidate?.endTime,
                    hcExerciseType = outcome.matchedCandidate?.exerciseTypeName,
                    hcLastAttemptAt = now,
                    updatedAt = now,
                    syncState = SyncState.PENDING_UPLOAD
                )
            )
        }

        metricsRecomputer.recompute(workoutId)
        outcome.status
    }

    suspend fun syncPendingWorkouts() {
        val pending = database.workoutDao().getByHcStatus(listOf(HcStatus.PENDING, HcStatus.NOT_ATTEMPTED))
            .filter { !it.excludedFromStats && it.startedAt.isAfter(Instant.now().minusSeconds(7 * 86400L)) }
        for (w in pending) {
            syncWorkout(w.id)
        }
    }

    fun backfillWorkouts(fromInstant: Instant): Flow<BackfillProgress> = flow {
        val list = database.workoutDao().getWorkoutsSince(fromInstant)
        var done = 0
        var matched = 0
        var hrOnly = 0
        var noData = 0
        val total = list.size
        emit(BackfillProgress(0, total, 0, 0, 0))
        for (w in list) {
            when (syncWorkout(w.id)) {
                HcStatus.MATCHED -> matched++
                HcStatus.HR_ONLY -> hrOnly++
                else -> noData++
            }
            done++
            emit(BackfillProgress(done, total, matched, hrOnly, noData))
        }
    }
}
