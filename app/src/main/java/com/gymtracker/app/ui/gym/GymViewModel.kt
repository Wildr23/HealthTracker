package com.gymtracker.app.ui.gym

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.db.ExerciseDao
import com.gymtracker.app.data.db.ExerciseEntity
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.RoutineDao
import com.gymtracker.app.data.db.RoutineWithDays
import com.gymtracker.app.data.db.WorkoutFull
import com.gymtracker.app.data.health.HealthSyncRepository
import com.gymtracker.app.data.prefs.UserPreferences
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.WorkoutRepository
import com.gymtracker.app.domain.MuscleRecoveryEngine
import com.gymtracker.app.domain.MuscleRecoverySnapshot
import com.gymtracker.app.domain.OneRmEngine
import com.gymtracker.app.domain.RecoveryMuscleGroup
import com.gymtracker.app.ui.components.MuscleCategoryGroup
import com.gymtracker.app.work.WorkoutTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlin.math.roundToInt

sealed interface HistoryListItem {
    data class MonthHeader(val yearMonth: YearMonth) : HistoryListItem
    data class BreakSeparator(val months: Int, val days: Long) : HistoryListItem
    data class WorkoutRow(val workout: WorkoutFull) : HistoryListItem
}

data class ExerciseCatalogItem(
    val exercise: ExerciseEntity,
    val usageCount: Int,
    val bestE1rmKg: Double?
)

data class Big3Summary(
    val benchKg: Double?,
    val squatKg: Double?,
    val deadliftKg: Double?
) {
    val totalKg: Double?
        get() = if (benchKg != null && squatKg != null && deadliftKg != null) {
            OneRmEngine.roundToTwoDecimals(benchKg + squatKg + deadliftKg)
        } else null
}

data class WeeklyKpiSummary(
    val volumeKg: Double = 0.0,
    val volumeDeltaPercent: Int? = null,
    val workoutMinutes: Int = 0,
    val timeDeltaPercent: Int? = null,
    val totalSessions: Int = 0,
    val currentWeekStreak: Int = 0
)

data class GymUiState(
    val historyItems: List<HistoryListItem> = emptyList(),
    val workoutsByDate: Map<LocalDate, List<WorkoutFull>> = emptyMap(),
    val weeklyKpis: WeeklyKpiSummary = WeeklyKpiSummary(),
    val routines: List<RoutineWithDays> = emptyList(),
    val lastDoneByDayId: Map<Long, LocalDate> = emptyMap(),
    val dayReadinessByDayId: Map<Long, Int> = emptyMap(),
    val recovery: MuscleRecoverySnapshot? = null,
    val selectedRoutineDayId: Long? = null,
    val exercises: List<ExerciseCatalogItem> = emptyList(),
    val selectedCategory: MuscleCategoryGroup? = null,
    val big3: Big3Summary = Big3Summary(null, null, null),
    val searchQuery: String = "",
    val showExternalCardio: Boolean = false,
    val isRefreshingPending: Boolean = false
)

