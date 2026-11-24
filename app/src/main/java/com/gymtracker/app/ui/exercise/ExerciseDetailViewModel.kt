package com.gymtracker.app.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.db.BodyWeightDao
import com.gymtracker.app.data.db.ExerciseDao
import com.gymtracker.app.data.db.ExerciseEntity
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.MetricsDao
import com.gymtracker.app.data.db.Muscle
import com.gymtracker.app.data.db.OneRmFormula
import com.gymtracker.app.data.db.PrType
import com.gymtracker.app.data.db.SetType
import com.gymtracker.app.data.db.WorkoutDao
import com.gymtracker.app.data.prefs.UserPreferences
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.domain.ExercisePrAnalysis
import com.gymtracker.app.domain.ExerciseSessionInput
import com.gymtracker.app.domain.MuscleRecoveryEngine
import com.gymtracker.app.domain.OneRmEngine
import com.gymtracker.app.domain.PrEngine
import com.gymtracker.app.domain.PrSetInput
import com.gymtracker.app.domain.RepMaxRow
import com.gymtracker.app.domain.SessionPrPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs

enum class ChartTimeRange {
    D14, M1, M3, M6, Y1, ALL
}

data class ExerciseHistorySetUi(
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val distanceKm: Double?,
    val setType: SetType,
    val restSec: Int?,
    val e1rmKg: Double?,
    val isBestSet: Boolean
)

data class ExerciseHistorySessionUi(
    val workoutId: Long,
    val date: LocalDate,
    val workoutTitle: String?,
    val note: String?,
    val bestE1rmKg: Double?,
    val totalVolumeKg: Double,
    val prBadges: Set<PrType>,
    val sets: List<ExerciseHistorySetUi>
)

