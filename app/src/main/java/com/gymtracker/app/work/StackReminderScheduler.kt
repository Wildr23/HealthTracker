package com.gymtracker.app.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gymtracker.app.data.db.SupplementDao
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.SupplementRepository
import com.gymtracker.app.domain.SupplementScheduleEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class NextReminderInfo(
    val triggerAt: ZonedDateTime,
    val itemNamesUk: List<String>,
    val itemNamesEn: List<String>,
    val exactAlarmGranted: Boolean
)

@Singleton
class StackReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supplementDao: SupplementDao,
    private val prefsRepo: UserPreferencesRepository
) {

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    suspend fun rescheduleAll(minLeadSeconds: Long = 15L) = withContext(Dispatchers.IO) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@withContext
        val prefs = prefsRepo.getOnce()

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
            .filter { it.item.notes != SupplementRepository.QUICK_CAFFEINE_TAG }
        val bedtimeMin = ((prefs.bedtimeMinutesOfDay % 1440) + 1440) % 1440

        val targetsByMin = linkedMapOf<Int, Int>()
        for (sw in stack) {
            val activeSchedules = sw.schedules.filter { it.enabled && it.deletedAt == null }
            for (s in activeSchedules) {
                val minOfDay = SupplementScheduleEngine.targetMinuteOfDay(s, bedtimeMin) ?: continue
                targetsByMin[minOfDay] = (targetsByMin[minOfDay] ?: 0) or s.daysOfWeekMask
            }
        }

        val now = ZonedDateTime.now()
        val exact = canScheduleExactAlarms()
        targetsByMin.entries
            .sortedBy { it.key }
            .take(MAX_SLOTS)
            .forEachIndexed { idx, (minOfDay, mask) ->
                val triggerAt = nextTriggerAfter(now, minOfDay, mask, minLeadSeconds) ?: return@forEachIndexed
                val intent = Intent(context, StackReminderReceiver::class.java).apply {
                    action = StackReminderReceiver.ACTION_FIRE_REMINDER
                    putExtra(StackReminderReceiver.EXTRA_MINUTE_OF_DAY, minOfDay)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    BASE_REQ_CODE + idx,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerMs = triggerAt.toInstant().toEpochMilli()
                runCatching {
                    if (exact) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
                    }
                }
            }
    }

    suspend fun getNextScheduledReminder(): NextReminderInfo? = withContext(Dispatchers.IO) {
        val prefs = prefsRepo.getOnce()
        if (!prefs.stackRemindersEnabled) return@withContext null
        val stack = supplementDao.getActiveWithSchedulesOnce()
            .filter { it.item.notes != SupplementRepository.QUICK_CAFFEINE_TAG }
        val bedtimeMin = ((prefs.bedtimeMinutesOfDay % 1440) + 1440) % 1440
        val now = ZonedDateTime.now()

        val slotNamesUk = linkedMapOf<Int, MutableList<String>>()
        val slotNamesEn = linkedMapOf<Int, MutableList<String>>()
        val slotMasks = linkedMapOf<Int, Int>()

        for (sw in stack) {
            val activeSchedules = sw.schedules.filter { it.enabled && it.deletedAt == null }
            for (s in activeSchedules) {
                val minOfDay = SupplementScheduleEngine.targetMinuteOfDay(s, bedtimeMin) ?: continue
                slotMasks[minOfDay] = (slotMasks[minOfDay] ?: 0) or s.daysOfWeekMask
                val ukList = slotNamesUk.getOrPut(minOfDay) { mutableListOf() }
                if (sw.item.nameUk !in ukList) ukList += sw.item.nameUk
                val enList = slotNamesEn.getOrPut(minOfDay) { mutableListOf() }
                if (sw.item.nameEn !in enList) enList += sw.item.nameEn
            }
        }

        val candidates = slotMasks.entries.mapNotNull { (minOfDay, mask) ->
            val triggerAt = nextTriggerAfter(now, minOfDay, mask, 15L) ?: return@mapNotNull null
            NextReminderInfo(
                triggerAt = triggerAt,
                itemNamesUk = slotNamesUk[minOfDay].orEmpty(),
                itemNamesEn = slotNamesEn[minOfDay].orEmpty(),
                exactAlarmGranted = canScheduleExactAlarms()
            )
        }
        candidates.minByOrNull { it.triggerAt }
    }

    fun scheduleSnooze(
        minuteOfDay: Int,
        itemIds: LongArray,
        scheduleIds: LongArray,
        delayMinutes: Int = 30
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerMs = ZonedDateTime.now().plusMinutes(delayMinutes.toLong()).toInstant().toEpochMilli()
        val intent = Intent(context, StackReminderReceiver::class.java).apply {
            action = StackReminderReceiver.ACTION_FIRE_SNOOZE
            putExtra(StackReminderReceiver.EXTRA_MINUTE_OF_DAY, minuteOfDay)
            putExtra(StackReminderReceiver.EXTRA_ITEM_IDS, itemIds)
            putExtra(StackReminderReceiver.EXTRA_SCHEDULE_IDS, scheduleIds)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            SNOOZE_REQ_CODE_BASE + ((minuteOfDay % 100) + 100) % 100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
        }
    }

    fun scheduleTestReminderIn5Sec(): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        val triggerMs = System.currentTimeMillis() + 5_000L
        val intent = Intent(context, StackReminderReceiver::class.java).apply {
            action = StackReminderReceiver.ACTION_TEST_REMINDER
        }
        val pi = PendingIntent.getBroadcast(
            context,
            TEST_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return runCatching {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
            true
        }.getOrDefault(false)
    }

    private fun nextTriggerAfter(
        now: ZonedDateTime,
        minuteOfDay: Int,
        daysMask: Int,
        minLeadSeconds: Long
    ): ZonedDateTime? {
        val time = LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), (minuteOfDay % 60).coerceIn(0, 59))
        for (dayOffset in 0L..7L) {
            val candidateDate = now.toLocalDate().plusDays(dayOffset)
            val dayBit = 1 shl (candidateDate.dayOfWeek.value - DayOfWeek.MONDAY.value)
            if (daysMask != 0 && (daysMask and dayBit) == 0) continue
            val candidate = ZonedDateTime.of(candidateDate, time, now.zone)
            if (candidate.isAfter(now.plusSeconds(minLeadSeconds))) {
                return candidate
            }
        }
        return null
    }

    companion object {
        private const val BASE_REQ_CODE = 7100
        private const val MAX_SLOTS = 16
        private const val SNOOZE_REQ_CODE_BASE = 7400
        private const val TEST_REQ_CODE = 7999
    }
}
