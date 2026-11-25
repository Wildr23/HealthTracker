package com.gymtracker.app.data.scale

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gymtracker.app.R
import com.gymtracker.app.data.db.BodyWeightDao
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.domain.ScaleBodyComposition
import com.gymtracker.app.domain.XiaomiBodyCompositionCalculator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/**
 * Short-lived (max 25s) Foreground Service (connectedDevice) spun up when the scale wakes in the background.
 * Runs SCAN_MODE_LOW_LATENCY so the 3-5 second BIA impedance packet is never missed even when the app is closed.
 */
@AndroidEntryPoint
class XiaomiScaleCaptureService : Service() {

    @Inject
    lateinit var prefsRepo: UserPreferencesRepository

    @Inject
    lateinit var bodyWeightDao: BodyWeightDao

    @Inject
    lateinit var readingsRepository: ScaleReadingsRepository

    @Inject
    lateinit var bleController: XiaomiScaleBleController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var burstScanCallback: ScanCallback? = null
    private var timeoutJob: Job? = null
    private var fallbackWeightJob: Job? = null
    private var pendingFallbackComp: ScaleBodyComposition? = null
    private var pendingFallbackDevice: String? = null
    @Volatile
    private var finished = false

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureBurstChannel()
        val notif = NotificationCompat.Builder(this, BURST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_health_tracker)
            .setContentTitle(getString(R.string.scale_title))
            .setContentText(getString(R.string.scale_burst_notif_text))
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(
            BURST_NOTIF_ID,
            notif,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        if (burstScanCallback == null) {
            scope.launch { startLowLatencyBurst() }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private suspend fun startLowLatencyBurst() {
        val prefs = prefsRepo.getOnce()
        val boundMac = prefs.scaleMacAddress?.uppercase(Locale.US)
        if (!prefs.scaleBackgroundEnabled || boundMac.isNullOrBlank() ||
            !bleController.hasBlePermissions() || !bleController.isBluetoothEnabled()
        ) {
            stopSelfSafely()
            return
        }

        val scanner = getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            stopSelfSafely()
            return
        }

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                scope.launch { handleBurstPacket(result, boundMac) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                scope.launch {
                    results.forEach { handleBurstPacket(it, boundMac) }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "Burst BLE scan failed: $errorCode")
                stopSelfSafely()
            }
        }
        burstScanCallback = cb

        val filters = listOf(ScanFilter.Builder().setDeviceAddress(boundMac).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        try {
            scanner.startScan(filters, settings, cb)
        } catch (se: SecurityException) {
            Log.w(TAG, "Missing BLE permission in burst service", se)
            stopSelfSafely()
            return
        }

        // Hard 25-second ceiling so service can never remain running
        timeoutJob = scope.launch {
            delay(25_000L)
            flushFallbackIfAny(boundMac)
            stopSelfSafely()
        }
    }

    private suspend fun handleBurstPacket(result: ScanResult, boundMac: String) {
        if (finished) return
        val mac = result.device?.address?.uppercase(Locale.US) ?: return
        if (!mac.equals(boundMac, ignoreCase = true)) return
        val serviceData = result.scanRecord?.serviceData ?: return
        val packet = XiaomiScaleBleParser.parseServiceData(
            serviceData.entries.associate { it.key.uuid to it.value }
        ) ?: return

        val prefs = prefsRepo.getOnce()
        val devName = result.scanRecord?.deviceName ?: prefs.scaleName

        if (packet.isRemoved) {
            if (pendingFallbackComp != null) {
                flushFallbackIfAny(boundMac)
                stopSelfSafely()
            }
            return
        }

        if (!packet.isStabilized) return

        val latestValid = runCatching { bodyWeightDao.getLatestValid()?.kg }.getOrNull()
        val range = bleController.resolveWeightRange(prefs, latestValid)
        if (!XiaomiScaleBleParser.isWithinUserRange(packet.weightKg, range.first, range.second)) {
            prefsRepo.incrementScaleIgnoredFamily()
            stopSelfSafely()
            return
        }

        val today = LocalDate.now()
        val age = (today.year - prefs.birthYear).coerceIn(10, 100)
        val comp = XiaomiBodyCompositionCalculator.calculate(
            weightKg = packet.weightKg,
            heightCm = prefs.heightCm,
            age = age,
            isMale = prefs.isMale,
            impedanceOhm = packet.impedanceOhm
        )

        if (packet.hasImpedance || packet.protocol == ScaleProtocol.WEIGHT_SCALE_181D) {
            fallbackWeightJob?.cancel()
            pendingFallbackComp = null
            readingsRepository.saveWeighIn(
                date = today,
                composition = comp,
                deviceName = devName,
                macAddress = boundMac
            )
            val nowSec = System.currentTimeMillis() / 1000L
            prefsRepo.recordScaleAutoCapture(epochSec = nowSec, hadImpedance = packet.hasImpedance)
            bleController.showCapturedNotification(composition = comp, pendingImpedance = false)
            stopSelfSafely()
            return
        }

        // Stabilized 0x181B without impedance yet — hold up to 7s for BIA feet reading
        pendingFallbackComp = comp
        pendingFallbackDevice = devName
        if (fallbackWeightJob == null || fallbackWeightJob?.isActive != true) {
            fallbackWeightJob = scope.launch {
                delay(7_000L)
                flushFallbackIfAny(boundMac)
                stopSelfSafely()
            }
        }
    }

    private suspend fun flushFallbackIfAny(boundMac: String) {
        if (finished) return
        val comp = pendingFallbackComp ?: return
        pendingFallbackComp = null
        readingsRepository.saveWeighIn(
            date = LocalDate.now(),
            composition = comp,
            deviceName = pendingFallbackDevice,
            macAddress = boundMac
        )
        prefsRepo.recordScaleAutoCapture(
            epochSec = System.currentTimeMillis() / 1000L,
            hadImpedance = false
        )
        bleController.showCapturedNotification(composition = comp, pendingImpedance = false)
    }

    @SuppressLint("MissingPermission")
    private fun stopSelfSafely() {
        if (finished) return
        finished = true
        timeoutJob?.cancel()
        fallbackWeightJob?.cancel()
        burstScanCallback?.let { cb ->
            runCatching {
                getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(cb)
            }
        }
        burstScanCallback = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureBurstChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            BURST_CHANNEL_ID,
            getString(R.string.scale_notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(ch)
    }

    override fun onDestroy() {
        burstScanCallback?.let { cb ->
            runCatching {
                getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(cb)
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "XiaomiScaleBurstSvc"
        private const val BURST_CHANNEL_ID = "xiaomi_scale_burst_channel"
        private const val BURST_NOTIF_ID = 4206

        fun startBurst(context: Context) {
            context.startForegroundService(Intent(context, XiaomiScaleCaptureService::class.java))
        }
    }
}
