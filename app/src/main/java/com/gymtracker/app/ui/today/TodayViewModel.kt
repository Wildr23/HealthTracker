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
import com.gymtracker.app.data.prefs.UserPreferences
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.WorkoutRepository
import com.gymtracker.app.domain.MuscleRecoveryEngine
import com.gymtracker.app.domain.MuscleRecoverySnapshot
import com.gymtracker.app.domain.RecoveryMuscleGroup
import com.gymtracker.app.work.WorkoutTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class TodayUiState(
    val activeWorkout: WorkoutFull? = null,
    val lastWorkout: WorkoutFull? = null,
    val totalWorkoutCount: Int = 0,
    val routines: List<RoutineWithDays> = emptyList(),
    val pendingHcCount: Int = 0,
    val permissionState: HcPermissionState? = null,
    val todaySummary: HcTodaySummary? = null,
    val tookPreWorkout: Boolean = false,
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
    prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private val permState = MutableStateFlow<HcPermissionState?>(null)
    private val todaySummary = MutableStateFlow<HcTodaySummary?>(null)
    private val preWorkoutFlag = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)

    val uiState: StateFlow<TodayUiState> = combine(
        workoutRepository.observeActiveWorkout(),
        workoutRepository.observeCompletedWorkouts(),
        routineDao.observeRoutinesWithDays(),
        workoutRepository.observePendingHcCount(),
        combine(permState, todaySummary, preWorkoutFlag, refreshing, prefsRepo.preferences) { p, s, pw, r, prefs ->
            ExtraBundle(p, s, pw, r, prefs)
        }
    ) { active, completed, routines, pending, extra ->
        val nonExcluded = completed.filter { !it.workout.excludedFromStats }
        val injuredMap = extra.prefs.injuredMuscles.mapNotNull { (k, iso) ->
            val grp = runCatching { RecoveryMuscleGroup.valueOf(k) }.getOrNull() ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: LocalDate.now()
            grp to date
        }.toMap()

        val recSnapshot = MuscleRecoveryEngine.computeRecoveryState(
            completedWorkouts = nonExcluded,
            now = Instant.now(),
            injuredMuscles = injuredMap,
            sleepMinutesLastNight = extra.s?.sleepMinutes?.toInt(),
            formula = extra.prefs.preferredFormula,
            bodyweightKg = extra.prefs.defaultBodyWeightKg
        )

        TodayUiState(
            activeWorkout = active,
            lastWorkout = nonExcluded.firstOrNull(),
            totalWorkoutCount = nonExcluded.size,
            routines = routines,
            pendingHcCount = pending,
            permissionState = extra.p,
            todaySummary = extra.s,
            tookPreWorkout = extra.pw,
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
        preWorkoutFlag.value = took
    }

    fun requiredPermissionsToRequest(): Set<String> {
        val state = permState.value
        val core = healthDataSource.requiredCorePermissions()
        return if (state != null) core + healthDataSource.optionalHistoryAndBgPermissions(state) else core
    }

    fun startWorkout(routineDayId: Long?, title: String?, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val wId = workoutRepository.startWorkout(routineDayId, preWorkoutFlag.value, title)
            WorkoutTimerService.start(context, System.currentTimeMillis(), title ?: "Gym Workout")
            onStarted(wId)
        }
    }

    private data class ExtraBundle(
        val p: HcPermissionState?,
        val s: HcTodaySummary?,
        val pw: Boolean,
        val r: Boolean,
        val prefs: UserPreferences
    )
}
