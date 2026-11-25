package com.gymtracker.app.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.gymtracker.app.data.db.SupplementDao
import com.gymtracker.app.data.db.SupplementTrigger
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StackReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supplementDao: SupplementDao,
    private val prefsRepo: UserPreferencesRepository
) {
    suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@withContext
        val prefs = prefsRepo.getOnce()

        // Cancel all existing reminder slots first
        for (slotIdx in 0 until MAX_SLOTS) {
            val cancelIntent = Intent(context, StackReminderReceiver::class.java).apply {
                action = StackReminderReceiver.ACTION_FIRE_REMINDER
            }
            val pi = PendingIntent.getBroadcast(
                context,
                BASE_REQ_CODE + slotIdx,
                cancelIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }

        if (!prefs.stackRemindersEnabled) return@withContext

        val stack = supplementDao.getActiveWithSchedulesOnce()
        val bedtimeMin = ((prefs.bedtimeMinutesOfDay % 1440) + 1440) % 1440
        val defaultBedtimeReminderMin = ((bedtimeMin - 45) + 1440) % 1440

        data class SlotTarget(val minuteOfDay: Int, val mask: Int)

        val targetsByMin = linkedMapOf<Int, Int>()
        for (sw in stack) {
            val activeSchedules = sw.schedules.filter { it.enabled && it.deletedAt == null }
            for (s in activeSchedules) {
                val minOfDay = when (s.triggerType) {
                    SupplementTrigger.FIXED_TIME -> s.timeOfDay?.let { it.hour * 60 + it.minute }
                    SupplementTrigger.WITH_MEAL -> s.timeOfDay?.let { it.hour * 60 + it.minute } ?: (9 * 60)
                    SupplementTrigger.BEDTIME -> s.timeOfDay?.let { it.hour * 60 + it.minute } ?: defaultBedtimeReminderMin
                    SupplementTrigger.BEFORE_WORKOUT,
                    SupplementTrigger.AFTER_WORKOUT -> null
                } ?: continue
                targetsByMin[minOfDay] = (targetsByMin[minOfDay] ?: 0) or s.daysOfWeekMask
            }
        }

        val now = ZonedDateTime.now()
        targetsByMin.entries
            .map { SlotTarget(it.key, it.value) }
            .sortedBy { it.minuteOfDay }
            .take(MAX_SLOTS)
            .forEachIndexed { idx, slot ->
                val triggerAt = nextTriggerAfter(now, slot.minuteOfDay, slot.mask) ?: return@forEachIndexed
                val intent = Intent(context, StackReminderReceiver::class.java).apply {
                    action = StackReminderReceiver.ACTION_FIRE_REMINDER
                    putExtra(StackReminderReceiver.EXTRA_MINUTE_OF_DAY, slot.minuteOfDay)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    BASE_REQ_CODE + idx,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                runCatching {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt.toInstant().toEpochMilli(),
                        pi
                    )
                }
            }
    }

    private fun nextTriggerAfter(
        now: ZonedDateTime,
        minuteOfDay: Int,
        daysMask: Int
    ): ZonedDateTime? {
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        for (dayOffset in 0L..7L) {
            val candidateDate = now.toLocalDate().plusDays(dayOffset)
            val dayBit = 1 shl (candidateDate.dayOfWeek.value - DayOfWeek.MONDAY.value)
            if (daysMask != 0 && (daysMask and dayBit) == 0) continue
            val candidate = ZonedDateTime.of(candidateDate, time, now.zone)
            if (candidate.isAfter(now.plusSeconds(15))) {
                return candidate
            }
        }
        return null
    }

    companion object {
        private const val BASE_REQ_CODE = 7100
        private const val MAX_SLOTS = 8
    }
}
