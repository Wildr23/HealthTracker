package com.gymtracker.app.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.db.GymTrackerDatabase
import com.gymtracker.app.data.db.HrSampleEntity
import com.gymtracker.app.data.db.MetricsDao
import com.gymtracker.app.data.db.RoutineDao
import com.gymtracker.app.data.db.SetMetricsEntity
import com.gymtracker.app.data.db.WorkoutFull
import com.gymtracker.app.data.health.HcSession
import com.gymtracker.app.data.health.HealthSyncRepository
import com.gymtracker.app.data.prefs.UserPreferences
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.WorkoutRepository
import com.gymtracker.app.domain.MuscleRecoveryEngine
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
import javax.inject.Inject

data class SummaryUiState(
    val workoutFull: WorkoutFull? = null,
    val hrSamples: List<HrSampleEntity> = emptyList(),
    val setMetricsById: Map<Long, SetMetricsEntity> = emptyMap(),
    val inferredSupersetsByExId: Map<Long, Int> = emptyMap(),
    val muscleAnalysis: WorkoutMuscleAnalysis? = null,
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
    prefsRepo: UserPreferencesRepository
) : ViewModel() {

    val workoutId: Long = savedStateHandle.get<Long>("workoutId") ?: -1L
    private val syncing = MutableStateFlow(false)
    private val candidates = MutableStateFlow<List<HcSession>?>(null)
    private val inferredSupersets = MutableStateFlow<Map<Long, Int>>(emptyMap())

    val uiState: StateFlow<SummaryUiState> = combine(
        workoutRepository.observeWorkout(workoutId),
        metricsDao.observeHrSamples(workoutId),
        metricsDao.observeSetMetricsForWorkout(workoutId),
        workoutRepository.observeCompletedWorkouts(),
        combine(prefsRepo.preferences, syncing, candidates, inferredSupersets) { p, s, c, ss ->
            ExtraSummary(p, s, c, ss)
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
        SummaryUiState(
            workoutFull = wf,
            hrSamples = hr,
            setMetricsById = setMetrics.associateBy { it.setId },
            inferredSupersetsByExId = extra.inferredSupersets,
            muscleAnalysis = mAnalysis,
            hrMax = extra.prefs.hrMax,
            isSyncingHc = extra.syncing,
            candidateSessions = extra.candidates
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SummaryUiState())

    private data class ExtraSummary(
        val prefs: UserPreferences,
        val syncing: Boolean,
        val candidates: List<HcSession>?,
        val inferredSupersets: Map<Long, Int>
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
}
