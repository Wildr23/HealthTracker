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
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gymtracker.app.MainActivity
import com.gymtracker.app.R
import com.gymtracker.app.data.db.SupplementDao
import com.gymtracker.app.data.db.SupplementItemEntity
import com.gymtracker.app.data.db.SupplementScheduleEntity
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
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToInt

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
                        reminderScheduler.rescheduleAll(minLeadSeconds = 120L)
                    }
                    ACTION_FIRE_SNOOZE -> {
                        val minuteOfDay = intent.getIntExtra(EXTRA_MINUTE_OF_DAY, -1)
                        val itemIds = intent.getLongArrayExtra(EXTRA_ITEM_IDS) ?: LongArray(0)
                        val schedIds = intent.getLongArrayExtra(EXTRA_SCHEDULE_IDS) ?: LongArray(0)
                        if (minuteOfDay >= 0 && itemIds.isNotEmpty()) {
                            handleSnoozedReminder(context, minuteOfDay, itemIds, schedIds)
                        }
                    }
                    ACTION_MARK_TAKEN -> {
                        val ids = intent.getLongArrayExtra(EXTRA_ITEM_IDS) ?: LongArray(0)
                        val schedIds = intent.getLongArrayExtra(EXTRA_SCHEDULE_IDS) ?: LongArray(0)
                        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                        val targets = ids.indices.map { idx ->
                            val rawSched = schedIds.getOrNull(idx) ?: -1L
                            ids[idx] to rawSched.takeIf { it > 0L }
                        }
                        runCatching { supplementRepository.logBatch(targets) }
                        if (notifId >= 0) {
                            context.getSystemService(NotificationManager::class.java)?.cancel(notifId)
                        }
                    }
                    ACTION_SNOOZE -> {
                        val ids = intent.getLongArrayExtra(EXTRA_ITEM_IDS) ?: LongArray(0)
                        val schedIds = intent.getLongArrayExtra(EXTRA_SCHEDULE_IDS) ?: LongArray(0)
                        val minuteOfDay = intent.getIntExtra(EXTRA_MINUTE_OF_DAY, 0)
                        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                        if (ids.isNotEmpty()) {
                            reminderScheduler.scheduleSnooze(minuteOfDay, ids, schedIds, delayMinutes = 30)
                        }
                        if (notifId >= 0) {
                            context.getSystemService(NotificationManager::class.java)?.cancel(notifId)
                        }
                    }
                    ACTION_TEST_REMINDER -> {
                        postTestNotification(context)
                    }
                    ACTION_TEST_ACTION -> {
                        val label = intent.getStringExtra(EXTRA_TEST_LABEL).orEmpty()
                        context.getSystemService(NotificationManager::class.java)?.cancel(TEST_NOTIF_ID)
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context.applicationContext, "OK: $label", Toast.LENGTH_SHORT).show()
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
        if (!prefs.stackRemindersEnabled || !hasNotificationPermission(context)) return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayStart = today.atStartOfDay(zone).toInstant()
        val bedtimeMin = ((prefs.bedtimeMinutesOfDay % 1440) + 1440) % 1440

        val stack = supplementDao.getActiveWithSchedulesOnce()
            .filter { it.item.notes != SupplementRepository.QUICK_CAFFEINE_TAG }
        val todayLogs = supplementDao.observeLogsSince(todayStart).first()
            .filter { it.deletedAt == null }
        val logScheduleMap = prefsRepo.getLogScheduleMapOnce()

        val duePairs = mutableListOf<Pair<SupplementItemEntity, SupplementScheduleEntity>>()
        for (sw in stack) {
            if (!SupplementScheduleEngine.isCycleActiveOnDate(sw.item, today)) continue
            val doses = SupplementScheduleEngine.matchTodayDoses(
                sw = sw,
                todayLogs = todayLogs,
                logScheduleMap = logScheduleMap,
                today = today,
                bedtimeMinutesOfDay = bedtimeMin,
                zoneId = zone
            )
            for (dose in doses) {
                if (dose.matchedLog != null) continue
                val sched = dose.schedule ?: continue
                if (SupplementScheduleEngine.targetMinuteOfDay(sched, bedtimeMin) == minuteOfDay) {
                    duePairs += sw.item to sched
                }
            }
        }

        if (duePairs.isEmpty()) return
        postStackNotification(context, minuteOfDay, duePairs)
    }

    private suspend fun handleSnoozedReminder(
        context: Context,
        minuteOfDay: Int,
        itemIds: LongArray,
        schedIds: LongArray
    ) {
        val prefs = prefsRepo.getOnce()
        if (!prefs.stackRemindersEnabled || !hasNotificationPermission(context)) return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayStart = today.atStartOfDay(zone).toInstant()
        val bedtimeMin = ((prefs.bedtimeMinutesOfDay % 1440) + 1440) % 1440

        val stackById = supplementDao.getActiveWithSchedulesOnce()
            .filter { it.item.notes != SupplementRepository.QUICK_CAFFEINE_TAG }
            .associateBy { it.item.id }
        val todayLogs = supplementDao.observeLogsSince(todayStart).first()
            .filter { it.deletedAt == null }
        val logScheduleMap = prefsRepo.getLogScheduleMapOnce()

        val remaining = mutableListOf<Pair<SupplementItemEntity, SupplementScheduleEntity>>()
        for (idx in itemIds.indices) {
            val sw = stackById[itemIds[idx]] ?: continue
            val targetSchedId = schedIds.getOrNull(idx) ?: continue
            val doses = SupplementScheduleEngine.matchTodayDoses(
                sw = sw,
                todayLogs = todayLogs,
                logScheduleMap = logScheduleMap,
                today = today,
                bedtimeMinutesOfDay = bedtimeMin,
                zoneId = zone
            )
            val match = doses.firstOrNull { it.schedule?.id == targetSchedId && it.matchedLog == null }
            val sched = match?.schedule ?: continue
            remaining += sw.item to sched
        }

        if (remaining.isEmpty()) return
        postStackNotification(context, minuteOfDay, remaining)
    }

    private fun postStackNotification(
        context: Context,
        minuteOfDay: Int,
        duePairs: List<Pair<SupplementItemEntity, SupplementScheduleEntity>>
    ) {
        ensureChannel(context)
        val isUk = context.resources.configuration.locales[0]?.language == "uk"
        val timeStr = LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), (minuteOfDay % 60).coerceIn(0, 59))
            .format(DateTimeFormatter.ofPattern("HH:mm"))
        val names = duePairs.joinToString(", ") { (item, _) ->
            val nm = if (isUk) item.nameUk else item.nameEn
            val doseStr = if (item.defaultDose % 1.0 == 0.0) item.defaultDose.roundToInt().toString()
            else String.format("%.1f", item.defaultDose)
            "$nm ($doseStr)"
        }
        val title = context.getString(R.string.stack_notif_title_timed, timeStr)
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

        val itemIdArr = duePairs.map { it.first.id }.toLongArray()
        val schedIdArr = duePairs.map { it.second.id }.toLongArray()

        val markIntent = Intent(context, StackReminderReceiver::class.java).apply {
            action = ACTION_MARK_TAKEN
            putExtra(EXTRA_ITEM_IDS, itemIdArr)
            putExtra(EXTRA_SCHEDULE_IDS, schedIdArr)
            putExtra(EXTRA_NOTIF_ID, notifId)
        }
        val markPi = PendingIntent.getBroadcast(
            context,
            notifId + 2000,
            markIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, StackReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_MINUTE_OF_DAY, minuteOfDay)
            putExtra(EXTRA_ITEM_IDS, itemIdArr)
            putExtra(EXTRA_SCHEDULE_IDS, schedIdArr)
            putExtra(EXTRA_NOTIF_ID, notifId)
        }
        val snoozePi = PendingIntent.getBroadcast(
            context,
            notifId + 4000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val takeAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_health_tracker,
            context.getString(R.string.stack_notif_action_take),
            markPi
        ).setShowsUserInterface(false).build()

        val snoozeAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_health_tracker,
            context.getString(R.string.stack_notif_action_snooze),
            snoozePi
        ).setShowsUserInterface(false).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_health_tracker)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openPi)
            .addAction(takeAction)
            .addAction(snoozeAction)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)?.notify(notifId, notification)
    }

    private fun postTestNotification(context: Context) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, Routes.STACK)
        }
        val openPi = PendingIntent.getActivity(
            context,
            TEST_NOTIF_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val takeLabel = context.getString(R.string.stack_notif_action_take)
        val snoozeLabel = context.getString(R.string.stack_notif_action_snooze)
        val takeTestPi = PendingIntent.getBroadcast(
            context,
            TEST_NOTIF_ID + 1,
            Intent(context, StackReminderReceiver::class.java).apply {
                action = ACTION_TEST_ACTION
                putExtra(EXTRA_TEST_LABEL, takeLabel)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeTestPi = PendingIntent.getBroadcast(
            context,
            TEST_NOTIF_ID + 2,
            Intent(context, StackReminderReceiver::class.java).apply {
                action = ACTION_TEST_ACTION
                putExtra(EXTRA_TEST_LABEL, snoozeLabel)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val takeTestAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_health_tracker,
            takeLabel,
            takeTestPi
        ).setShowsUserInterface(false).build()

        val snoozeTestAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_health_tracker,
            snoozeLabel,
            snoozeTestPi
        ).setShowsUserInterface(false).build()

        val body = context.getString(R.string.stack_notif_test_body)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_health_tracker)
            .setContentTitle(context.getString(R.string.stack_notif_test_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openPi)
            .addAction(takeTestAction)
            .addAction(snoozeTestAction)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)?.notify(TEST_NOTIF_ID, notification)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.stack_notif_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.stack_notif_channel_desc)
                    enableVibration(true)
                    enableLights(true)
                }
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "stack_reminders_v2"
        const val ACTION_FIRE_REMINDER = "com.gymtracker.app.ACTION_STACK_REMINDER"
        const val ACTION_FIRE_SNOOZE = "com.gymtracker.app.ACTION_STACK_SNOOZE"
        const val ACTION_MARK_TAKEN = "com.gymtracker.app.ACTION_STACK_MARK_TAKEN"
        const val ACTION_SNOOZE = "com.gymtracker.app.ACTION_STACK_SNOOZE_ACTION"
        const val ACTION_TEST_REMINDER = "com.gymtracker.app.ACTION_STACK_TEST_REMINDER"
        const val ACTION_TEST_ACTION = "com.gymtracker.app.ACTION_STACK_TEST_ACTION"
        const val EXTRA_MINUTE_OF_DAY = "minute_of_day"
        const val EXTRA_ITEM_IDS = "item_ids"
        const val EXTRA_SCHEDULE_IDS = "schedule_ids"
        const val EXTRA_NOTIF_ID = "notif_id"
        const val EXTRA_TEST_LABEL = "test_label"
        private const val TEST_NOTIF_ID = 8999
    }
}
