package com.gymtracker.app.data.scale

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

    override fun onReceive(context: Context, intent: Intent) {
        val shouldSync = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> true
            BluetoothAdapter.ACTION_STATE_CHANGED ->
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) ==
                    BluetoothAdapter.STATE_ON
            else -> false
        }
        if (!shouldSync) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                bleController.syncBackgroundScan()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
