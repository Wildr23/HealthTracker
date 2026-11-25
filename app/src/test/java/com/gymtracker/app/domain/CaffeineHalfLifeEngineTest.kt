package com.gymtracker.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CaffeineHalfLifeEngineTest {

    private val zone = ZoneId.of("Europe/Kyiv")

    @Test
    fun singleDose_decaysByHalfEvery5Hours_andProjectsAtMidnight() {
        val doseTime = LocalDateTime.of(2026, 5, 1, 14, 0).atZone(zone).toInstant()
        val now = LocalDateTime.of(2026, 5, 1, 19, 0).atZone(zone).toInstant()

        val analysis = CaffeineHalfLifeEngine.analyze(
            doses = listOf(CaffeineDose(takenAt = doseTime, mg = 200, label = "Coffee")),
            now = now,
            bedtimeMinutesOfDay = 0, // 24:00 (00:00 next day = 10h after 14:00)
            halfLifeHours = 5.0,
            sleepThresholdMg = 50,
            zoneId = zone
        )

        // After 5h (14:00 -> 19:00): 200 * 0.5 = 100 mg
        assertEquals(100.0, analysis.activeMgNow, 0.5)
        // After 10h (14:00 -> 00:00): 200 * 0.25 = 50 mg -> SAFE (<= 50)
        assertEquals(LocalTime.MIDNIGHT, analysis.bedtimeLocal)
        assertEquals(50.0, analysis.projectedMgAtBedtime, 0.5)
        assertEquals(CaffeineSleepStatus.SAFE, analysis.status)
        assertEquals(200, analysis.totalTodayMg)
        assertTrue(analysis.curvePoints.size >= 10)
    }

    @Test
    fun lateDose_superposesWithMorningDose_flagsHighRiskAndFindsSafeTime() {
        val morning = LocalDateTime.of(2026, 5, 1, 9, 0).atZone(zone).toInstant()
        val lateEnergy = LocalDateTime.of(2026, 5, 1, 19, 0).atZone(zone).toInstant()
        val now = LocalDateTime.of(2026, 5, 1, 19, 30).atZone(zone).toInstant()

        val analysis = CaffeineHalfLifeEngine.analyze(
            doses = listOf(
                CaffeineDose(morning, 100, "Coffee"),
                CaffeineDose(lateEnergy, 200, "Energy")
            ),
            now = now,
            bedtimeMinutesOfDay = 0, // 00:00 (5h after 19:00)
            halfLifeHours = 5.0,
            sleepThresholdMg = 50,
            zoneId = zone
        )

        // At 00:00: 100*(0.5^3 = 12.5) + 200*(0.5^1 = 100) = 112.5 mg -> HIGH (> 100)
        assertTrue(analysis.projectedMgAtBedtime > 105.0)
        assertEquals(CaffeineSleepStatus.HIGH, analysis.status)
        assertNotNull(analysis.safeSleepInstant)
        assertTrue(analysis.safeSleepInstant!!.isAfter(analysis.bedtimeInstant))
    }

    @Test
    fun zeroCaffeine_reportsSafeAndNullSafeTime() {
        val now = LocalDateTime.of(2026, 5, 1, 18, 0).atZone(zone).toInstant()
        val analysis = CaffeineHalfLifeEngine.analyze(
            doses = emptyList(),
            now = now,
            bedtimeMinutesOfDay = 0,
            zoneId = zone
        )
        assertEquals(0.0, analysis.activeMgNow, 0.001)
        assertEquals(0.0, analysis.projectedMgAtBedtime, 0.001)
        assertEquals(CaffeineSleepStatus.SAFE, analysis.status)
        assertNull(analysis.safeSleepInstant)
    }
}
