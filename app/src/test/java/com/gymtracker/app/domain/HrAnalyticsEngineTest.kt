package com.gymtracker.app.domain

import com.gymtracker.app.data.db.HcStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HrAnalyticsEngineTest {

    @Test
    fun `matchSession picks full overlap with score 1_0`() {
        val ws = Instant.parse("2026-11-17T18:42:00Z")
        val we = Instant.parse("2026-11-17T19:25:00Z")
        val ohealth = SessionCandidate("oh-1", ws, we, "Комплексне тренування", "WORKOUT")
        val outcome = HrAnalyticsEngine.matchSession(ws, we, listOf(ohealth))
        assertEquals(HcStatus.MATCHED, outcome.status)
        assertEquals(1.0, outcome.overlapScore, 0.001)
        assertEquals("oh-1", outcome.matchedCandidate?.id)
    }

    @Test
    fun `linear interpolation works within 60s and returns null across larger gaps`() {
        val t0 = Instant.parse("2026-11-17T19:00:00Z")
        val samples = listOf(
            HrPoint(t0, 100),
            HrPoint(t0.plusSeconds(20), 120),
            HrPoint(t0.plusSeconds(180), 90)
        )
        assertEquals(110, HrAnalyticsEngine.interpolateHr(samples, t0.plusSeconds(10)))
        assertNull(HrAnalyticsEngine.interpolateHr(samples, t0.plusSeconds(100)))
    }

    @Test
    fun `per set hr captures post set surge and 60s recovery`() {
        val t0 = Instant.parse("2026-11-17T19:00:00Z")
        // Set 1 completes at t0 + 30s; HR peaks at t0 + 40s (135 bpm), recovers to 105 bpm by t0 + 100s
        val samples = listOf(
            HrPoint(t0, 95),
            HrPoint(t0.plusSeconds(20), 115),
            HrPoint(t0.plusSeconds(40), 135),
            HrPoint(t0.plusSeconds(60), 125),
            HrPoint(t0.plusSeconds(80), 115),
            HrPoint(t0.plusSeconds(100), 105),
            HrPoint(t0.plusSeconds(140), 100)
        )
        val sets = listOf(
            SetHrInput(1, reps = 10, startedAt = null, completedAt = t0.plusSeconds(30)),
            SetHrInput(2, reps = 10, startedAt = null, completedAt = t0.plusSeconds(180))
        )
        val computed = HrAnalyticsEngine.computeSetHrMetrics(sets, samples, t0.plusSeconds(240), secPerRep = 3)
        assertEquals(135, computed[0].peakHr)
        assertTrue(computed[0].sampleCount >= 2)
        // Peak at t+40s = 135; at t+100s = 105 -> recovery = 30
        assertEquals(30, computed[0].hrRecovery60)
        // Rest from t+30s to set2 start (180 - 30 = 150s) -> 120s
        assertEquals(120, computed[0].restSec)
    }

    @Test
    fun `zones trimp and tanaka hrmax compute expected values`() {
        val hrMax = HrAnalyticsEngine.tanakaHrMax(LocalDate.of(1993, 2, 20), LocalDate.of(2026, 11, 23))
        assertEquals(185, hrMax)

        val t0 = Instant.parse("2026-11-17T19:00:00Z")
        // 3 minutes at 140 bpm (~75.6% -> Zone 3)
        val samples = (0..8).map { HrPoint(t0.plusSeconds(it * 20L), 140) }
        val zt = HrAnalyticsEngine.computeZonesAndTrimp(samples, hrMax)
        assertEquals(140, zt.avgHr)
        assertTrue(zt.zoneSeconds[2] > 0)
        assertTrue(zt.edwardsTrimp > 0.0)
    }
}
