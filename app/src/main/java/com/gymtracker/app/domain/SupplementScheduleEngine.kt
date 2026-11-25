package com.gymtracker.app.domain

import com.gymtracker.app.data.db.SupplementItemEntity
import com.gymtracker.app.data.db.SupplementLogEntity
import com.gymtracker.app.data.db.SupplementScheduleEntity
import com.gymtracker.app.data.db.SupplementTrigger
import com.gymtracker.app.data.db.SupplementWithSchedules
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

enum class StackTimeSlot {
    MORNING_MEAL,
    DAY_EVENING,
    WORKOUT,
    BEDTIME
}

sealed interface CyclePhase {
    data object None : CyclePhase
    data class On(val dayIndex: Int, val totalDays: Int) : CyclePhase
    data class Off(val dayIndex: Int, val totalDays: Int) : CyclePhase
}

object SupplementScheduleEngine {

    fun computeCycleStatus(item: SupplementItemEntity, today: LocalDate): CyclePhase {
        val onDays = item.cycleDaysOn ?: return CyclePhase.None
        val offDays = item.cycleDaysOff ?: return CyclePhase.None
        if (onDays <= 0 || offDays <= 0) return CyclePhase.None

        val start = item.cycleStartDate ?: today
        val period = onDays + offDays
        val daysSince = ChronoUnit.DAYS.between(start, today)
        val mod = ((daysSince % period) + period) % period
        return if (mod < onDays) {
            CyclePhase.On(dayIndex = (mod + 1).toInt(), totalDays = onDays)
        } else {
            CyclePhase.Off(dayIndex = (mod - onDays + 1).toInt(), totalDays = offDays)
        }
    }

    fun isCycleActiveOnDate(item: SupplementItemEntity, date: LocalDate): Boolean =
        computeCycleStatus(item, date) !is CyclePhase.Off

    fun isScheduledForDate(schedules: List<SupplementScheduleEntity>, date: LocalDate): Boolean {
        val active = schedules.filter { it.enabled && it.deletedAt == null }
        if (active.isEmpty()) return true
        val dayBit = 1 shl (date.dayOfWeek.value - 1)
        return active.any { (it.daysOfWeekMask and dayBit) != 0 }
    }

    fun slotFor(schedules: List<SupplementScheduleEntity>, item: SupplementItemEntity): StackTimeSlot {
        val active = schedules.filter { it.enabled && it.deletedAt == null }
        val primary = active.firstOrNull() ?: return StackTimeSlot.DAY_EVENING
        return when (primary.triggerType) {
            SupplementTrigger.BEDTIME -> StackTimeSlot.BEDTIME
            SupplementTrigger.BEFORE_WORKOUT,
            SupplementTrigger.AFTER_WORKOUT -> StackTimeSlot.WORKOUT
            SupplementTrigger.WITH_MEAL -> {
                val t = primary.timeOfDay
                if (t == null || t.isBefore(LocalTime.NOON)) StackTimeSlot.MORNING_MEAL else StackTimeSlot.DAY_EVENING
            }
            SupplementTrigger.FIXED_TIME -> {
                val t = primary.timeOfDay
                when {
                    t != null && !t.isBefore(LocalTime.of(22, 0)) -> StackTimeSlot.BEDTIME
                    t == null || t.isBefore(LocalTime.NOON) -> StackTimeSlot.MORNING_MEAL
                    else -> StackTimeSlot.DAY_EVENING
                }
            }
        }
    }

    fun estimateDaysRemaining(
        item: SupplementItemEntity,
        schedules: List<SupplementScheduleEntity>
    ): Int? {
        val rem = item.servingsRemaining ?: return null
        if (rem <= 0.0) return 0
        val active = schedules.filter { it.enabled && it.deletedAt == null }
        val daysPerWeek = if (active.isEmpty()) {
            7
        } else {
            var count = 0
            for (bit in 0 until 7) {
                val mask = 1 shl bit
                if (active.any { (it.daysOfWeekMask and mask) != 0 }) count++
            }
            count.coerceAtLeast(1)
        }
        val avgPerDay = daysPerWeek / 7.0
        return (rem / avgPerDay).roundToInt().coerceAtLeast(0)
    }

    fun computeStreakAndAdherence(
        items: List<SupplementWithSchedules>,
        logs: List<SupplementLogEntity>,
        today: LocalDate,
        zoneId: ZoneId,
        days: Int = 14
    ): Pair<Int, Int> {
        if (items.isEmpty()) return 0 to 0
        val logsByDate = logs
            .filter { it.deletedAt == null }
            .groupBy { Instant.ofEpochSecond(it.takenAt.epochSecond).atZone(zoneId).toLocalDate() }

        var expectedTotal = 0
        var takenTotal = 0
        val dayCompleted = mutableMapOf<LocalDate, Boolean>()

        for (offset in 0 until days) {
            val date = today.minusDays(offset.toLong())
            val activeItems = items.filter { sw ->
                isCycleActiveOnDate(sw.item, date) && isScheduledForDate(sw.schedules, date)
            }
            if (activeItems.isEmpty()) continue
            val takenIdsOnDate = logsByDate[date]?.map { it.supplementId }?.toSet().orEmpty()
            val hitCount = activeItems.count { it.item.id in takenIdsOnDate }
            expectedTotal += activeItems.size
            takenTotal += hitCount
            dayCompleted[date] = hitCount == activeItems.size
        }

        val adherencePct = if (expectedTotal == 0) 0 else ((takenTotal.toDouble() / expectedTotal) * 100.0).roundToInt()

        var streak = 0
        var cursor = if (dayCompleted[today] == true) today else today.minusDays(1)
        while (dayCompleted[cursor] == true) {
            streak++
            cursor = cursor.minusDays(1)
        }

        return streak to adherencePct
    }
}
