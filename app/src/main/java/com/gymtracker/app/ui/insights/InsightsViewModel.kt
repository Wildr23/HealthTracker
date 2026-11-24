package com.gymtracker.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.Muscle
import com.gymtracker.app.data.db.SetType
import com.gymtracker.app.data.repo.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class MuscleSetStat(
    val muscle: Muscle,
    val workSets: Int
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    workoutRepository: WorkoutRepository
) : ViewModel() {

    val weeklyMuscleSets: StateFlow<List<MuscleSetStat>> = workoutRepository.observeCompletedWorkouts()
        .map { workouts ->
            val cutoff = Instant.now().minus(7, ChronoUnit.DAYS)
            val recent = workouts.filter { !it.workout.excludedFromStats && it.workout.startedAt.isAfter(cutoff) }
            val counts = mutableMapOf<Muscle, Int>()
            for (wf in recent) {
                for (we in wf.exercises) {
                    if (we.exercise.type == ExerciseType.CARDIO) continue
                    val workSets = we.sets.count { it.setType != SetType.WARMUP }
                    val m = we.exercise.primaryMuscle
                    counts[m] = (counts[m] ?: 0) + workSets
                }
            }
            counts.entries
                .sortedByDescending { it.value }
                .map { MuscleSetStat(it.key, it.value) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
