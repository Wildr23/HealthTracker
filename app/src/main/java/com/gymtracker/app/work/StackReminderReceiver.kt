package com.gymtracker.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gymtracker.app.MainActivity
import com.gymtracker.app.R
import com.gymtracker.app.data.db.SupplementDao
import com.gymtracker.app.data.db.SupplementTrigger
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.SupplementRepository
import com.gymtracker.app.domain.SupplementScheduleEngine
import com.gymtracker.app.ui.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class StackReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var supplementDao: SupplementDao

    @Inject
    lateinit var supplementRepository: SupplementRepository

    @Inject
    lateinit var prefsRepo: UserPreferencesRepository

    @Inject
    lateinit var reminderScheduler: StackReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE_REMINDER -> {
                        val minuteOfDay = intent.getIntExtra(EXTRA_MINUTE_OF_DAY, -1)
                        if (minuteOfDay >= 0) {
                            handleReminder(context, minuteOfDay)
                        }
                        reminderScheduler.rescheduleAll()
                    }
                    ACTION_MARK_TAKEN -> {
                        val ids = intent.getLongArrayExtra(EXTRA_ITEM_IDS) ?: LongArray(0)
                        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                        ids.forEach { id ->
                            runCatching { supplementRepository.logIntake(id) }
                        }
                        if (notifId >= 0) {
                            val nm = context.getSystemService(NotificationManager::class.java)
                            nm?.cancel(notifId)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReminder(context: Context, minuteOfDay: Int) {
        val prefs = prefsRepo.getOnce()
        if (!prefs.stackRemindersEnabled) return

        val hasNotifPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasNotifPerm) return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayStart = today.atStartOfDay(zone).toInstant()
        val bedtimeMin = ((prefs.bedtimeMinutesOfDay % 1440) + 1440) % 1440
        val defaultBedtimeReminderMin = ((bedtimeMin - 45) + 1440) % 1440

        val stack = supplementDao.getActiveWithSchedulesOnce()
            .filter { it.item.notes != SupplementRepository.QUICK_CAFFEINE_TAG }
        val takenIdsToday = supplementDao.observeLogsSince(todayStart).first()
            .filter { it.deletedAt == null }
            .map { it.supplementId }
            .toSet()

        val dueItems = stack.filter { sw ->
            if (sw.item.id in takenIdsToday) return@filter false
            if (!SupplementScheduleEngine.isCycleActiveOnDate(sw.item, today)) return@filter false
            if (!SupplementScheduleEngine.isScheduledForDate(sw.schedules, today)) return@filter false
            sw.schedules.any { s ->
                if (!s.enabled || s.deletedAt != null) return@any false
                val targetMin = when (s.triggerType) {
                    SupplementTrigger.FIXED_TIME -> s.timeOfDay?.let { it.hour * 60 + it.minute }
                    SupplementTrigger.WITH_MEAL -> s.timeOfDay?.let { it.hour * 60 + it.minute } ?: (9 * 60)
                    SupplementTrigger.BEDTIME -> s.timeOfDay?.let { it.hour * 60 + it.minute } ?: defaultBedtimeReminderMin
                    SupplementTrigger.BEFORE_WORKOUT, SupplementTrigger.AFTER_WORKOUT -> null
                }
                targetMin == minuteOfDay
            }
        }

        if (dueItems.isEmpty()) return

        ensureChannel(context)
        val isUk = context.resources.configuration.locales[0]?.language == "uk"
        val names = dueItems.joinToString(", ") { sw ->
            if (isUk) sw.item.nameUk else sw.item.nameEn
        }
        val title = context.getString(R.string.stack_notif_title)
        val body = context.getString(R.string.stack_notif_body, names)

        val notifId = 8100 + minuteOfDay
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, Routes.STACK)
        }
        val openPi = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markIntent = Intent(context, StackReminderReceiver::class.java).apply {
            action = ACTION_MARK_TAKEN
            putExtra(EXTRA_ITEM_IDS, dueItems.map { it.item.id }.toLongArray())
            putExtra(EXTRA_NOTIF_ID, notifId)
        }
        val markPi = PendingIntent.getBroadcast(
            context,
            notifId + 2000,
            markIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_health_tracker)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openPi)
            .addAction(0, context.getString(R.string.stack_notif_action_take), markPi)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.notify(notifId, notification)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.stack_notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.stack_notif_channel_desc)
                }
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "stack_reminders"
        const val ACTION_FIRE_REMINDER = "com.gymtracker.app.ACTION_STACK_REMINDER"
        const val ACTION_MARK_TAKEN = "com.gymtracker.app.ACTION_STACK_MARK_TAKEN"
        const val EXTRA_MINUTE_OF_DAY = "minute_of_day"
        const val EXTRA_ITEM_IDS = "item_ids"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
