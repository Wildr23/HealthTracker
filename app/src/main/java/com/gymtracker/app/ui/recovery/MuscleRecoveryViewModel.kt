package com.gymtracker.app.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.health.HealthDataSource
import com.gymtracker.app.data.health.HealthSyncRepository
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.WorkoutRepository
import com.gymtracker.app.domain.MuscleRecoveryEngine
import com.gymtracker.app.domain.MuscleRecoverySnapshot
import com.gymtracker.app.domain.RecoveryMuscleGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class MuscleRecoveryUiState(
    val snapshot: MuscleRecoverySnapshot = MuscleRecoverySnapshot(
        items = emptyList(),
        muscleFreshnessMap = emptyMap(),
        muscleFatigueMap = emptyMap(),
        injuredMuscleSet = emptySet(),
        overallReadinessPercent = 100,
        sleepImpact = com.gymtracker.app.domain.RecoverySleepImpact.UNKNOWN
    ),
    val sleepMinutesLastNight: Int? = null
)

@HiltViewModel
class MuscleRecoveryViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    private val prefsRepo: UserPreferencesRepository,
    private val healthDataSource: HealthDataSource,
    private val healthSyncRepository: HealthSyncRepository
) : ViewModel() {

    private val sleepMinutesFlow = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<MuscleRecoveryUiState> = combine(
        workoutRepository.observeCompletedWorkouts(),
        prefsRepo.preferences,
        sleepMinutesFlow
    ) { completed, prefs, sleepMin ->
        val injuredMap = prefs.injuredMuscles.mapNotNull { (k, iso) ->
            val grp = runCatching { RecoveryMuscleGroup.valueOf(k) }.getOrNull() ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: LocalDate.now()
            grp to date
        }.toMap()

        val snap = MuscleRecoveryEngine.computeRecoveryState(
            completedWorkouts = completed,
            now = Instant.now(),
            injuredMuscles = injuredMap,
            sleepMinutesLastNight = sleepMin,
            formula = prefs.preferredFormula,
            bodyweightKg = prefs.defaultBodyWeightKg
        )
        MuscleRecoveryUiState(snapshot = snap, sleepMinutesLastNight = sleepMin)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MuscleRecoveryUiState())

    init {
        viewModelScope.launch {
            runCatching {
                val p = healthDataSource.getPermissionState()
                if (p.coreReadGranted) {
                    sleepMinutesFlow.value = healthSyncRepository.getTodaySummary().sleepMinutes?.toInt()
                }
            }
        }
    }

    fun toggleInjured(group: RecoveryMuscleGroup) {
        viewModelScope.launch {
            prefsRepo.toggleInjuredMuscle(group.name, LocalDate.now().toString())
        }
    }
}
