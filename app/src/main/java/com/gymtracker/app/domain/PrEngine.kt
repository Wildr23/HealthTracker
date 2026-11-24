package com.gymtracker.app.domain

import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.OneRmFormula
import com.gymtracker.app.data.db.PrType
import com.gymtracker.app.data.db.SetType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class PrSetInput(
    val setId: Long,
    val setIndex: Int,
    val weightKg: Double?,
    val reps: Int?,
    val rpe: Float?,
    val setType: SetType,
    val peakHr: Int? = null,
    val durationSec: Int? = null
)

data class ExerciseSessionInput(
    val workoutId: Long,
    val startedAt: Instant,
    val workoutTitle: String?,
    val note: String?,
    val blockAvgHr: Int?,
    val blockMaxHr: Int?,
    val sets: List<PrSetInput>
)

data class SessionPrPoint(
    val workoutId: Long,
    val date: LocalDate,
    val startedAt: Instant,
    val sessionIndex: Int,
    val bestE1rmKg: Double?,
    val maxWeightKg: Double?,
    val maxReps: Int?,
    val totalVolumeKg: Double,
    val workSetCount: Int,
    val bestSetLabel: String,
    val allSetsSummary: String,
    val peakHr: Int?,
    val note: String?,
    val prBadges: Set<PrType>
)

data class TrainingBreak(
    val afterSessionIndex: Int,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val days: Long
)

data class PrLeaderRecords(
    val bestE1rmKg: Double? = null,
    val bestE1rmDate: LocalDate? = null,
    val bestE1rmSetLabel: String? = null,
    val maxWeightKg: Double? = null,
    val maxWeightDate: LocalDate? = null,
    val maxWeightReps: Int? = null,
    val maxRepsAtTopWeightLabel: String? = null,
    val maxRepsOverall: Int? = null,
    val maxRepsOverallDate: LocalDate? = null,
    val maxSetVolumeKg: Double? = null,
    val maxSetVolumeDate: LocalDate? = null,
    val maxSetVolumeLabel: String? = null,
    val maxSessionVolumeKg: Double? = null,
    val maxSessionVolumeDate: LocalDate? = null
)

data class EraComparison(
    val era2019To2021PeakE1rm: Double?,
    val era2023PeakE1rm: Double?,
    val current2026PlusE1rm: Double?,
    val recoveryVsBestPriorPercent: Int?
)

data class ExercisePrAnalysis(
    val points: List<SessionPrPoint>,
    val trainingBreaks: List<TrainingBreak>,
    val records: PrLeaderRecords,
    val currentE1rmKg: Double?,
    val eraComparison: EraComparison
)

data class LivePrHit(
    val type: PrType,
    val newValue: Double,
    val previousBest: Double?
)

object PrEngine {

    private const val BREAK_THRESHOLD_DAYS = 60L

