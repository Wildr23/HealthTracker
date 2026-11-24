package com.gymtracker.app.domain

import com.gymtracker.app.data.db.HcStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class HrPoint(
    val time: Instant,
    val bpm: Int
)

data class SessionCandidate(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val title: String?,
    val exerciseTypeName: String?
)

data class SessionMatchOutcome(
    val status: HcStatus,
    val matchedCandidate: SessionCandidate?,
    val overlapScore: Double,
    val analysisWindowStart: Instant,
    val analysisWindowEnd: Instant
)

data class SetHrInput(
    val setId: Long,
    val reps: Int?,
    val startedAt: Instant?,
    val completedAt: Instant?
)

data class ComputedSetHr(
    val setId: Long,
    val peakHr: Int?,
    val endHr: Int?,
    val restSec: Int?,
    val restAvgHr: Int?,
    val hrRecovery60: Int?,
    val sampleCount: Int
)

data class ZonesAndTrimp(
    val avgHr: Int?,
    val maxHr: Int?,
    val minHr: Int?,
    val zoneSeconds: IntArray,
    val edwardsTrimp: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZonesAndTrimp) return false
        return avgHr == other.avgHr &&
            maxHr == other.maxHr &&
            minHr == other.minHr &&
            zoneSeconds.contentEquals(other.zoneSeconds) &&
            edwardsTrimp == other.edwardsTrimp
    }

    override fun hashCode(): Int {
        var result = avgHr ?: 0
        result = 31 * result + (maxHr ?: 0)
        result = 31 * result + (minHr ?: 0)
        result = 31 * result + zoneSeconds.contentHashCode()
        result = 31 * result + edwardsTrimp.hashCode()
        return result
    }
}

object HrAnalyticsEngine {

    private const val MAX_INTERPOLATION_GAP_SEC = 60L
    private const val POST_SET_SURGE_SEC = 20L
    private const val PENDING_RETRY_CUTOFF_HOURS = 6L

    /**
     * Tanaka formula: 208 - 0.7 * age.
     */
    fun tanakaHrMax(birthDate: LocalDate, onDate: LocalDate = LocalDate.now()): Int {
        val age = Period.between(birthDate, onDate).years.coerceIn(15, 90)
        return (208.0 - 0.7 * age).roundToInt()
    }

    /**
     * Matches an app workout window [workoutStart, workoutEnd] against candidate Health Connect sessions.
     * Picks the highest overlap / min(wDur, sDur). Score >= 0.5 yields MATCHED.
     */
    fun matchSession(
        workoutStart: Instant,
        workoutEnd: Instant,
        candidates: List<SessionCandidate>,
        rawHrPoints: List<HrPoint> = emptyList(),
        now: Instant = Instant.now()
    ): SessionMatchOutcome {
        val wEnd = if (workoutEnd.isAfter(workoutStart)) workoutEnd else workoutStart.plusSeconds(45 * 60L)
        val wDurSec = Duration.between(workoutStart, wEnd).seconds.coerceAtLeast(60L)

        var bestCandidate: SessionCandidate? = null
        var bestScore = 0.0

        for (c in candidates) {
            if (!c.endTime.isAfter(c.startTime)) continue
            val ovStart = if (workoutStart.isAfter(c.startTime)) workoutStart else c.startTime
            val ovEnd = if (wEnd.isBefore(c.endTime)) wEnd else c.endTime
            if (!ovEnd.isAfter(ovStart)) continue

            val ovSec = Duration.between(ovStart, ovEnd).seconds.toDouble()
            val cDurSec = Duration.between(c.startTime, c.endTime).seconds.coerceAtLeast(60L)
            val denom = min(wDurSec, cDurSec).toDouble()
            val score = ovSec / denom
            if (score > bestScore) {
                bestScore = score
                bestCandidate = c
            }
        }

        if (bestCandidate != null && bestScore >= 0.5) {
            val winStart = minOf(workoutStart, bestCandidate.startTime).minusSeconds(60)
            val winEnd = maxOf(wEnd, bestCandidate.endTime).plusSeconds(180)
            return SessionMatchOutcome(
                status = HcStatus.MATCHED,
                matchedCandidate = bestCandidate,
                overlapScore = bestScore,
                analysisWindowStart = winStart,
                analysisWindowEnd = winEnd
            )
        }

        val defaultStart = workoutStart.minusSeconds(60)
        val defaultEnd = wEnd.plusSeconds(180)
        val inWindow = rawHrPoints.filter { !it.time.isBefore(defaultStart) && !it.time.isAfter(defaultEnd) }
        val workoutMinutes = max(1.0, wDurSec / 60.0)
        val hasDenseEnoughHr = inWindow.size >= workoutMinutes * 0.5

        val hoursSinceEnd = Duration.between(wEnd, now).toHours()
        val status = when {
            hasDenseEnoughHr -> HcStatus.HR_ONLY
            hoursSinceEnd in 0 until PENDING_RETRY_CUTOFF_HOURS -> HcStatus.PENDING
            else -> HcStatus.NO_DATA
        }

        return SessionMatchOutcome(
            status = status,
            matchedCandidate = null,
            overlapScore = bestScore,
            analysisWindowStart = defaultStart,
            analysisWindowEnd = defaultEnd
        )
    }

