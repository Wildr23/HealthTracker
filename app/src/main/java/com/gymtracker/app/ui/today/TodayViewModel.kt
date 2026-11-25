package com.gymtracker.app.ui.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.db.RoutineDao
import com.gymtracker.app.data.db.RoutineWithDays
import com.gymtracker.app.data.db.WorkoutFull
import com.gymtracker.app.data.health.HcPermissionState
import com.gymtracker.app.data.health.HcTodaySummary
import com.gymtracker.app.data.health.HealthDataSource
import com.gymtracker.app.data.health.HealthSyncRepository
import com.gymtracker.app.data.health.WatchActivityRepository
import com.gymtracker.app.data.prefs.UserPreferences
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.SupplementRepository
import com.gymtracker.app.data.repo.WorkoutRepository
import com.gymtracker.app.domain.CaffeineDose
import com.gymtracker.app.domain.CaffeineHalfLifeEngine
import com.gymtracker.app.domain.CaffeineSleepStatus
import com.gymtracker.app.domain.MuscleRecoveryEngine
import com.gymtracker.app.domain.MuscleRecoverySnapshot
import com.gymtracker.app.domain.RecoveryMuscleGroup
import com.gymtracker.app.domain.SupplementScheduleEngine
import com.gymtracker.app.work.WorkoutTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

