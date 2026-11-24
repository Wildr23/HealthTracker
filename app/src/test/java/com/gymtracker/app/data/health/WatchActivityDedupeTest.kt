package com.gymtracker.app.data.health

import com.gymtracker.app.data.db.DataSource
import com.gymtracker.app.data.db.WorkoutEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class WatchActivityDedupeTest {

    private val baseTime = Instant.parse("2026-05-01T10:00:00Z")

    private fun gymWorkout(
        id: Long,
        startOffsetMin: Long,
        durationMin: Long = 60,
        hcSessionId: String? = null,
        excludedFromStats: Boolean = false,
        isInProgress: Boolean = false,
        deletedAt: Instant? = null
    ) = WorkoutEntity(
        id = id,
        startedAt = baseTime.plus(startOffsetMin, ChronoUnit.MINUTES),
        endedAt = baseTime.plus(startOffsetMin + durationMin, ChronoUnit.MINUTES),
        title = "Gym $id",
        source = DataSource.APP,
        excludedFromStats = excludedFromStats,
        isInProgress = isInProgress,
        hcSessionId = hcSessionId,
        deletedAt = deletedAt
    )

    private fun watchSession(
        id: String,
        type: String,
        startOffsetMin: Long,
        durationMin: Long
    ) = HcWatchActivity(
        id = id,
        startTime = baseTime.plus(startOffsetMin, ChronoUnit.MINUTES),
        endTime = baseTime.plus(startOffsetMin + durationMin, ChronoUnit.MINUTES),
        durationSec = durationMin * 60,
        title = type,
        exerciseTypeName = type,
        originPackage = "com.heytap.health.international",
        distanceKm = if (type == "WALKING" || type == "RUNNING") 3.5 else null,
        activeKcal = 250.0,
        totalKcal = 300.0,
        avgHr = 128,
        maxHr = 152,
        steps = 4200
    )

    @Test
    fun excludesLinkedHcSessionId_andOverlappingStrengthSessions() {
        val gyms = listOf(
            gymWorkout(id = 1, startOffsetMin = 0, durationMin = 60, hcSessionId = "hc-linked-walk"),
            gymWorkout(id = 2, startOffsetMin = 240, durationMin = 60, hcSessionId = null),
            // Ignored gym records must NOT suppress watch sessions
            gymWorkout(id = 3, startOffsetMin = 500, durationMin = 60, excludedFromStats = true),
            gymWorkout(id = 4, startOffsetMin = 700, durationMin = 60, isInProgress = true),
            gymWorkout(id = 5, startOffsetMin = 900, durationMin = 60, deletedAt = baseTime)
        )

        val sessions = listOf(
            // Matching hcSessionId -> excluded even if WALKING
            watchSession("hc-linked-walk", "WALKING", startOffsetMin = 0, durationMin = 20),
            // STRENGTH_TRAINING overlapping Gym 2 >= 50% (40 of 50 min overlap = 80%) -> excluded
            watchSession("hc-overlap-strength", "STRENGTH_TRAINING", startOffsetMin = 250, durationMin = 50),
            // WORKOUT overlapping Gym 2 only 20% (10 of 50 min) -> kept
            watchSession("hc-minor-overlap", "WORKOUT", startOffsetMin = 290, durationMin = 50),
            // STRENGTH_TRAINING completely outside gym windows -> kept
            watchSession("hc-outside-strength", "STRENGTH_TRAINING", startOffsetMin = 120, durationMin = 40),
            // STRENGTH_TRAINING overlapping ignored gyms (3, 4, 5) -> kept
            watchSession("hc-ignored-excl", "WEIGHTLIFTING", startOffsetMin = 505, durationMin = 40),
            watchSession("hc-ignored-prog", "CALISTHENICS", startOffsetMin = 705, durationMin = 40),
            watchSession("hc-ignored-del", "STRENGTH_TRAINING", startOffsetMin = 905, durationMin = 40),
            // Cardio/lifestyle activities overlapping Gym 2 -> ALWAYS kept
            watchSession("hc-walk-overlap", "WALKING", startOffsetMin = 240, durationMin = 45),
            watchSession("hc-run-same-day", "RUNNING", startOffsetMin = 245, durationMin = 30),
            watchSession("hc-bike-same-day", "BIKING", startOffsetMin = 260, durationMin = 25)
        )

        val filtered = WatchActivityDeduplicator.filterNonDuplicateSessions(sessions, gyms)
        val keptIds = filtered.map { it.id }

        assertEquals(
            listOf(
                "hc-minor-overlap",
                "hc-outside-strength",
                "hc-ignored-excl",
                "hc-ignored-prog",
                "hc-ignored-del",
                "hc-walk-overlap",
                "hc-run-same-day",
                "hc-bike-same-day"
            ),
            keptIds
        )
    }
}
