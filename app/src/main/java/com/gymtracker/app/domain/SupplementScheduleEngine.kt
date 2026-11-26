package com.gymtracker.app.domain

import com.gymtracker.app.data.db.SupplementCategory
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
import kotlin.math.abs
import kotlin.math.roundToInt

enum class StackTimeSlot {
    MORNING,
    AFTERNOON,
    WORKOUT,
    EVENING,
    BEDTIME,
    ANYTIME
}

sealed interface CyclePhase {
    data object None : CyclePhase
    data class On(val dayIndex: Int, val totalDays: Int) : CyclePhase
    data class Off(val dayIndex: Int, val totalDays: Int) : CyclePhase
}

data class DoseMatchResult(
    val schedule: SupplementScheduleEntity?,
    val slot: StackTimeSlot,
    val matchedLog: SupplementLogEntity?
)

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

    fun isScheduleDueOnDate(schedule: SupplementScheduleEntity, date: LocalDate): Boolean {
        if (!schedule.enabled || schedule.deletedAt != null) return false
        val dayBit = 1 shl (date.dayOfWeek.value - 1)
        return schedule.daysOfWeekMask == 0 || (schedule.daysOfWeekMask and dayBit) != 0
    }

    fun isScheduledForDate(schedules: List<SupplementScheduleEntity>, date: LocalDate): Boolean {
        val active = schedules.filter { it.enabled && it.deletedAt == null }
        if (active.isEmpty()) return true
        return active.any { isScheduleDueOnDate(it, date) }
    }

    fun activeSchedulesForDate(
        schedules: List<SupplementScheduleEntity>,
        date: LocalDate
    ): List<SupplementScheduleEntity> {
        return schedules
            .filter { isScheduleDueOnDate(it, date) }
            .sortedBy { scheduleSortKey(it) }
    }

    private fun scheduleSortKey(schedule: SupplementScheduleEntity): Int {
        val slotOrder = when (slotForSchedule(schedule)) {
            StackTimeSlot.MORNING -> 0
            StackTimeSlot.AFTERNOON -> 1
            StackTimeSlot.WORKOUT -> 2
            StackTimeSlot.EVENING -> 3
            StackTimeSlot.BEDTIME -> 4
            StackTimeSlot.ANYTIME -> 5
        }
        val min = when (schedule.triggerType) {
            SupplementTrigger.BEFORE_WORKOUT -> 1500
            SupplementTrigger.AFTER_WORKOUT -> 1560
            SupplementTrigger.BEDTIME -> schedule.timeOfDay?.let { it.hour * 60 + it.minute } ?: 1400
            SupplementTrigger.WITH_MEAL -> schedule.timeOfDay?.let { it.hour * 60 + it.minute } ?: (9 * 60)
            SupplementTrigger.FIXED_TIME -> schedule.timeOfDay?.let { it.hour * 60 + it.minute } ?: (14 * 60)
        }
        return slotOrder * 2000 + min
    }

    fun slotForSchedule(schedule: SupplementScheduleEntity): StackTimeSlot {
        return when (schedule.triggerType) {
            SupplementTrigger.BEDTIME -> StackTimeSlot.BEDTIME
            SupplementTrigger.BEFORE_WORKOUT,
            SupplementTrigger.AFTER_WORKOUT -> StackTimeSlot.WORKOUT
            SupplementTrigger.WITH_MEAL -> {
                val t = schedule.timeOfDay
                when {
                    t == null || t.isBefore(LocalTime.NOON) -> StackTimeSlot.MORNING
                    t.isBefore(LocalTime.of(17, 0)) -> StackTimeSlot.AFTERNOON
                    t.isBefore(LocalTime.of(22, 0)) -> StackTimeSlot.EVENING
                    else -> StackTimeSlot.BEDTIME
                }
            }
            SupplementTrigger.FIXED_TIME -> {
                val t = schedule.timeOfDay
                when {
                    t == null -> StackTimeSlot.AFTERNOON
                    t.isBefore(LocalTime.NOON) -> StackTimeSlot.MORNING
                    t.isBefore(LocalTime.of(17, 0)) -> StackTimeSlot.AFTERNOON
                    t.isBefore(LocalTime.of(22, 0)) -> StackTimeSlot.EVENING
                    else -> StackTimeSlot.BEDTIME
                }
            }
        }
    }

    fun slotFor(schedules: List<SupplementScheduleEntity>, item: SupplementItemEntity): StackTimeSlot {
        val active = schedules.filter { it.enabled && it.deletedAt == null }
        val primary = active.firstOrNull() ?: return StackTimeSlot.ANYTIME
        return slotForSchedule(primary)
    }

    fun targetMinuteOfDay(schedule: SupplementScheduleEntity, bedtimeMinutesOfDay: Int): Int? {
        schedule.timeOfDay?.let { return it.hour * 60 + it.minute }
        return when (schedule.triggerType) {
            SupplementTrigger.BEDTIME -> ((bedtimeMinutesOfDay - 45) + 1440) % 1440
            SupplementTrigger.WITH_MEAL -> 9 * 60
            SupplementTrigger.FIXED_TIME -> 14 * 60
            SupplementTrigger.BEFORE_WORKOUT,
            SupplementTrigger.AFTER_WORKOUT -> null
        }
    }

    fun matchTodayDoses(
        sw: SupplementWithSchedules,
        todayLogs: List<SupplementLogEntity>,
        logScheduleMap: Map<Long, Long>,
        today: LocalDate,
        bedtimeMinutesOfDay: Int,
        zoneId: ZoneId
    ): List<DoseMatchResult> {
        val activeAll = sw.schedules.filter { it.enabled && it.deletedAt == null }
        val itemLogs = todayLogs
            .filter { it.deletedAt == null && it.supplementId == sw.item.id }
            .sortedBy { it.takenAt }

        if (activeAll.isEmpty()) {
            return listOf(
                DoseMatchResult(
                    schedule = null,
                    slot = StackTimeSlot.ANYTIME,
                    matchedLog = itemLogs.lastOrNull()
                )
            )
        }

        val dueSchedules = activeSchedulesForDate(sw.schedules, today)
        if (dueSchedules.isEmpty()) return emptyList()

        val matchedByScheduleId = mutableMapOf<Long, SupplementLogEntity>()
        val usedLogIds = mutableSetOf<Long>()

        // Pass 1: explicit logId -> scheduleId mapping
        for (log in itemLogs) {
            val mappedSchedId = logScheduleMap[log.id] ?: continue
            if (dueSchedules.any { it.id == mappedSchedId } && mappedSchedId !in matchedByScheduleId) {
                matchedByScheduleId[mappedSchedId] = log
                usedLogIds += log.id
            }
        }

        // Pass 2: workout-linked logs -> BEFORE_WORKOUT / AFTER_WORKOUT
        for (log in itemLogs) {
            if (log.id in usedLogIds || log.linkedWorkoutId == null) continue
            val preferredTrigger = if (sw.item.category == SupplementCategory.PRE_WORKOUT) {
                SupplementTrigger.BEFORE_WORKOUT
            } else {
                SupplementTrigger.AFTER_WORKOUT
            }
            val candidate = dueSchedules.firstOrNull {
                it.id !in matchedByScheduleId && it.triggerType == preferredTrigger
            } ?: dueSchedules.firstOrNull {
                it.id !in matchedByScheduleId &&
                    (it.triggerType == SupplementTrigger.BEFORE_WORKOUT || it.triggerType == SupplementTrigger.AFTER_WORKOUT)
            }
            if (candidate != null) {
                matchedByScheduleId[candidate.id] = log
                usedLogIds += log.id
            }
        }

        // Pass 3: match remaining logs to closest timed schedule within 360 min, else first unmatched chronologically
        for (log in itemLogs) {
            if (log.id in usedLogIds) continue
            val unmatched = dueSchedules.filter { it.id !in matchedByScheduleId }
            if (unmatched.isEmpty()) break

            val localT = log.takenAt.atZone(zoneId).toLocalTime()
            val logMin = localT.hour * 60 + localT.minute

            val timedBest = unmatched.mapNotNull { sched ->
                val targetMin = targetMinuteOfDay(sched, bedtimeMinutesOfDay) ?: return@mapNotNull null
                val rawDiff = abs(logMin - targetMin)
                val diff = minOf(rawDiff, 1440 - rawDiff)
                if (diff <= 360) sched to diff else null
            }.minByOrNull { it.second }?.first

            val chosen = timedBest ?: unmatched.first()
            matchedByScheduleId[chosen.id] = log
            usedLogIds += log.id
        }

        return dueSchedules.map { sched ->
            DoseMatchResult(
                schedule = sched,
                slot = slotForSchedule(sched),
                matchedLog = matchedByScheduleId[sched.id]
            )
        }
    }

    fun estimateDaysRemaining(
        item: SupplementItemEntity,
        schedules: List<SupplementScheduleEntity>
    ): Int? {
        val rem = item.servingsRemaining ?: return null
        if (rem <= 0.0) return 0
        val active = schedules.filter { it.enabled && it.deletedAt == null }
        val dosesPerWeek = if (active.isEmpty()) {
            7
        } else {
            var total = 0
            for (s in active) {
                var schedDays = 0
                for (bit in 0 until 7) {
                    val mask = 1 shl bit
                    if (s.daysOfWeekMask == 0 || (s.daysOfWeekMask and mask) != 0) schedDays++
                }
                total += schedDays.coerceAtLeast(1)
            }
            total.coerceAtLeast(1)
        }
        val avgPerDay = dosesPerWeek / 7.0
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

            val logsOnDateByItem = logsByDate[date]?.groupBy { it.supplementId }.orEmpty()
            var expectedDay = 0
            var hitDay = 0
            for (sw in activeItems) {
                val activeScheds = sw.schedules.filter { it.enabled && it.deletedAt == null }
                val expectedForItem = if (activeScheds.isEmpty()) {
                    1
                } else {
                    activeSchedulesForDate(sw.schedules, date).size.coerceAtLeast(1)
                }
                val takenForItem = (logsOnDateByItem[sw.item.id]?.size ?: 0).coerceAtMost(expectedForItem)
                expectedDay += expectedForItem
                hitDay += takenForItem
            }

            expectedTotal += expectedDay
            takenTotal += hitDay
            dayCompleted[date] = expectedDay > 0 && hitDay >= expectedDay
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
