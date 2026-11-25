package com.gymtracker.app.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.db.GymTrackerDatabase
import com.gymtracker.app.data.db.HrSampleEntity
import com.gymtracker.app.data.db.MetricsDao
import com.gymtracker.app.data.db.RoutineDao
import com.gymtracker.app.data.db.SetMetricsEntity
import com.gymtracker.app.data.db.SupplementItemEntity
import com.gymtracker.app.data.db.SupplementTrigger
import com.gymtracker.app.data.db.WorkoutFull
import com.gymtracker.app.data.health.HcSession
import com.gymtracker.app.data.health.HealthSyncRepository
import com.gymtracker.app.data.prefs.UserPreferences
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.SupplementRepository
import com.gymtracker.app.data.repo.WorkoutRepository
import com.gymtracker.app.domain.MuscleRecoveryEngine
import com.gymtracker.app.domain.SupplementScheduleEngine
import com.gymtracker.app.domain.WorkoutMuscleAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class SummaryUiState(
    val workoutFull: WorkoutFull? = null,
    val hrSamples: List<HrSampleEntity> = emptyList(),
    val setMetricsById: Map<Long, SetMetricsEntity> = emptyMap(),
    val inferredSupersetsByExId: Map<Long, Int> = emptyMap(),
    val muscleAnalysis: WorkoutMuscleAnalysis? = null,
    val pendingPostWorkoutSupplements: List<SupplementItemEntity> = emptyList(),
    val hrMax: Int = 185,
    val isSyncingHc: Boolean = false,
    val candidateSessions: List<HcSession>? = null
)

@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val routineDao: RoutineDao,
    private val database: GymTrackerDatabase,
    metricsDao: MetricsDao,
    private val healthSyncRepository: HealthSyncRepository,
    private val supplementRepository: SupplementRepository,
    prefsRepo: UserPreferencesRepository
) : ViewModel() {

    val workoutId: Long = savedStateHandle.get<Long>("workoutId") ?: -1L
    private val syncing = MutableStateFlow(false)
    private val candidates = MutableStateFlow<List<HcSession>?>(null)
    private val inferredSupersets = MutableStateFlow<Map<Long, Int>>(emptyMap())

    private val postWorkoutStackFlow = combine(
        supplementRepository.observeVisibleStack(),
        supplementRepository.observeLogsSince(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant())
    ) { stack, logs ->
        stack to logs
    }

    val uiState: StateFlow<SummaryUiState> = combine(
        workoutRepository.observeWorkout(workoutId),
        metricsDao.observeHrSamples(workoutId),
        metricsDao.observeSetMetricsForWorkout(workoutId),
        workoutRepository.observeCompletedWorkouts(),
        combine(prefsRepo.preferences, syncing, candidates, inferredSupersets, postWorkoutStackFlow) { p, s, c, ss, stackPair ->
            ExtraSummary(p, s, c, ss, stackPair.first, stackPair.second)
        }
    ) { wf, hr, setMetrics, completedHistory, extra ->
        val mAnalysis = wf?.let {
            MuscleRecoveryEngine.analyzeWorkoutStimulus(
                workout = it,
                history = completedHistory,
                formula = extra.prefs.preferredFormula,
                bodyweightKg = extra.prefs.defaultBodyWeightKg
            )
        }

        val now = Instant.now()
        val today = LocalDate.now(ZoneId.systemDefault())
        val isRecentWorkout = wf?.workout?.endedAt?.let { ended ->
            Duration.between(ended, now).toHours() in 0..12
        } ?: false
        val takenTodayIds = extra.todayLogs.map { it.supplementId }.toSet()
        val pendingPost = if (isRecentWorkout) {
            extra.visibleStack.filter { sw ->
                sw.item.id !in takenTodayIds &&
                    SupplementScheduleEngine.isCycleActiveOnDate(sw.item, today) &&
                    sw.schedules.any { it.enabled && it.deletedAt == null && it.triggerType == SupplementTrigger.AFTER_WORKOUT }
            }.map { it.item }
        } else emptyList()

        SummaryUiState(
            workoutFull = wf,
            hrSamples = hr,
            setMetricsById = setMetrics.associateBy { it.setId },
            inferredSupersetsByExId = extra.inferredSupersets,
            muscleAnalysis = mAnalysis,
            pendingPostWorkoutSupplements = pendingPost,
            hrMax = extra.prefs.hrMax,
            isSyncingHc = extra.syncing,
            candidateSessions = extra.candidates
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SummaryUiState())

    private data class ExtraSummary(
        val prefs: UserPreferences,
        val syncing: Boolean,
        val candidates: List<HcSession>?,
        val inferredSupersets: Map<Long, Int>,
        val visibleStack: List<com.gymtracker.app.data.db.SupplementWithSchedules>,
        val todayLogs: List<com.gymtracker.app.data.db.SupplementLogEntity>
    )

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val w = database.workoutDao().getWorkoutById(workoutId)
                val dayId = w?.routineDayId ?: return@withContext
                val day = routineDao.getDayWithExercises(dayId) ?: return@withContext
                inferredSupersets.value = day.exercises
                    .mapNotNull { de -> de.dayExercise.supersetGroup?.let { de.exercise.id to it } }
                    .toMap()
            }
        }
    }

    fun refreshWatchSync() {
        viewModelScope.launch {
            syncing.value = true
            runCatching { healthSyncRepository.syncWorkout(workoutId) }
            syncing.value = false
        }
    }

    fun loadCandidateSessions() {
        viewModelScope.launch {
            syncing.value = true
            candidates.value = runCatching { healthSyncRepository.listCandidateSessions(workoutId) }
                .getOrDefault(emptyList())
            syncing.value = false
        }
    }

    fun selectCandidateSession(sessionId: String) {
        candidates.value = null
        viewModelScope.launch {
            syncing.value = true
            runCatching { healthSyncRepository.syncWorkout(workoutId, manualSessionId = sessionId) }
            syncing.value = false
        }
    }

    fun dismissCandidates() {
        candidates.value = null
    }

    fun unlinkWatchSession() {
        viewModelScope.launch {
            syncing.value = true
            runCatching { healthSyncRepository.unlinkHealthSession(workoutId) }
            syncing.value = false
        }
    }

    fun deleteWorkout(onDeleted: () -> Unit) {
        viewModelScope.launch {
            workoutRepository.discardWorkout(workoutId)
            onDeleted()
        }
    }

    fun logPostWorkoutSupplement(itemId: Long) {
        viewModelScope.launch {
            supplementRepository.logIntake(itemId = itemId, linkedWorkoutId = workoutId)
        }
    }
}
