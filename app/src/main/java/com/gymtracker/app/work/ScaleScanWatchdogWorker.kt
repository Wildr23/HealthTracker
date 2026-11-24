package com.gymtracker.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gymtracker.app.data.scale.XiaomiScaleBleController
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ScaleScanWatchdogWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val bleController: XiaomiScaleBleController
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        bleController.syncBackgroundScan(force = true)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "xiaomi_scale_scan_watchdog"

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<ScaleScanWatchdogWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
