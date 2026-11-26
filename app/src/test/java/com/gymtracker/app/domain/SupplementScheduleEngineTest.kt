package com.gymtracker.app.domain

import com.gymtracker.app.data.db.SupplementCategory
import com.gymtracker.app.data.db.SupplementItemEntity
import com.gymtracker.app.data.db.SupplementLogEntity
import com.gymtracker.app.data.db.SupplementScheduleEntity
import com.gymtracker.app.data.db.SupplementTrigger
import com.gymtracker.app.data.db.SupplementUnit
import com.gymtracker.app.data.db.SupplementWithSchedules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SupplementScheduleEngineTest {

    private fun item(
        id: Long,
        cat: SupplementCategory,
        servings: Double? = null,
        on: Int? = null,
        off: Int? = null,
        start: LocalDate? = null
    ) = SupplementItemEntity(
        id = id,
        nameEn = "Item $id",
        nameUk = "Добавка $id",
        category = cat,
        defaultDose = 1.0,
        unit = SupplementUnit.SCOOP,
        servingsRemaining = servings,
        cycleDaysOn = on,
        cycleDaysOff = off,
        cycleStartDate = start
    )

    @Test
    fun cycleStatus_cyclesThroughOnAndOffPhases() {
        val start = LocalDate.of(2026, 5, 1)
        val cyclic = item(id = 1, cat = SupplementCategory.CREATINE, on = 5, off = 2, start = start)

        val d1 = SupplementScheduleEngine.computeCycleStatus(cyclic, start)
        assertTrue(d1 is CyclePhase.On && d1.dayIndex == 1 && d1.totalDays == 5)
        assertTrue(SupplementScheduleEngine.isCycleActiveOnDate(cyclic, start.plusDays(4)))

        val d6 = SupplementScheduleEngine.computeCycleStatus(cyclic, start.plusDays(5))
        assertTrue(d6 is CyclePhase.Off && d6.dayIndex == 1 && d6.totalDays == 2)
        assertFalse(SupplementScheduleEngine.isCycleActiveOnDate(cyclic, start.plusDays(6)))

        // Repeats back to ON on day 8
        assertTrue(SupplementScheduleEngine.isCycleActiveOnDate(cyclic, start.plusDays(7)))
    }

    @Test
    fun slotGrouping_placesTriggersIntoSixExpectedSlots() {
        val dummy = item(1, SupplementCategory.OTHER)
        assertEquals(
            StackTimeSlot.MORNING,
            SupplementScheduleEngine.slotFor(
                listOf(SupplementScheduleEntity(supplementId = 1, triggerType = SupplementTrigger.WITH_MEAL, timeOfDay = LocalTime.of(9, 0))),
                dummy
            )
        )
        assertEquals(
            StackTimeSlot.AFTERNOON,
            SupplementScheduleEngine.slotFor(
                listOf(SupplementScheduleEntity(supplementId = 1, triggerType = SupplementTrigger.FIXED_TIME, timeOfDay = LocalTime.of(14, 0))),
                dummy
            )
        )
        assertEquals(
            StackTimeSlot.WORKOUT,
            SupplementScheduleEngine.slotFor(
                listOf(SupplementScheduleEntity(supplementId = 1, triggerType = SupplementTrigger.BEFORE_WORKOUT)),
                dummy
            )
        )
        assertEquals(
            StackTimeSlot.EVENING,
            SupplementScheduleEngine.slotFor(
                listOf(SupplementScheduleEntity(supplementId = 1, triggerType = SupplementTrigger.WITH_MEAL, timeOfDay = LocalTime.of(19, 30))),
                dummy
            )
        )
        assertEquals(
            StackTimeSlot.BEDTIME,
            SupplementScheduleEngine.slotFor(
                listOf(SupplementScheduleEntity(supplementId = 1, triggerType = SupplementTrigger.BEDTIME)),
                dummy
            )
        )
        assertEquals(
            StackTimeSlot.ANYTIME,
            SupplementScheduleEngine.slotFor(emptyList(), dummy)
        )
    }

    @Test
    fun streakAndAdherence_andDaysRemainingWorkAccurately() {
        val zone = ZoneId.of("Europe/Kyiv")
        val today = LocalDate.of(2026, 5, 10)
        val it = item(1, SupplementCategory.VITAMIN, servings = 20.0)
        val sched = SupplementScheduleEntity(
            supplementId = 1,
            triggerType = SupplementTrigger.FIXED_TIME,
            timeOfDay = LocalTime.of(9, 0),
            daysOfWeekMask = 0b1111111
        )
        val sw = SupplementWithSchedules(it, listOf(sched))
        assertEquals(20, SupplementScheduleEngine.estimateDaysRemaining(it, listOf(sched)))

        val logs = (0L..2L).map { offset ->
            SupplementLogEntity(
                id = offset + 1,
                supplementId = 1,
                takenAt = today.minusDays(offset).atTime(9, 10).atZone(zone).toInstant(),
                dose = 1.0
            )
        }
        val (streak, adherence) = SupplementScheduleEngine.computeStreakAndAdherence(
            items = listOf(sw),
            logs = logs,
            today = today,
            zoneId = zone,
            days = 6
        )
        assertEquals(3, streak)
        assertEquals(50, adherence) // 3 of 6 days
    }

    @Test
    fun multiDoseSchedules_matchOmegaMorningEveningAndCreatineWorkoutCorrectly() {
        val zone = ZoneId.of("Europe/Kyiv")
        val today = LocalDate.of(2026, 5, 10)

        // Omega-3 twice a day (09:00 + 20:00), 60 servings -> 30 days remaining
        val omega = item(1, SupplementCategory.OMEGA_3, servings = 60.0)
        val omegaMorning = SupplementScheduleEntity(id = 10, supplementId = 1, triggerType = SupplementTrigger.WITH_MEAL, timeOfDay = LocalTime.of(9, 0))
        val omegaEvening = SupplementScheduleEntity(id = 11, supplementId = 1, triggerType = SupplementTrigger.WITH_MEAL, timeOfDay = LocalTime.of(20, 0))
        val omegaSw = SupplementWithSchedules(omega, listOf(omegaMorning, omegaEvening))
        assertEquals(30, SupplementScheduleEngine.estimateDaysRemaining(omega, omegaSw.schedules))

        // Only evening dose logged via explicit mapping -> morning stays pending, evening matched!
        val eveningLog = SupplementLogEntity(
            id = 100,
            supplementId = 1,
            takenAt = today.atTime(9, 5).atZone(zone).toInstant(),
            dose = 1.0
        )
        val omegaMatched = SupplementScheduleEngine.matchTodayDoses(
            sw = omegaSw,
            todayLogs = listOf(eveningLog),
            logScheduleMap = mapOf(100L to 11L),
            today = today,
            bedtimeMinutesOfDay = 0,
            zoneId = zone
        )
        assertEquals(2, omegaMatched.size)
        assertEquals(StackTimeSlot.MORNING, omegaMatched[0].slot)
        assertNull(omegaMatched[0].matchedLog)
        assertEquals(StackTimeSlot.EVENING, omegaMatched[1].slot)
        assertNotNull(omegaMatched[1].matchedLog)

        // Creatine: 09:00 daily + AFTER_WORKOUT
        val creatine = item(2, SupplementCategory.CREATINE, servings = 30.0)
        val crMorning = SupplementScheduleEntity(id = 20, supplementId = 2, triggerType = SupplementTrigger.FIXED_TIME, timeOfDay = LocalTime.of(9, 0))
        val crPost = SupplementScheduleEntity(id = 21, supplementId = 2, triggerType = SupplementTrigger.AFTER_WORKOUT)
        val crSw = SupplementWithSchedules(creatine, listOf(crMorning, crPost))

        // Workout-linked post-workout log matches AFTER_WORKOUT even without map
        val postLog = SupplementLogEntity(
            id = 200,
            supplementId = 2,
            takenAt = today.atTime(18, 30).atZone(zone).toInstant(),
            dose = 5.0,
            linkedWorkoutId = 55L
        )
        val crMatched = SupplementScheduleEngine.matchTodayDoses(
            sw = crSw,
            todayLogs = listOf(postLog),
            logScheduleMap = emptyMap(),
            today = today,
            bedtimeMinutesOfDay = 0,
            zoneId = zone
        )
        assertEquals(2, crMatched.size)
        assertNull(crMatched.first { it.schedule?.id == 20L }.matchedLog)
        assertEquals(200L, crMatched.first { it.schedule?.id == 21L }.matchedLog?.id)

        // 2 expected doses today, 1 logged -> adherence = 50%
        val (streak, adherence) = SupplementScheduleEngine.computeStreakAndAdherence(
            items = listOf(omegaSw),
            logs = listOf(eveningLog),
            today = today,
            zoneId = zone,
            days = 1
        )
        assertEquals(0, streak)
        assertEquals(50, adherence)
    }
}