    /**
     * Chronologically analyzes an exercise's historical sessions to compute e1RM curves,
     * 5-category PR badges, training breaks (>60 days), and era comeback comparisons.
     */
    fun analyzeExerciseHistory(
        exerciseType: ExerciseType,
        sessions: List<ExerciseSessionInput>,
        formula: OneRmFormula = OneRmFormula.EPLEY,
        maxRepsFilter: Int = 12,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ExercisePrAnalysis {
        if (sessions.isEmpty()) {
            return ExercisePrAnalysis(
                points = emptyList(),
                trainingBreaks = emptyList(),
                records = PrLeaderRecords(),
                currentE1rmKg = null,
                eraComparison = EraComparison(null, null, null, null)
            )
        }

        val sorted = sessions.sortedBy { it.startedAt }
        var runningBestE1rm = 0.0
        var runningMaxWeight = 0.0
        val runningBestRepsByWeight = mutableMapOf<Int, Int>() // weight * 4 (0.25 kg bucket) -> reps
        var runningMaxSetVolume = 0.0
        var runningMaxSessionVolume = 0.0
        var runningMaxRepsOverall = 0

        var bestE1rmDate: LocalDate? = null
        var bestE1rmLabel: String? = null
        var maxWeightDate: LocalDate? = null
        var maxWeightReps: Int? = null
        var maxRepsOverallDate: LocalDate? = null
        var maxSetVolDate: LocalDate? = null
        var maxSetVolLabel: String? = null
        var maxSessionVolDate: LocalDate? = null

        val points = mutableListOf<SessionPrPoint>()
        val breaks = mutableListOf<TrainingBreak>()

        sorted.forEachIndexed { idx, session ->
            val date = session.startedAt.atZone(zoneId).toLocalDate()
            if (idx > 0) {
                val prevDate = points.last().date
                val gapDays = ChronoUnit.DAYS.between(prevDate, date)
                if (gapDays > BREAK_THRESHOLD_DAYS) {
                    breaks += TrainingBreak(
                        afterSessionIndex = idx - 1,
                        fromDate = prevDate,
                        toDate = date,
                        days = gapDays
                    )
                }
            }

            val workSets = session.sets.filter { it.setType != SetType.WARMUP }
            var sessionBestE1rm: Double? = null
            var sessionBestE1rmLabel: String? = null
            var sessionMaxWeight: Double? = null
            var sessionMaxReps: Int? = null
            var sessionVolume = 0.0
            var sessionMaxSetVol = 0.0
            var sessionMaxSetVolLabel: String? = null
            val badges = mutableSetOf<PrType>()

            val setsText = session.sets.joinToString(", ") { s ->
                val w = s.weightKg
                val r = s.reps
                when {
                    exerciseType == ExerciseType.BODYWEIGHT && r != null -> "${r}"
                    w != null && r != null -> "${formatKg(w)}×$r"
                    s.durationSec != null -> "${s.durationSec}s"
                    else -> "-"
                }
            }

            for (s in workSets) {
                val reps = s.reps ?: continue
                if (reps <= 0) continue
                sessionMaxReps = maxOf(sessionMaxReps ?: 0, reps)

                if (exerciseType == ExerciseType.BODYWEIGHT) {
                    if (reps > runningMaxRepsOverall) {
                        runningMaxRepsOverall = reps
                        maxRepsOverallDate = date
                        badges += PrType.MAX_REPS_AT_WEIGHT
                    }
                    continue
                }

                if (exerciseType != ExerciseType.WEIGHTED) continue
                val w = s.weightKg ?: continue
                if (w <= 0.0) continue

                val setVol = w * reps
                sessionVolume += setVol
                if (setVol > sessionMaxSetVol) {
                    sessionMaxSetVol = setVol
                    sessionMaxSetVolLabel = "${formatKg(w)} kg × $reps"
                }

                if (w > (sessionMaxWeight ?: 0.0)) {
                    sessionMaxWeight = w
                }

                if (w > runningMaxWeight) {
                    runningMaxWeight = w
                    maxWeightDate = date
                    maxWeightReps = reps
                    badges += PrType.MAX_WEIGHT
                } else if (w == runningMaxWeight && reps > (maxWeightReps ?: 0)) {
                    maxWeightReps = reps
                }

                val weightKey = (w * 4.0).roundToInt()
                val prevRepsAtWeight = runningBestRepsByWeight[weightKey] ?: 0
                if (reps > prevRepsAtWeight) {
                    if (prevRepsAtWeight > 0) {
                        badges += PrType.MAX_REPS_AT_WEIGHT
                    }
                    runningBestRepsByWeight[weightKey] = reps
                }

                if (reps <= maxRepsFilter) {
                    val e1rm = OneRmEngine.calculate(w, reps, formula, s.rpe)?.e1rmKg
                    if (e1rm != null && e1rm > (sessionBestE1rm ?: 0.0)) {
                        sessionBestE1rm = e1rm
                        sessionBestE1rmLabel = "${formatKg(w)} kg × $reps"
                    }
                }
            }

            if (sessionBestE1rm != null && sessionBestE1rm > runningBestE1rm) {
                runningBestE1rm = sessionBestE1rm
                bestE1rmDate = date
                bestE1rmLabel = sessionBestE1rmLabel
                badges += PrType.BEST_E1RM
            }
            if (sessionMaxSetVol > runningMaxSetVolume) {
                runningMaxSetVolume = sessionMaxSetVol
                maxSetVolDate = date
                maxSetVolLabel = sessionMaxSetVolLabel
                badges += PrType.MAX_SET_VOLUME
            }
            if (sessionVolume > runningMaxSessionVolume) {
                runningMaxSessionVolume = sessionVolume
                maxSessionVolDate = date
                badges += PrType.MAX_SESSION_VOLUME
            }

            val peakHr = session.blockMaxHr ?: session.sets.mapNotNull { it.peakHr }.maxOrNull()
            val bestLabel = when {
                sessionBestE1rmLabel != null -> "$sessionBestE1rmLabel → ${formatKg(sessionBestE1rm ?: 0.0)} kg"
                sessionMaxWeight != null && sessionMaxReps != null -> "${formatKg(sessionMaxWeight)} kg × $sessionMaxReps"
                sessionMaxReps != null -> "$sessionMaxReps reps"
                else -> "-"
            }

            points += SessionPrPoint(
                workoutId = session.workoutId,
                date = date,
                startedAt = session.startedAt,
                sessionIndex = idx,
                bestE1rmKg = sessionBestE1rm,
                maxWeightKg = sessionMaxWeight,
                maxReps = sessionMaxReps,
                totalVolumeKg = OneRmEngine.roundToTwoDecimals(sessionVolume),
                workSetCount = workSets.size,
                bestSetLabel = bestLabel,
                allSetsSummary = setsText,
                peakHr = peakHr,
                note = session.note,
                prBadges = badges
            )
        }

        val era1921 = points.filter { it.date.year in 2019..2021 }.mapNotNull { it.bestE1rmKg }.maxOrNull()
        val era2023 = points.filter { it.date.year in 2022..2024 }.mapNotNull { it.bestE1rmKg }.maxOrNull()
        val era2026 = points.filter { it.date.year >= 2025 }.mapNotNull { it.bestE1rmKg }.maxOrNull()
        val currentE1rm = points.lastOrNull { it.bestE1rmKg != null }?.bestE1rmKg
        val bestPrior = listOfNotNull(era1921, era2023).maxOrNull()
        val recoveryPct = if (bestPrior != null && bestPrior > 0.0 && currentE1rm != null) {
            ((currentE1rm / bestPrior) * 100.0).roundToInt()
        } else null

        val topRepTierLabel = if (runningMaxWeight > 0.0) {
            val repsAtTop = runningBestRepsByWeight[(runningMaxWeight * 4.0).roundToInt()] ?: maxWeightReps
            repsAtTop?.let { "${formatKg(runningMaxWeight)} kg × $it" }
        } else null

        return ExercisePrAnalysis(
            points = points,
            trainingBreaks = breaks,
            records = PrLeaderRecords(
                bestE1rmKg = runningBestE1rm.takeIf { it > 0.0 },
                bestE1rmDate = bestE1rmDate,
                bestE1rmSetLabel = bestE1rmLabel,
                maxWeightKg = runningMaxWeight.takeIf { it > 0.0 },
                maxWeightDate = maxWeightDate,
                maxWeightReps = maxWeightReps,
                maxRepsAtTopWeightLabel = topRepTierLabel,
                maxRepsOverall = runningMaxRepsOverall.takeIf { it > 0 },
                maxRepsOverallDate = maxRepsOverallDate,
                maxSetVolumeKg = runningMaxSetVolume.takeIf { it > 0.0 },
                maxSetVolumeDate = maxSetVolDate,
                maxSetVolumeLabel = maxSetVolLabel,
                maxSessionVolumeKg = runningMaxSessionVolume.takeIf { it > 0.0 },
                maxSessionVolumeDate = maxSessionVolDate
            ),
            currentE1rmKg = currentE1rm,
            eraComparison = EraComparison(
                era2019To2021PeakE1rm = era1921,
                era2023PeakE1rm = era2023,
                current2026PlusE1rm = era2026,
                recoveryVsBestPriorPercent = recoveryPct
            )
        )
    }

    /**
     * Checks whether a newly completed set in an active workout breaks any all-time PRs.
     */
    fun detectLiveSetPrs(
        exerciseType: ExerciseType,
        weightKg: Double?,
        reps: Int?,
        rpe: Float?,
        setType: SetType,
        priorSessions: List<ExerciseSessionInput>,
        currentSessionWeightVolume: Double,
        formula: OneRmFormula = OneRmFormula.EPLEY
    ): List<LivePrHit> {
        if (setType == SetType.WARMUP || reps == null || reps <= 0) return emptyList()
        val history = analyzeExerciseHistory(exerciseType, priorSessions, formula)
        val recs = history.records
        val hits = mutableListOf<LivePrHit>()

        if (exerciseType == ExerciseType.BODYWEIGHT) {
            val prev = recs.maxRepsOverall ?: 0
            if (reps > prev) {
                hits += LivePrHit(PrType.MAX_REPS_AT_WEIGHT, reps.toDouble(), prev.takeIf { it > 0 }?.toDouble())
            }
            return hits
        }

        if (exerciseType != ExerciseType.WEIGHTED || weightKg == null || weightKg <= 0.0) return emptyList()

        if (reps <= 12) {
            val newE1rm = OneRmEngine.calculate(weightKg, reps, formula, rpe)?.e1rmKg
            val prevE1rm = recs.bestE1rmKg ?: 0.0
            if (newE1rm != null && newE1rm > prevE1rm) {
                hits += LivePrHit(PrType.BEST_E1RM, newE1rm, recs.bestE1rmKg)
            }
        }

        if (weightKg > (recs.maxWeightKg ?: 0.0)) {
            hits += LivePrHit(PrType.MAX_WEIGHT, weightKg, recs.maxWeightKg)
        }

        val setVol = weightKg * reps
        if (setVol > (recs.maxSetVolumeKg ?: 0.0)) {
            hits += LivePrHit(PrType.MAX_SET_VOLUME, setVol, recs.maxSetVolumeKg)
        }

        if (currentSessionWeightVolume > (recs.maxSessionVolumeKg ?: 0.0)) {
            hits += LivePrHit(PrType.MAX_SESSION_VOLUME, currentSessionWeightVolume, recs.maxSessionVolumeKg)
        }

        return hits
    }

    fun formatKg(kg: Double): String {
        return if (kg % 1.0 == 0.0) kg.toInt().toString()
        else String.format(java.util.Locale.US, "%.2f", kg).trimEnd('0').trimEnd('.')
    }
}
