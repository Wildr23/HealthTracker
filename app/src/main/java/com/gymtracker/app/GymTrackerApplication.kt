package com.gymtracker.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.gymtracker.app.data.scale.XiaomiScaleBleController
import com.gymtracker.app.work.CloudSyncWorker
import com.gymtracker.app.work.ScaleScanWatchdogWorker
import com.gymtracker.app.work.StackReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GymTrackerApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var xiaomiScaleBleController: XiaomiScaleBleController

    @Inject
    lateinit var stackReminderScheduler: StackReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CloudSyncWorker.schedulePeriodic(this)
        ScaleScanWatchdogWorker.schedulePeriodic(this)
        appScope.launch {
            xiaomiScaleBleController.syncBackgroundScan()
            stackReminderScheduler.rescheduleAll()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
