package com.gymtracker.app.data.health

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gymtracker.app.data.db.WorkoutDao
import com.gymtracker.app.data.db.WorkoutEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.watchActivityStore: DataStore<Preferences> by preferencesDataStore(name = "watch_activity_cache")

@Serializable
data class CachedDailyActivity(
    val dateIso: String,
    val steps: Long,
    val distanceKm: Double,
    val activeKcal: Double,
    val workoutMinutes: Long
)

@Serializable
data class CachedWatchActivity(
    val id: String,
    val startEpochSec: Long,
    val endEpochSec: Long,
    val durationSec: Long,
    val title: String? = null,
    val exerciseTypeName: String,
    val originPackage: String,
    val distanceKm: Double? = null,
    val activeKcal: Double? = null,
    val totalKcal: Double? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val steps: Long? = null
)

object WatchActivityDeduplicator {
    private val gymStrengthTypes = setOf(
        "STRENGTH_TRAINING",
        "WEIGHTLIFTING",
        "CALISTHENICS",
        "WORKOUT"
    )

    /**
     * Filters out Health Connect sessions already attached to a logged gym workout (`hcSessionId`)
     * or strength/generic workouts overlapping an existing gym session by >= 50% of duration.
     * Walking, running, biking, hiking, swimming, etc. are always kept intact.
     */
    fun filterNonDuplicateSessions(
        sessions: List<HcWatchActivity>,
        gymWorkouts: List<WorkoutEntity>
    ): List<HcWatchActivity> {
        val validGym = gymWorkouts.filter { !it.excludedFromStats && !it.isInProgress && it.deletedAt == null }
        val linkedHcIds = validGym.mapNotNull { it.hcSessionId }.toSet()
        val gymWindows = validGym.map { w ->
            w.startedAt.epochSecond to (w.endedAt?.epochSecond ?: (w.startedAt.epochSecond + 45 * 60L))
        }

        return sessions.filter { s ->
            if (s.id in linkedHcIds) return@filter false
            if (s.exerciseTypeName in gymStrengthTypes) {
                val sStart = s.startTime.epochSecond
                val sEnd = s.endTime.epochSecond
                val sDur = (sEnd - sStart).coerceAtLeast(1L).toDouble()
                val overlapsGym = gymWindows.any { (gStart, gEnd) ->
                    val ovStart = maxOf(sStart, gStart)
                    val ovEnd = minOf(sEnd, gEnd)
                    ovEnd > ovStart && ((ovEnd - ovStart).toDouble() / sDur) >= 0.5
                }
                if (overlapsGym) return@filter false
            }
            true
        }
    }
}

@Singleton
class WatchActivityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthDataSource: HealthDataSource,
    private val healthSyncRepository: HealthSyncRepository,
    private val workoutDao: WorkoutDao
) {
    private object Keys {
        val DAILY_JSON = stringPreferencesKey("daily_activity_json")
        val SESSIONS_JSON = stringPreferencesKey("watch_sessions_json")
        val LAST_SYNC_SEC = longPreferencesKey("watch_activity_last_sync_sec")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val dailyHistory: Flow<List<CachedDailyActivity>> =
        context.watchActivityStore.data.map { prefs ->
            prefs[Keys.DAILY_JSON]?.let { raw ->
                runCatching { json.decodeFromString<List<CachedDailyActivity>>(raw) }.getOrNull()
            } ?: emptyList()
        }

    val watchActivities: Flow<List<CachedWatchActivity>> =
        context.watchActivityStore.data.map { prefs ->
            prefs[Keys.SESSIONS_JSON]?.let { raw ->
                runCatching { json.decodeFromString<List<CachedWatchActivity>>(raw) }.getOrNull()
            } ?: emptyList()
        }

    val lastSyncedEpochSec: Flow<Long> =
        context.watchActivityStore.data.map { prefs ->
            prefs[Keys.LAST_SYNC_SEC] ?: 0L
        }

    suspend fun syncLastDays(days: Int = 30) = withContext(Dispatchers.IO) {
        val perm = healthDataSource.getPermissionState()
        if (!perm.coreReadGranted) return@withContext

        val origin = healthSyncRepository.resolveWatchOrigin()
        val now = Instant.now()
        val start = now.minusSeconds(days.coerceIn(1, 90) * 86400L)

        val daily = healthDataSource.readDailyActivityHistory(days, origin).map {
            CachedDailyActivity(
                dateIso = it.date.toString(),
                steps = it.steps,
                distanceKm = it.distanceKm,
                activeKcal = it.activeKcal,
                workoutMinutes = it.workoutMinutes
            )
        }

        val rawSessions = healthDataSource.readWatchActivities(start, now, origin)
        val filtered = WatchActivityDeduplicator.filterNonDuplicateSessions(
            sessions = rawSessions,
            gymWorkouts = workoutDao.getAllWorkoutsOnce()
        ).map { s ->
            CachedWatchActivity(
                id = s.id,
                startEpochSec = s.startTime.epochSecond,
                endEpochSec = s.endTime.epochSecond,
                durationSec = s.durationSec,
                title = s.title,
                exerciseTypeName = s.exerciseTypeName,
                originPackage = s.originPackage,
                distanceKm = s.distanceKm,
                activeKcal = s.activeKcal,
                totalKcal = s.totalKcal,
                avgHr = s.avgHr,
                maxHr = s.maxHr,
                steps = s.steps
            )
        }

        context.watchActivityStore.edit { prefs ->
            prefs[Keys.DAILY_JSON] = json.encodeToString(daily)
            prefs[Keys.SESSIONS_JSON] = json.encodeToString(filtered)
            prefs[Keys.LAST_SYNC_SEC] = now.epochSecond
        }
    }
}
