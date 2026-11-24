package com.gymtracker.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gymtracker.app.MainActivity
import com.gymtracker.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WorkoutTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null

    private var workoutStartMillis: Long = 0L
    private var restEndMillis: Long = 0L
    private var workoutTitle: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                workoutStartMillis = intent.getLongExtra(EXTRA_START_MILLIS, System.currentTimeMillis())
                workoutTitle = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.active_workout_title)
                ensureChannel()
                val notif = buildNotification()
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
                } else {
                    startForeground(NOTIF_ID, notif)
                }
                startTicker()
            }
            ACTION_UPDATE_REST -> {
                restEndMillis = intent.getLongExtra(EXTRA_REST_END_MILLIS, 0L)
                updateNotification()
            }
            ACTION_STOP -> {
                tickerJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                updateNotification()
                delay(1000L)
            }
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val now = System.currentTimeMillis()
        val elapsedSec = ((now - workoutStartMillis).coerceAtLeast(0L)) / 1000L
        val elapsedStr = String.format("%02d:%02d", elapsedSec / 60, elapsedSec % 60)

        val restRemaining = ((restEndMillis - now) / 1000L).coerceAtLeast(0L)
        val content = if (restRemaining > 0L) {
            getString(
                R.string.workout_notif_with_rest,
                elapsedStr,
                String.format("%02d:%02d", restRemaining / 60, restRemaining % 60)
            )
        } else {
            getString(R.string.workout_notif_elapsed, elapsedStr)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(workoutTitle.ifBlank { getString(R.string.active_workout_title) })
            .setContentText(content)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_workout_name),
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.gymtracker.app.action.WORKOUT_START"
        const val ACTION_UPDATE_REST = "com.gymtracker.app.action.WORKOUT_REST"
        const val ACTION_STOP = "com.gymtracker.app.action.WORKOUT_STOP"
        const val EXTRA_START_MILLIS = "start_millis"
        const val EXTRA_REST_END_MILLIS = "rest_end_millis"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL_ID = "active_workout_channel"
        private const val NOTIF_ID = 4101

        fun start(context: Context, startMillis: Long, title: String) {
            val intent = Intent(context, WorkoutTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_START_MILLIS, startMillis)
                putExtra(EXTRA_TITLE, title)
            }
            runCatching { context.startForegroundService(intent) }
        }

        fun updateRest(context: Context, restEndMillis: Long) {
            val intent = Intent(context, WorkoutTimerService::class.java).apply {
                action = ACTION_UPDATE_REST
                putExtra(EXTRA_REST_END_MILLIS, restEndMillis)
            }
            runCatching { context.startService(intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WorkoutTimerService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