data class ExerciseDetailUiState(
    val exercise: ExerciseEntity? = null,
    val formula: OneRmFormula = OneRmFormula.EPLEY,
    val maxReps10Only: Boolean = false,
    val compactBreaks: Boolean = true,
    val timeRange: ChartTimeRange = ChartTimeRange.ALL,
    val fullAnalysis: ExercisePrAnalysis? = null,
    val filteredPoints: List<SessionPrPoint> = emptyList(),
    val selectedPoint: SessionPrPoint? = null,
    val repMaxRows: List<RepMaxRow> = emptyList(),
    val relativeStrengthRatios: List<Pair<String, Double>> = emptyList(),
    val historySessions: List<ExerciseHistorySessionUi> = emptyList(),
    val goalKg: Double? = null,
    val muscleShares: List<Pair<Muscle, Int>> = emptyList(),
    val sessionCount: Int = 0
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exerciseDao: ExerciseDao,
    workoutDao: WorkoutDao,
    metricsDao: MetricsDao,
    bodyWeightDao: BodyWeightDao,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    val exerciseId: Long = savedStateHandle.get<Long>("exerciseId") ?: -1L

    private val maxReps10Only = MutableStateFlow(false)
    private val compactBreaks = MutableStateFlow(true)
    private val timeRange = MutableStateFlow(ChartTimeRange.ALL)
    private val selectedPointId = MutableStateFlow<Long?>(null)

    private val controlsFlow = combine(
        prefsRepo.preferences,
        maxReps10Only,
        compactBreaks,
        timeRange,
        selectedPointId
    ) { prefs, r10, cb, tr, sel ->
        DetailControls(prefs, r10, cb, tr, sel)
    }

    private val metaFlow = combine(bodyWeightDao.observeAll(), controlsFlow) { bws, ctrl ->
        bws to ctrl
    }

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        exerciseDao.observeById(exerciseId),
        workoutDao.observeExerciseHistoryRows(exerciseId),
        workoutDao.observeSetsForExercise(exerciseId),
        metricsDao.observeSetMetricsForExercise(exerciseId),
        metaFlow
    ) { exercise, sessionRows, allSets, setMetrics, (bodyWeights, ctrl) ->
        if (exercise == null) return@combine ExerciseDetailUiState()

        val formula = ctrl.prefs.preferredFormula
        val setsByWe = allSets.groupBy { it.workoutExerciseId }
        val metricsBySetId = setMetrics.associateBy { it.setId }

        val inputs = sessionRows.map { row ->
            ExerciseSessionInput(
                workoutId = row.workoutId,
                startedAt = row.workoutStartedAt,
                workoutTitle = row.workoutTitle,
                note = row.note,
                blockAvgHr = row.blockAvgHr,
                blockMaxHr = row.blockMaxHr,
                sets = setsByWe[row.workoutExerciseId].orEmpty()
                    .sortedBy { it.setIndex }
                    .map { s ->
                        PrSetInput(s.id, s.setIndex, s.weightKg, s.reps, s.rpe, s.setType, metricsBySetId[s.id]?.peakHr, s.durationSec)
                    }
            )
        }

        val maxRepsCap = if (ctrl.maxReps10Only) 10 else 12
        val analysis = PrEngine.analyzeExerciseHistory(
            exerciseType = exercise.type,
            sessions = inputs,
            formula = formula,
            maxRepsFilter = maxRepsCap
        )

        val today = LocalDate.now()
        val filteredPoints = analysis.points.filter { p ->
            when (ctrl.timeRange) {
                ChartTimeRange.D14 -> !p.date.isBefore(today.minusDays(14))
                ChartTimeRange.M1 -> !p.date.isBefore(today.minusMonths(1))
                ChartTimeRange.M3 -> !p.date.isBefore(today.minusMonths(3))
                ChartTimeRange.M6 -> !p.date.isBefore(today.minusMonths(6))
                ChartTimeRange.Y1 -> !p.date.isBefore(today.minusYears(1))
                ChartTimeRange.ALL -> true
            }
        }

        val pointsByWorkoutId = analysis.points.associateBy { it.workoutId }
        val zone = ZoneId.systemDefault()
        val historySessions = sessionRows
            .sortedByDescending { it.workoutStartedAt }
            .mapNotNull { row ->
                val rawSets = setsByWe[row.workoutExerciseId].orEmpty().sortedBy { it.setIndex }
                if (rawSets.isEmpty()) return@mapNotNull null

                val rawE1rms = rawSets.map { s ->
                    val w = s.weightKg ?: 0.0
                    val r = s.reps ?: 0
                    if (exercise.type == ExerciseType.WEIGHTED && w > 0.0 && r in 1..15) {
                        OneRmEngine.calculate(w, r, formula, s.rpe)?.e1rmKg
                    } else null
                }
                val workBestE1rm = rawSets.indices
                    .filter { rawSets[it].setType != SetType.WARMUP }
                    .mapNotNull { rawE1rms[it] }
                    .maxOrNull() ?: rawE1rms.filterNotNull().maxOrNull()

                val pt = pointsByWorkoutId[row.workoutId]
                val uiSets = rawSets.mapIndexed { idx, s ->
                    val e1 = rawE1rms[idx]
                    val isBest = e1 != null && workBestE1rm != null && abs(e1 - workBestE1rm) < 0.01 && s.setType != SetType.WARMUP
                    ExerciseHistorySetUi(
                        setNumber = idx + 1,
                        weightKg = s.weightKg,
                        reps = s.reps,
                        durationSec = s.durationSec,
                        distanceKm = s.distanceKm,
                        setType = s.setType,
                        restSec = metricsBySetId[s.id]?.restSec,
                        e1rmKg = e1,
                        isBestSet = isBest
                    )
                }

                ExerciseHistorySessionUi(
                    workoutId = row.workoutId,
                    date = row.workoutStartedAt.atZone(zone).toLocalDate(),
                    workoutTitle = row.workoutTitle,
                    note = row.note,
                    bestE1rmKg = workBestE1rm,
                    totalVolumeKg = pt?.totalVolumeKg ?: rawSets
                        .filter { it.setType != SetType.WARMUP }
                        .sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) },
                    prBadges = pt?.prBadges.orEmpty(),
                    sets = uiSets
                )
            }

        val validBw = bodyWeights.filter { !it.flaggedOutlier && it.kg > 0.0 }
        val relRatios = if (exercise.type == ExerciseType.WEIGHTED && validBw.isNotEmpty()) {
            filteredPoints.mapNotNull { pt ->
                val e1 = pt.bestE1rmKg ?: return@mapNotNull null
                val nearestBw = validBw.minByOrNull { abs(ChronoUnit.DAYS.between(it.date, pt.date)) }
                    ?: return@mapNotNull null
                pt.date.toString() to OneRmEngine.roundToTwoDecimals(e1 / nearestBw.kg)
            }
        } else emptyList()

        val currentOrBestE1rm = analysis.records.bestE1rmKg ?: analysis.currentE1rmKg ?: 0.0
        val repMaxRows = if (exercise.type == ExerciseType.WEIGHTED && currentOrBestE1rm > 0.0) {
            OneRmEngine.percentageTable(currentOrBestE1rm)
        } else emptyList()

        ExerciseDetailUiState(
            exercise = exercise,
            formula = formula,
            maxReps10Only = ctrl.maxReps10Only,
            compactBreaks = ctrl.compactBreaks,
            timeRange = ctrl.timeRange,
            fullAnalysis = analysis,
            filteredPoints = filteredPoints,
            selectedPoint = filteredPoints.firstOrNull { it.workoutId == ctrl.selectedWorkoutId },
            repMaxRows = repMaxRows,
            relativeStrengthRatios = relRatios,
            historySessions = historySessions,
            goalKg = ctrl.prefs.exerciseGoalsKg[exerciseId],
            muscleShares = MuscleRecoveryEngine.exerciseMuscleSharePercents(exercise),
            sessionCount = historySessions.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExerciseDetailUiState())

    fun setFormula(formula: OneRmFormula) {
        viewModelScope.launch { prefsRepo.setPreferredFormula(formula) }
    }

    fun setMaxReps10Only(only10: Boolean) {
        maxReps10Only.value = only10
    }

    fun setCompactBreaks(compact: Boolean) {
        compactBreaks.value = compact
    }

    fun setTimeRange(range: ChartTimeRange) {
        timeRange.value = range
    }

    fun selectPoint(point: SessionPrPoint?) {
        selectedPointId.value = point?.workoutId
    }

    fun setGoalKg(goalKg: Double?) {
        viewModelScope.launch { prefsRepo.setExerciseGoal(exerciseId, goalKg) }
    }

    private data class DetailControls(
        val prefs: UserPreferences,
        val maxReps10Only: Boolean,
        val compactBreaks: Boolean,
        val timeRange: ChartTimeRange,
        val selectedWorkoutId: Long?
    )
}