    /**
     * Linearly interpolates heart rate at [target]. Returns null if outside sample bounds
     * or if either bounding sample is more than 60s away from [target].
     */
    fun interpolateHr(samples: List<HrPoint>, target: Instant): Int? {
        if (samples.isEmpty()) return null
        val sorted = if (isSorted(samples)) samples else samples.sortedBy { it.time }
        if (target.isBefore(sorted.first().time) || target.isAfter(sorted.last().time)) {
            val nearest = sorted.minByOrNull { abs(Duration.between(it.time, target).seconds) }
                ?: return null
            return if (abs(Duration.between(nearest.time, target).seconds) <= MAX_INTERPOLATION_GAP_SEC) {
                nearest.bpm
            } else null
        }

        val exactIdx = sorted.binarySearchBy(target) { it.time }
        if (exactIdx >= 0) return sorted[exactIdx].bpm

        val insertIdx = -(exactIdx + 1)
        val left = sorted[(insertIdx - 1).coerceAtLeast(0)]
        val right = sorted[insertIdx.coerceAtMost(sorted.lastIndex)]
        val leftGap = Duration.between(left.time, target).seconds
        val rightGap = Duration.between(target, right.time).seconds
        if (leftGap > MAX_INTERPOLATION_GAP_SEC || rightGap > MAX_INTERPOLATION_GAP_SEC) {
            return when {
                leftGap <= MAX_INTERPOLATION_GAP_SEC -> left.bpm
                rightGap <= MAX_INTERPOLATION_GAP_SEC -> right.bpm
                else -> null
            }
        }
        val span = Duration.between(left.time, right.time).toMillis().toDouble()
        if (span <= 0.0) return left.bpm
        val ratio = Duration.between(left.time, target).toMillis().toDouble() / span
        return (left.bpm + (right.bpm - left.bpm) * ratio).roundToInt()
    }

