package com.gymtracker.app.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.pow

data class CaffeineDose(
    val takenAt: Instant,
    val mg: Int,
    val label: String
)

enum class CaffeineSleepStatus {
    SAFE,
    MODERATE,
    HIGH
}

data class CaffeineCurvePoint(
    val instant: Instant,
    val mg: Double
)

data class CaffeineAnalysis(
    val activeMgNow: Double,
    val totalTodayMg: Int,
    val projectedMgAtBedtime: Double,
    val bedtimeInstant: Instant,
    val bedtimeLocal: LocalTime,
    val sleepThresholdMg: Int,
    val status: CaffeineSleepStatus,
    val safeSleepInstant: Instant?,
    val curvePoints: List<CaffeineCurvePoint>
)

object CaffeineHalfLifeEngine {

    fun remainingMgAt(
        doses: List<CaffeineDose>,
        targetTime: Instant,
        halfLifeHours: Double = 5.0
    ): Double {
        val hl = halfLifeHours.coerceIn(1.0, 24.0)
        var total = 0.0
        for (dose in doses) {
            if (dose.mg <= 0 || dose.takenAt.isAfter(targetTime)) continue
            val elapsedHours = Duration.between(dose.takenAt, targetTime).toMinutes().toDouble() / 60.0
            if (elapsedHours in 0.0..36.0) {
                total += dose.mg * 0.5.pow(elapsedHours / hl)
            }
        }
        return total
    }

    fun analyze(
        doses: List<CaffeineDose>,
        now: Instant,
        bedtimeMinutesOfDay: Int = 0,
        halfLifeHours: Double = 5.0,
        sleepThresholdMg: Int = 50,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CaffeineAnalysis {
        val normBedtime = ((bedtimeMinutesOfDay % 1440) + 1440) % 1440
        val bedtimeLocal = LocalTime.of(normBedtime / 60, normBedtime % 60)
        val nowLocal = now.atZone(zoneId).toLocalDateTime()
        val todayStart = nowLocal.toLocalDate().atStartOfDay(zoneId).toInstant()

        var bedtimeLdt = LocalDateTime.of(nowLocal.toLocalDate(), bedtimeLocal)
        if (!bedtimeLdt.isAfter(nowLocal)) {
            bedtimeLdt = bedtimeLdt.plusDays(1)
        }
        val bedtimeInstant = bedtimeLdt.atZone(zoneId).toInstant()

        val activeNow = remainingMgAt(doses, now, halfLifeHours)
        val projectedBedtime = remainingMgAt(doses, bedtimeInstant, halfLifeHours)
        val totalToday = doses
            .filter { !it.takenAt.isBefore(todayStart) && !it.takenAt.isAfter(now) }
            .sumOf { it.mg.coerceAtLeast(0) }

        val threshold = sleepThresholdMg.coerceAtLeast(10)
        val status = when {
            projectedBedtime <= threshold -> CaffeineSleepStatus.SAFE
            projectedBedtime <= threshold * 2.0 -> CaffeineSleepStatus.MODERATE
            else -> CaffeineSleepStatus.HIGH
        }

        val safeSleepInstant = if (activeNow <= threshold) {
            null
        } else {
            var cursor = now.plusSeconds(15 * 60L)
            var found: Instant? = null
            val limit = now.plusSeconds(24 * 3600L)
            while (!cursor.isAfter(limit)) {
                if (remainingMgAt(doses, cursor, halfLifeHours) <= threshold) {
                    found = cursor
                    break
                }
                cursor = cursor.plusSeconds(15 * 60L)
            }
            found
        }

        val startCurve = now.minusSeconds(2 * 3600L)
        val endCurve = maxOf(bedtimeInstant.plusSeconds(2 * 3600L), now.plusSeconds(10 * 3600L))
        val points = mutableListOf<CaffeineCurvePoint>()
        var t = startCurve
        while (!t.isAfter(endCurve)) {
            points += CaffeineCurvePoint(
                instant = t,
                mg = remainingMgAt(doses, t, halfLifeHours)
            )
            t = t.plusSeconds(30 * 60L)
        }

        return CaffeineAnalysis(
            activeMgNow = activeNow,
            totalTodayMg = totalToday,
            projectedMgAtBedtime = projectedBedtime,
            bedtimeInstant = bedtimeInstant,
            bedtimeLocal = bedtimeLocal,
            sleepThresholdMg = threshold,
            status = status,
            safeSleepInstant = safeSleepInstant,
            curvePoints = points
        )
    }
}