data class TodayUiState(
    val activeWorkout: WorkoutFull? = null,
    val lastWorkout: WorkoutFull? = null,
    val totalWorkoutCount: Int = 0,
    val routines: List<RoutineWithDays> = emptyList(),
    val pendingHcCount: Int = 0,
    val permissionState: HcPermissionState? = null,
    val todaySummary: HcTodaySummary? = null,
    val dailyStepGoal: Int = 10000,
    val tookPreWorkout: Boolean = false,
    val recentPreWorkoutMinAgo: Long? = null,
    val stackTakenToday: Int = 0,
    val stackTotalToday: Int = 0,
    val caffeineBedtimeMg: Int = 0,
    val caffeineTotalTodayMg: Int = 0,
    val caffeineSleepStatus: CaffeineSleepStatus = CaffeineSleepStatus.SAFE,
    val bedtimeMinutesOfDay: Int = 0,
    val isRefreshingHc: Boolean = false,
    val recovery: MuscleRecoverySnapshot? = null
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutRepository: WorkoutRepository,
    routineDao: RoutineDao,
    private val healthDataSource: HealthDataSource,
    private val healthSyncRepository: HealthSyncRepository,
    private val watchActivityRepository: WatchActivityRepository,
    private val supplementRepository: SupplementRepository,
    prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private val permState = MutableStateFlow<HcPermissionState?>(null)
    private val todaySummary = MutableStateFlow<HcTodaySummary?>(null)
    private val preWorkoutOverride = MutableStateFlow<Boolean?>(null)
    private val refreshing = MutableStateFlow(false)

    private val stackSummaryFlow = combine(
        supplementRepository.observeAllActiveStack(),
        supplementRepository.observeLogsSince(Instant.now().minusSeconds(36 * 3600L)),
        supplementRepository.observeRecentPreWorkoutLog(90L)
    ) { stack, logs, recentPre ->
        Triple(stack, logs, recentPre)
    }

    val uiState: StateFlow<TodayUiState> = combine(
        workoutRepository.observeActiveWorkout(),
        workoutRepository.observeCompletedWorkouts(),
        routineDao.observeRoutinesWithDays(),
        workoutRepository.observePendingHcCount(),
        combine(
            permState,
            todaySummary,
            preWorkoutOverride,
            refreshing,
            combine(prefsRepo.preferences, stackSummaryFlow) { prefs, stackTriple -> prefs to stackTriple }
        ) { p, s, pw, r, pair ->
            ExtraBundle(p, s, pw, r, pair.first, pair.second)
        }
    ) { active, completed, routines, pending, extra ->
        val nonExcluded = completed.filter { !it.workout.excludedFromStats }
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.now(zone)
        val todayStart = today.atStartOfDay(zone).toInstant()

        val injuredMap = extra.prefs.injuredMuscles.mapNotNull { (k, iso) ->
            val grp = runCatching { RecoveryMuscleGroup.valueOf(k) }.getOrNull() ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: today
            grp to date
        }.toMap()

        val recSnapshot = MuscleRecoveryEngine.computeRecoveryState(
            completedWorkouts = nonExcluded,
            now = now,
            injuredMuscles = injuredMap,
            sleepMinutesLastNight = extra.s?.sleepMinutes?.toInt(),
            formula = extra.prefs.preferredFormula,
            bodyweightKg = extra.prefs.defaultBodyWeightKg
        )

        val (allActiveStack, recentLogs, recentPreLog) = extra.stackTriple
        val visibleToday = allActiveStack.filter {
            it.item.notes != SupplementRepository.QUICK_CAFFEINE_TAG &&
                SupplementScheduleEngine.isCycleActiveOnDate(it.item, today) &&
                SupplementScheduleEngine.isScheduledForDate(it.schedules, today)
        }
        val takenIdsToday = recentLogs
            .filter { !it.takenAt.isBefore(todayStart) }
            .map { it.supplementId }
            .toSet()
        val stackTaken = visibleToday.count { it.item.id in takenIdsToday }

        val itemsById = allActiveStack.associateBy { it.item.id }
        val caffeineDoses = recentLogs.mapNotNull { log ->
            val item = itemsById[log.supplementId]?.item
            val mg = log.caffeineMg ?: item?.caffeineMgPerServing ?: 0
            if (mg <= 0) return@mapNotNull null
            CaffeineDose(log.takenAt, mg, item?.nameEn ?: "Caffeine")
        }
        val cafAnalysis = CaffeineHalfLifeEngine.analyze(
            doses = caffeineDoses,
            now = now,
            bedtimeMinutesOfDay = extra.prefs.bedtimeMinutesOfDay,
            halfLifeHours = extra.prefs.caffeineHalfLifeHours,
            sleepThresholdMg = extra.prefs.caffeineSleepThresholdMg,
            zoneId = zone
        )

        val preMinAgo = recentPreLog?.let {
            Duration.between(it.takenAt, now).toMinutes().coerceAtLeast(0L)
        }
        val effectivePre = extra.pw ?: (recentPreLog != null)

        TodayUiState(
            activeWorkout = active,
            lastWorkout = nonExcluded.firstOrNull(),
            totalWorkoutCount = nonExcluded.size,
            routines = routines,
            pendingHcCount = pending,
            permissionState = extra.p,
            todaySummary = extra.s,
            dailyStepGoal = extra.prefs.dailyStepGoal,
            tookPreWorkout = effectivePre,
            recentPreWorkoutMinAgo = preMinAgo,
            stackTakenToday = stackTaken,
            stackTotalToday = visibleToday.size,
            caffeineBedtimeMg = cafAnalysis.projectedMgAtBedtime.roundToInt(),
            caffeineTotalTodayMg = cafAnalysis.totalTodayMg,
            caffeineSleepStatus = cafAnalysis.status,
            bedtimeMinutesOfDay = extra.prefs.bedtimeMinutesOfDay,
            isRefreshingHc = extra.r,
            recovery = recSnapshot
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState())

    init {
        refreshHealth()
    }

    fun refreshHealth() {
        viewModelScope.launch {
            refreshing.value = true
            runCatching {
                val p = healthDataSource.getPermissionState()
                permState.value = p
                if (p.coreReadGranted) {
                    todaySummary.value = healthSyncRepository.getTodaySummary()
                    watchActivityRepository.syncLastDays(30)
                }
            }
            refreshing.value = false
        }
    }

    fun syncPendingNow() {
        viewModelScope.launch {
            refreshing.value = true
            runCatching {
                healthSyncRepository.syncPendingWorkouts()
                todaySummary.value = healthSyncRepository.getTodaySummary()
            }
            refreshing.value = false
        }
    }

    fun setPreWorkout(took: Boolean) {
        preWorkoutOverride.value = took
    }

    fun requiredPermissionsToRequest(): Set<String> {
        val state = permState.value
        val core = healthDataSource.requiredCorePermissions()
        return if (state != null) core + healthDataSource.optionalHistoryAndBgPermissions(state) else core
    }

    fun startWorkout(routineDayId: Long?, title: String?, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val flag = uiState.value.tookPreWorkout
            val wId = workoutRepository.startWorkout(routineDayId, flag, title)
            preWorkoutOverride.value = null
            WorkoutTimerService.start(context, System.currentTimeMillis(), title ?: "Gym Workout")
            onStarted(wId)
        }
    }

    private data class ExtraBundle(
        val p: HcPermissionState?,
        val s: HcTodaySummary?,
        val pw: Boolean?,
        val r: Boolean,
        val prefs: UserPreferences,
        val stackTriple: Triple<List<com.gymtracker.app.data.db.SupplementWithSchedules>, List<com.gymtracker.app.data.db.SupplementLogEntity>, com.gymtracker.app.data.db.SupplementLogEntity?>
    )
}
