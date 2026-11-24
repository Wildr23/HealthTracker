package com.gymtracker.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.health.CachedDailyActivity
import com.gymtracker.app.data.health.CachedWatchActivity
import com.gymtracker.app.data.health.HcPermissionState
import com.gymtracker.app.data.health.HealthDataSource
import com.gymtracker.app.data.health.WatchActivityRepository
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WatchChartMetric {
    STEPS,
    DISTANCE_KM,
    ACTIVE_KCAL
}

enum class WatchActivityFilter {
    ALL,
    WALK,
    RUN,
    BIKE,
    OTHER
}

data class WatchActivityUiState(
    val dailyHistory: List<CachedDailyActivity> = emptyList(),
    val activities: List<CachedWatchActivity> = emptyList(),
    val selectedDays: Int = 14,
    val chartMetric: WatchChartMetric = WatchChartMetric.STEPS,
    val filter: WatchActivityFilter = WatchActivityFilter.ALL,
    val dailyStepGoal: Int = 10000,
    val isSyncing: Boolean = false,
    val lastSyncedEpochSec: Long = 0L,
    val permissionState: HcPermissionState? = null
)

@HiltViewModel
class WatchActivityViewModel @Inject constructor(
    private val watchActivityRepository: WatchActivityRepository,
    private val healthDataSource: HealthDataSource,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private val selectedDays = MutableStateFlow(14)
    private val chartMetric = MutableStateFlow(WatchChartMetric.STEPS)
    private val filter = MutableStateFlow(WatchActivityFilter.ALL)
    private val syncing = MutableStateFlow(false)
    private val permState = MutableStateFlow<HcPermissionState?>(null)

    val uiState: StateFlow<WatchActivityUiState> = combine(
        watchActivityRepository.dailyHistory,
        watchActivityRepository.watchActivities,
        watchActivityRepository.lastSyncedEpochSec,
        prefsRepo.preferences,
        combine(selectedDays, chartMetric, filter, syncing, permState) { d, m, f, s, p ->
            FilterBundle(d, m, f, s, p)
        }
    ) { daily, sessions, lastSync, prefs, fb ->
        val filteredSessions = sessions.filter { s ->
            when (fb.f) {
                WatchActivityFilter.ALL -> true
                WatchActivityFilter.WALK -> s.exerciseTypeName in setOf("WALKING", "HIKING")
                WatchActivityFilter.RUN -> s.exerciseTypeName in setOf("RUNNING", "RUNNING_TREADMILL")
                WatchActivityFilter.BIKE -> s.exerciseTypeName in setOf("BIKING", "BIKING_STATIONARY")
                WatchActivityFilter.OTHER -> s.exerciseTypeName !in setOf(
                    "WALKING", "HIKING", "RUNNING", "RUNNING_TREADMILL", "BIKING", "BIKING_STATIONARY"
                )
            }
        }
        WatchActivityUiState(
            dailyHistory = daily.takeLast(fb.d),
            activities = filteredSessions,
            selectedDays = fb.d,
            chartMetric = fb.m,
            filter = fb.f,
            dailyStepGoal = prefs.dailyStepGoal,
            isSyncing = fb.s,
            lastSyncedEpochSec = lastSync,
            permissionState = fb.p
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatchActivityUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            syncing.value = true
            runCatching {
                val p = healthDataSource.getPermissionState()
                permState.value = p
                if (p.coreReadGranted) {
                    watchActivityRepository.syncLastDays(30)
                }
            }
            syncing.value = false
        }
    }

    fun selectDays(days: Int) {
        selectedDays.value = days
    }

    fun selectMetric(metric: WatchChartMetric) {
        chartMetric.value = metric
    }

    fun selectFilter(f: WatchActivityFilter) {
        filter.value = f
    }

    private data class FilterBundle(
        val d: Int,
        val m: WatchChartMetric,
        val f: WatchActivityFilter,
        val s: Boolean,
        val p: HcPermissionState?
    )
}
