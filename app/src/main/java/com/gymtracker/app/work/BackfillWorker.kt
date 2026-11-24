package com.gymtracker.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gymtracker.app.R
import com.gymtracker.app.data.health.HealthSyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@HiltWorker
class BackfillWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val healthSyncRepository: HealthSyncRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val fromMillis = inputData.getLong(
            KEY_FROM_EPOCH_MILLIS,
            LocalDate.of(2026, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        val fromInstant = Instant.ofEpochMilli(fromMillis)

        ensureChannel()
        var lastMatched = 0
        var lastTotal = 0

        healthSyncRepository.backfillWorkouts(fromInstant).collect { progress ->
            lastMatched = progress.matched
            lastTotal = progress.total
            setProgress(
                workDataOf(
                    KEY_DONE to progress.done,
                    KEY_TOTAL to progress.total,
                    KEY_MATCHED to progress.matched,
                    KEY_HR_ONLY to progress.hrOnly
                )
            )
            postNotification(
                title = appContext.getString(R.string.backfill_notif_title),
                text = appContext.getString(
                    R.string.backfill_notif_progress,
                    progress.done,
                    progress.total,
                    progress.matched
                ),
                max = progress.total,
                current = progress.done,
                ongoing = progress.done < progress.total
            )
        }

        postNotification(
            title = appContext.getString(R.string.backfill_notif_done_title),
            text = appContext.getString(R.string.backfill_notif_done_body, lastMatched, lastTotal),
            max = 0,
            current = 0,
            ongoing = false
        )

        return Result.success(
            workDataOf(
                KEY_TOTAL to lastTotal,
                KEY_MATCHED to lastMatched
            )
        )
    }

    private fun ensureChannel() {
        val nm = appContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.channel_sync_name),
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    private fun postNotification(title: String, text: String, max: Int, current: Int, ongoing: Boolean) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = appContext.getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
        if (max > 0 && ongoing) {
            builder.setProgress(max, current, false)
        }
        nm.notify(NOTIF_ID, builder.build())
    }

    companion object {
        const val WORK_NAME = "hc_retroactive_backfill"
        const val KEY_FROM_EPOCH_MILLIS = "from_millis"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_MATCHED = "matched"
        const val KEY_HR_ONLY = "hr_only"
        private const val CHANNEL_ID = "hc_backfill_channel"
        private const val NOTIF_ID = 4102

        fun enqueue(context: Context, fromInstant: Instant) {
            val req = OneTimeWorkRequestBuilder<BackfillWorker>()
                .setInputData(workDataOf(KEY_FROM_EPOCH_MILLIS to fromInstant.toEpochMilli()))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }
}