    /**
     * Computes per-set peak HR (in [setStart, completedAt + 20s]), end HR, rest duration,
     * rest average HR, and 60-second HR recovery.
     */
    fun computeSetHrMetrics(
        sets: List<SetHrInput>,
        samples: List<HrPoint>,
        workoutEnd: Instant?,
        secPerRep: Int = 3
    ): List<ComputedSetHr> {
        if (sets.isEmpty()) return emptyList()
        val sortedSamples = samples.sortedBy { it.time }

        // Pre-resolve start times so we can measure rest until the next set's start
        val resolvedStarts = sets.map { s ->
            val comp = s.completedAt
            when {
                s.startedAt != null -> s.startedAt
                comp != null -> {
                    val estDur = ((s.reps ?: 10) * secPerRep).coerceIn(15, 90).toLong()
                    comp.minusSeconds(estDur)
                }
                else -> null
            }
        }

        return sets.mapIndexed { idx, s ->
            val completed = s.completedAt
            val start = resolvedStarts[idx]
            if (completed == null || start == null || sortedSamples.isEmpty()) {
                return@mapIndexed ComputedSetHr(
                    setId = s.setId,
                    peakHr = null,
                    endHr = null,
                    restSec = null,
                    restAvgHr = null,
                    hrRecovery60 = null,
                    sampleCount = 0
                )
            }

            val surgeEnd = completed.plusSeconds(POST_SET_SURGE_SEC)
            val setWindowSamples = sortedSamples.filter { !it.time.isBefore(start) && !it.time.isAfter(surgeEnd) }
            val peakPoint = setWindowSamples.maxByOrNull { it.bpm }
            val endHr = interpolateHr(sortedSamples, completed)
            val peakHr = peakPoint?.bpm ?: endHr

            val nextStart = resolvedStarts.drop(idx + 1).firstOrNull { it != null } ?: workoutEnd
            val restSec = if (nextStart != null && nextStart.isAfter(completed)) {
                Duration.between(completed, nextStart).seconds.toInt()
            } else null

            val restSamples = if (nextStart != null && nextStart.isAfter(completed)) {
                sortedSamples.filter { !it.time.isBefore(completed) && !it.time.isAfter(nextStart) }
            } else emptyList()
            val restAvgHr = if (restSamples.isNotEmpty()) {
                restSamples.map { it.bpm }.average().roundToInt()
            } else null

            val hrRecovery60 = if (peakPoint != null && restSec != null && restSec >= 60) {
                val hrAt60 = interpolateHr(sortedSamples, peakPoint.time.plusSeconds(60))
                hrAt60?.let { peakPoint.bpm - it }
            } else null

            ComputedSetHr(
                setId = s.setId,
                peakHr = peakHr,
                endHr = endHr,
                restSec = restSec,
                restAvgHr = restAvgHr,
                hrRecovery60 = hrRecovery60,
                sampleCount = setWindowSamples.size
            )
        }
    }

    /**
     * Computes 5 HR zones (% of HRmax: Z1 50-60, Z2 60-70, Z3 70-80, Z4 80-90, Z5 90-100)
     * and Edwards TRIMP = sum(minutes in zone i * i).
     */
    fun computeZonesAndTrimp(samples: List<HrPoint>, hrMax: Int): ZonesAndTrimp {
        if (samples.isEmpty()) {
            return ZonesAndTrimp(null, null, null, intArrayOf(0, 0, 0, 0, 0), 0.0)
        }
        val sorted = if (isSorted(samples)) samples else samples.sortedBy { it.time }
        val maxHr = sorted.maxOf { it.bpm }
        val minHr = sorted.minOf { it.bpm }
        val avgHr = sorted.map { it.bpm }.average().roundToInt()

        val zones = IntArray(5)
        for (i in sorted.indices) {
            val dtSec = if (i < sorted.lastIndex) {
                Duration.between(sorted[i].time, sorted[i + 1].time).seconds.coerceIn(1L, 45L).toInt()
            } else {
                20
            }
            val pct = (sorted[i].bpm.toDouble() / hrMax.coerceAtLeast(100).toDouble()) * 100.0
            val zoneIdx = when {
                pct >= 90.0 -> 4
                pct >= 80.0 -> 3
                pct >= 70.0 -> 2
                pct >= 60.0 -> 1
                pct >= 50.0 -> 0
                else -> -1
            }
            if (zoneIdx in 0..4) {
                zones[zoneIdx] += dtSec
            }
        }

        val trimp = zones.mapIndexed { idx, sec -> (sec / 60.0) * (idx + 1) }.sum()
        return ZonesAndTrimp(
            avgHr = avgHr,
            maxHr = maxHr,
            minHr = minHr,
            zoneSeconds = zones,
            edwardsTrimp = OneRmEngine.roundToTwoDecimals(trimp)
        )
    }

    private fun isSorted(samples: List<HrPoint>): Boolean {
        for (i in 1 until samples.size) {
            if (samples[i].time.isBefore(samples[i - 1].time)) return false
        }
        return true
    }
}
