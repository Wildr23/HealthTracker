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
    fun slotGrouping_placesTriggersIntoFourExpectedSlots() {
        val dummy = item(1, SupplementCategory.OTHER)
        assertEquals(
            StackTimeSlot.MORNING_MEAL,
            SupplementScheduleEngine.slotFor(
                listOf(SupplementScheduleEntity(supplementId = 1, triggerType = SupplementTrigger.WITH_MEAL, timeOfDay = LocalTime.of(9, 0))),
                dummy
            )
        )
        assertEquals(
            StackTimeSlot.DAY_EVENING,
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
            StackTimeSlot.BEDTIME,
            SupplementScheduleEngine.slotFor(
                listOf(SupplementScheduleEntity(supplementId = 1, triggerType = SupplementTrigger.BEDTIME)),
                dummy
            )
        )
    }

    @Test
    fun streakAndAdherence_andDaysRemainingWorkAccurately() {
        val zone = ZoneId.of("Europe/Kyiv")
        val today = LocalDate.of(2026, 5, 10)
        val it = item(1, SupplementCategory.VITAMIN, servings = 20.0)
        // 5 days/week schedule (Mon-Fri mask = 0b0011111 = 31)
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
}
