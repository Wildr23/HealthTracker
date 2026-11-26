package com.gymtracker.app.data.scale

import android.app.AlarmManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gymtracker.app.work.StackReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class XiaomiScaleBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var bleController: XiaomiScaleBleController

    @Inject
    lateinit var stackReminderScheduler: StackReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val shouldSyncBle = when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> true
            BluetoothAdapter.ACTION_STATE_CHANGED ->
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) ==
                    BluetoothAdapter.STATE_ON
            else -> false
        }
        val shouldSyncReminders = action in setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
        if (!shouldSyncBle && !shouldSyncReminders) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (shouldSyncBle) {
                    bleController.syncBackgroundScan()
                }
                if (shouldSyncReminders) {
                    stackReminderScheduler.rescheduleAll()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