@HiltViewModel
class GymViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutRepository: WorkoutRepository,
    private val healthSyncRepository: HealthSyncRepository,
    exerciseDao: ExerciseDao,
    routineDao: RoutineDao,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<MuscleCategoryGroup?>(null)
    private val selectedRoutineDayId = MutableStateFlow<Long?>(null)
    private val refreshingPending = MutableStateFlow(false)

    val uiState: StateFlow<GymUiState> = combine(
        workoutRepository.observeCompletedWorkouts(),
        routineDao.observeRoutinesWithDays(),
        exerciseDao.observeAll(),
        exerciseDao.observeUsageCounts(),
        combine(prefsRepo.preferences, searchQuery, selectedCategory, selectedRoutineDayId, refreshingPending) { p, q, cat, dayId, r ->
            FilterParams(p, q, cat, dayId, r)
        }
    ) { workouts, routines, catalog, usageCounts, params ->
        val zone = ZoneId.systemDefault()
        val nonExcluded = workouts.filter { !it.workout.excludedFromStats }
        val filteredWorkouts = workouts.filter { wf ->
            params.prefs.showExternalCardioInHistory || !wf.workout.excludedFromStats
        }

        val injuredMap = params.prefs.injuredMuscles.mapNotNull { (k, iso) ->
            val grp = runCatching { RecoveryMuscleGroup.valueOf(k) }.getOrNull() ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: LocalDate.now()
            grp to date
        }.toMap()
        val recSnapshot = MuscleRecoveryEngine.computeRecoveryState(
            completedWorkouts = nonExcluded,
            now = Instant.now(),
            injuredMuscles = injuredMap,
            formula = params.prefs.preferredFormula,
            bodyweightKg = params.prefs.defaultBodyWeightKg
        )

        val historyItems = buildHistoryItems(filteredWorkouts, zone)
        val workoutsByDate = nonExcluded.groupBy { it.workout.startedAt.atZone(zone).toLocalDate() }
        val weeklyKpis = computeWeeklyKpis(nonExcluded, zone)

        val lastDoneByDayId = mutableMapOf<Long, LocalDate>()
        for (wf in nonExcluded) {
            val dId = wf.workout.routineDayId ?: continue
            val d = wf.workout.startedAt.atZone(zone).toLocalDate()
            val cur = lastDoneByDayId[dId]
            if (cur == null || d.isAfter(cur)) {
                lastDoneByDayId[dId] = d
            }
        }

        val allDays = routines.flatMap { it.days }
        val resolvedSelectedDayId = params.selectedDayId?.takeIf { id -> allDays.any { it.day.id == id } }
            ?: allDays.firstOrNull { it.day.name.equals("full body", ignoreCase = true) }?.day?.id
            ?: allDays.firstOrNull()?.day?.id

        val bestE1rmByExId = mutableMapOf<Long, Double>()
        for (wf in nonExcluded) {
            for (we in wf.exercises) {
                if (we.exercise.type != ExerciseType.WEIGHTED) continue
                val best = we.sets
                    .filter { (it.weightKg ?: 0.0) > 0.0 && (it.reps ?: 0) in 1..12 }
                    .mapNotNull { OneRmEngine.calculate(it.weightKg!!, it.reps!!)?.e1rmKg }
                    .maxOrNull() ?: continue
                val cur = bestE1rmByExId[we.exercise.id] ?: 0.0
                if (best > cur) bestE1rmByExId[we.exercise.id] = best
            }
        }

        val countMap = usageCounts.associate { it.exerciseId to it.cnt }
        val q = params.query.trim().lowercase()
        val cat = params.selectedCategory
        val exerciseItems = catalog
            .filter { ex ->
                val matchesSearch = q.isBlank() || ex.nameEn.lowercase().contains(q) || ex.nameUk.lowercase().contains(q)
                val matchesCat = cat == null || cat == MuscleCategoryGroup.ALL || ex.primaryMuscle in cat.muscles
                matchesSearch && matchesCat
            }
            .map { ex ->
                ExerciseCatalogItem(
                    exercise = ex,
                    usageCount = countMap[ex.id] ?: 0,
                    bestE1rmKg = bestE1rmByExId[ex.id]
                )
            }
            .sortedWith(compareByDescending<ExerciseCatalogItem> { it.usageCount }.thenBy { it.exercise.nameEn })

        val benchId = catalog.firstOrNull { it.jefitEid == 2 }?.id
        val squatId = catalog.firstOrNull { it.jefitEid == 12 }?.id
        val deadliftId = catalog.firstOrNull { it.jefitEid == 93 }?.id

        GymUiState(
            historyItems = historyItems,
            workoutsByDate = workoutsByDate,
            weeklyKpis = weeklyKpis,
            routines = routines,
            lastDoneByDayId = lastDoneByDayId,
            dayReadinessByDayId = allDays.associate { it.day.id to MuscleRecoveryEngine.readinessForRoutineDay(it, recSnapshot) },
            recovery = recSnapshot,
            selectedRoutineDayId = resolvedSelectedDayId,
            exercises = exerciseItems,
            selectedCategory = cat,
            big3 = Big3Summary(
                benchKg = benchId?.let { bestE1rmByExId[it] },
                squatKg = squatId?.let { bestE1rmByExId[it] },
                deadliftKg = deadliftId?.let { bestE1rmByExId[it] }
            ),
            searchQuery = params.query,
            showExternalCardio = params.prefs.showExternalCardioInHistory,
            isRefreshingPending = params.refreshing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GymUiState())

    fun setSearchQuery(q: String) {
        searchQuery.value = q
    }

    fun selectCategory(cat: MuscleCategoryGroup?) {
        selectedCategory.value = cat
    }

    fun selectRoutineDay(dayId: Long) {
        selectedRoutineDayId.value = dayId
    }

    fun setShowExternalCardio(show: Boolean) {
        viewModelScope.launch { prefsRepo.setShowExternalCardio(show) }
    }

    fun refreshPendingHc() {
        viewModelScope.launch {
            refreshingPending.value = true
            runCatching { healthSyncRepository.syncPendingWorkouts() }
            refreshingPending.value = false
        }
    }

    fun startRoutineDay(routineDayId: Long, dayName: String, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val wId = workoutRepository.startWorkout(routineDayId, tookPreWorkout = false, customTitle = dayName)
            WorkoutTimerService.start(context, System.currentTimeMillis(), dayName)
            onStarted(wId)
        }
    }

    private fun computeWeeklyKpis(workouts: List<WorkoutFull>, zone: ZoneId): WeeklyKpiSummary {
        val today = LocalDate.now(zone)
        val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val prevWeekStart = thisWeekStart.minusWeeks(1)

        var thisVol = 0.0
        var prevVol = 0.0
        var thisSec = 0
        var prevSec = 0
        val weeksWithWorkouts = mutableSetOf<LocalDate>()

        for (wf in workouts) {
            val d = wf.workout.startedAt.atZone(zone).toLocalDate()
            weeksWithWorkouts += d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val vol = wf.metrics?.volumeKg ?: 0.0
            val dur = wf.metrics?.durationSec ?: 0
            when {
                !d.isBefore(thisWeekStart) -> {
                    thisVol += vol
                    thisSec += dur
                }
                !d.isBefore(prevWeekStart) && d.isBefore(thisWeekStart) -> {
                    prevVol += vol
                    prevSec += dur
                }
            }
        }

        val volDelta = if (prevVol > 0.0) (((thisVol - prevVol) / prevVol) * 100.0).roundToInt() else null
        val timeDelta = if (prevSec > 0) (((thisSec - prevSec).toDouble() / prevSec.toDouble()) * 100.0).roundToInt() else null

        var streak = 0
        var probe = if (thisWeekStart in weeksWithWorkouts) thisWeekStart else prevWeekStart
        while (probe in weeksWithWorkouts) {
            streak++
            probe = probe.minusWeeks(1)
        }

        return WeeklyKpiSummary(
            volumeKg = thisVol,
            volumeDeltaPercent = volDelta,
            workoutMinutes = (thisSec / 60),
            timeDeltaPercent = timeDelta,
            totalSessions = workouts.size,
            currentWeekStreak = streak
        )
    }

    private fun buildHistoryItems(sortedDesc: List<WorkoutFull>, zone: ZoneId): List<HistoryListItem> {
        val result = mutableListOf<HistoryListItem>()
        var currentMonth: YearMonth? = null
        var prevDate: LocalDate? = null

        for (wf in sortedDesc) {
            val date = wf.workout.startedAt.atZone(zone).toLocalDate()
            if (prevDate != null) {
                val gapDays = ChronoUnit.DAYS.between(date, prevDate)
                if (gapDays > 60L) {
                    result += HistoryListItem.BreakSeparator(
                        months = (gapDays / 30L).toInt().coerceAtLeast(2),
                        days = gapDays
                    )
                }
            }
            val ym = YearMonth.from(date)
            if (ym != currentMonth) {
                currentMonth = ym
                result += HistoryListItem.MonthHeader(ym)
            }
            result += HistoryListItem.WorkoutRow(wf)
            prevDate = date
        }
        return result
    }

    private data class FilterParams(
        val prefs: UserPreferences,
        val query: String,
        val selectedCategory: MuscleCategoryGroup?,
        val selectedDayId: Long?,
        val refreshing: Boolean
    )
}
