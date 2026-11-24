package com.gymtracker.app.ui.workout

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.db.ExerciseDao
import com.gymtracker.app.data.db.ExerciseEntity
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.GymTrackerDatabase
import com.gymtracker.app.data.db.RoutineDao
import com.gymtracker.app.data.db.SetType
import com.gymtracker.app.data.db.WorkoutFull
import com.gymtracker.app.data.db.WorkoutSetEntity
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.WorkoutRepository
import com.gymtracker.app.domain.LivePrHit
import com.gymtracker.app.domain.OneRmEngine
import com.gymtracker.app.domain.PlateBreakdown
import com.gymtracker.app.domain.PlateCalculator
import com.gymtracker.app.domain.RepMaxRow
import com.gymtracker.app.work.WorkoutTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class ActiveWorkoutUiState(
    val workoutFull: WorkoutFull? = null,
    val allExercises: List<ExerciseEntity> = emptyList(),
    val exerciseBestE1rmMap: Map<Long, Double> = emptyMap(),
    val exerciseRestSecMap: Map<Long, Int> = emptyMap(),
    val elapsedSec: Int = 0,
    val restRemainingSec: Int = 0,
    val keepScreenAwake: Boolean = true,
    val defaultRestSec: Int = 90,
    val livePrHits: List<LivePrHit> = emptyList(),
    val plateBreakdown: PlateBreakdown? = null,
    val repMaxRows: List<RepMaxRow> = emptyList()
)

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val workoutRepository: WorkoutRepository,
    private val database: GymTrackerDatabase,
    exerciseDao: ExerciseDao,
    routineDao: RoutineDao,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    val workoutId: Long = savedStateHandle.get<Long>("workoutId") ?: -1L

    private val elapsedSec = MutableStateFlow(0)
    private val restEndMillis = MutableStateFlow(0L)
    private val restRemainingSec = MutableStateFlow(0)
    private val livePrHits = MutableStateFlow<List<LivePrHit>>(emptyList())
    private val plateBreakdown = MutableStateFlow<PlateBreakdown?>(null)
    private val repMaxRows = MutableStateFlow<List<RepMaxRow>>(emptyList())
    private val historicalE1rmByExId = MutableStateFlow<Map<Long, Double>>(emptyMap())
    private val customRestByExId = MutableStateFlow<Map<Long, Int>>(emptyMap())

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        workoutRepository.observeWorkout(workoutId),
        exerciseDao.observeAll(),
        prefsRepo.preferences,
        combine(elapsedSec, restRemainingSec, livePrHits, plateBreakdown, repMaxRows) { e, r, p, pb, rm ->
            ExtraActiveState(e, r, p, pb, rm)
        },
        combine(historicalE1rmByExId, customRestByExId) { e1, restMap -> e1 to restMap }
    ) { wf, allEx, prefs, extra, maps ->
        val mergedE1rm = maps.first.toMutableMap()
        wf?.exercises?.forEach { we ->
            if (we.exercise.type == ExerciseType.WEIGHTED) {
                val liveBest = we.sets
                    .filter { it.setType != SetType.WARMUP && (it.weightKg ?: 0.0) > 0.0 && (it.reps ?: 0) in 1..15 }
                    .mapNotNull { OneRmEngine.calculate(it.weightKg!!, it.reps!!, prefs.preferredFormula, it.rpe)?.e1rmKg }
                    .maxOrNull()
                if (liveBest != null && liveBest > (mergedE1rm[we.exercise.id] ?: 0.0)) {
                    mergedE1rm[we.exercise.id] = liveBest
                }
            }
        }

        ActiveWorkoutUiState(
            workoutFull = wf,
            allExercises = allEx,
            exerciseBestE1rmMap = mergedE1rm,
            exerciseRestSecMap = maps.second,
            elapsedSec = extra.elapsed,
            restRemainingSec = extra.rest,
            keepScreenAwake = prefs.keepScreenAwake,
            defaultRestSec = prefs.defaultRestSec,
            livePrHits = extra.prHits,
            plateBreakdown = extra.plates,
            repMaxRows = extra.repMax
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActiveWorkoutUiState())

    init {
        loadHistoricalE1rmsAndRoutineRest()
        viewModelScope.launch {
            while (true) {
                val wf = uiState.value.workoutFull
                if (wf != null) {
                    elapsedSec.value = Duration.between(wf.workout.startedAt, Instant.now())
                        .seconds.toInt().coerceAtLeast(0)
                }
                val rem = ((restEndMillis.value - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                restRemainingSec.value = rem
                delay(1000L)
            }
        }
    }

    private fun loadHistoricalE1rmsAndRoutineRest() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val workout = database.workoutDao().getWorkoutById(workoutId)
                val routineRest = workout?.routineDayId?.let { dayId ->
                    database.routineDao().getDayWithExercises(dayId)?.exercises
                        ?.mapNotNull { de -> de.dayExercise.restSeconds?.takeIf { it > 0 }?.let { de.exercise.id to it } }
                        ?.toMap()
                }.orEmpty()
                if (routineRest.isNotEmpty()) {
                    customRestByExId.value = routineRest + customRestByExId.value
                }

                val allWorkouts = database.workoutDao().getAllWorkoutsOnce()
                    .filter { !it.isInProgress && !it.excludedFromStats && it.deletedAt == null }
                    .map { it.id }.toSet()
                val weMap = database.workoutDao().getAllWorkoutExercisesOnce()
                    .filter { it.workoutId in allWorkouts && it.deletedAt == null }
                    .associateBy { it.id }
                val sets = database.workoutDao().getAllWorkoutSetsOnce()
                    .filter { it.deletedAt == null && it.setType != SetType.WARMUP && (it.weightKg ?: 0.0) > 0.0 && (it.reps ?: 0) in 1..12 }
                val bestMap = mutableMapOf<Long, Double>()
                for (s in sets) {
                    val exId = weMap[s.workoutExerciseId]?.exerciseId ?: continue
                    val e1 = OneRmEngine.calculate(s.weightKg!!, s.reps!!)?.e1rmKg ?: continue
                    if (e1 > (bestMap[exId] ?: 0.0)) bestMap[exId] = e1
                }
                historicalE1rmByExId.value = bestMap
            }
        }
    }

    fun setExerciseRestSeconds(exerciseId: Long, sec: Int) {
        customRestByExId.value = customRestByExId.value + (exerciseId to sec)
    }

    fun togglePreWorkout(took: Boolean) {
        viewModelScope.launch { workoutRepository.togglePreWorkout(workoutId, took) }
    }

    fun setKeepScreenAwake(keep: Boolean) {
        viewModelScope.launch { prefsRepo.setKeepScreenAwake(keep) }
    }

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch { workoutRepository.addExerciseToWorkout(workoutId, exerciseId) }
    }

    fun removeExercise(workoutExerciseId: Long) {
        viewModelScope.launch { workoutRepository.removeExerciseFromWorkout(workoutExerciseId) }
    }

    fun moveExercise(workoutExerciseId: Long, delta: Int) {
        viewModelScope.launch { workoutRepository.moveExercise(workoutId, workoutExerciseId, delta) }
    }

    fun updateNote(workoutExerciseId: Long, note: String?) {
        viewModelScope.launch { workoutRepository.updateExerciseNote(workoutExerciseId, note) }
    }

    fun addSet(workoutExerciseId: Long) {
        viewModelScope.launch { workoutRepository.addSet(workoutExerciseId) }
    }

    fun removeLastSet(sets: List<WorkoutSetEntity>) {
        val last = sets.maxByOrNull { it.setIndex } ?: return
        viewModelScope.launch { workoutRepository.deleteSet(last.id) }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch { workoutRepository.deleteSet(setId) }
    }

    fun updateSetDraft(set: WorkoutSetEntity, weightKg: Double?, reps: Int?, setType: SetType, rpe: Float?) {
        viewModelScope.launch {
            workoutRepository.updateSetValues(set, weightKg, reps, setType, rpe)
        }
    }

    fun updateCardioSet(set: WorkoutSetEntity, durationSec: Int?, distanceKm: Double?, calories: Double?) {
        viewModelScope.launch {
            val updated = set.copy(
                durationSec = durationSec,
                distanceKm = distanceKm,
                calories = calories,
                updatedAt = Instant.now()
            )
            database.workoutDao().updateWorkoutSet(updated)
        }
    }

    fun toggleSetDone(
        workoutExerciseId: Long,
        exerciseId: Long,
        set: WorkoutSetEntity,
        weightKg: Double?,
        reps: Int?,
        rpe: Float?,
        setType: SetType
    ) {
        viewModelScope.launch {
            val wasDone = set.completedAt != null
            val hits = workoutRepository.toggleSetCompleted(
                workoutId = workoutId,
                workoutExerciseId = workoutExerciseId,
                exerciseId = exerciseId,
                set = set,
                weightKg = weightKg,
                reps = reps,
                rpe = rpe,
                setType = setType
            )
            if (!wasDone) {
                val rest = uiState.value.exerciseRestSecMap[exerciseId] ?: uiState.value.defaultRestSec
                val endMs = System.currentTimeMillis() + rest * 1000L
                restEndMillis.value = endMs
                restRemainingSec.value = rest
                WorkoutTimerService.updateRest(context, endMs)
            }
            if (hits.isNotEmpty()) {
                livePrHits.value = hits
            }
        }
    }

    fun dismissLivePrBanner() {
        livePrHits.value = emptyList()
    }

    fun adjustRest(deltaSec: Int) {
        val currentEnd = maxOf(restEndMillis.value, System.currentTimeMillis())
        val newEnd = (currentEnd + deltaSec * 1000L).coerceAtLeast(System.currentTimeMillis())
        restEndMillis.value = newEnd
        restRemainingSec.value = ((newEnd - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
        WorkoutTimerService.updateRest(context, newEnd)
    }

    fun skipRest() {
        restEndMillis.value = 0L
        restRemainingSec.value = 0
        WorkoutTimerService.updateRest(context, 0L)
    }

    fun showPlateCalculator(targetKg: Double) {
        plateBreakdown.value = PlateCalculator.calculate(targetKg)
    }

    fun dismissPlateCalculator() {
        plateBreakdown.value = null
    }

    fun showRepMaxTable(weightKg: Double?, reps: Int?, fallbackE1rm: Double?) {
        val e1rm = if (weightKg != null && weightKg > 0.0 && reps != null && reps in 1..15) {
            OneRmEngine.calculate(weightKg, reps)?.e1rmKg ?: weightKg
        } else {
            fallbackE1rm ?: 100.0
        }
        repMaxRows.value = OneRmEngine.percentageTable(e1rm)
    }

    fun dismissRepMaxTable() {
        repMaxRows.value = emptyList()
    }

    fun finishWorkout(onFinished: (Long) -> Unit) {
        viewModelScope.launch {
            WorkoutTimerService.stop(context)
            val id = workoutRepository.finishWorkout(workoutId)
            onFinished(id)
        }
    }

    fun discardWorkout(onDiscarded: () -> Unit) {
        viewModelScope.launch {
            WorkoutTimerService.stop(context)
            workoutRepository.discardWorkout(workoutId)
            onDiscarded()
        }
    }

    private data class ExtraActiveState(
        val elapsed: Int,
        val rest: Int,
        val prHits: List<LivePrHit>,
        val plates: PlateBreakdown?,
        val repMax: List<RepMaxRow>
    )
}
