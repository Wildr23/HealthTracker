package com.gymtracker.app.data.scale

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gymtracker.app.MainActivity
import com.gymtracker.app.R
import com.gymtracker.app.data.db.BodyWeightDao
import com.gymtracker.app.data.prefs.UserPreferences
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.domain.ScaleBodyComposition
import com.gymtracker.app.ui.navigation.Routes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class LiveScaleEvent(
    val macAddress: String,
    val deviceName: String?,
    val rssi: Int,
    val packet: ParsedScalePacket
)

@Singleton
class XiaomiScaleBleController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepo: UserPreferencesRepository,
    private val bodyWeightDao: BodyWeightDao
) {
    private val bluetoothManager: BluetoothManager?
        get() = context.getSystemService(BluetoothManager::class.java)

    @Volatile
    private var lastRegisteredMac: String? = null

    @Volatile
    private var lastSyncAtMs: Long = 0L

    fun hasBlePermissions(): Boolean {
        val scanOk = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
        val connectOk = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        return scanOk && connectOk
    }

    fun isBluetoothEnabled(): Boolean =
        bluetoothManager?.adapter?.isEnabled == true

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun resolveWeightRange(prefs: UserPreferences, latestWeightKg: Double?): Pair<Double, Double> {
        val minCustom = prefs.scaleMinWeightKg
        val maxCustom = prefs.scaleMaxWeightKg
        if (minCustom != null && maxCustom != null) {
            return minOf(minCustom, maxCustom) to maxOf(minCustom, maxCustom)
        }
        val base = latestWeightKg ?: prefs.defaultBodyWeightKg
        val lo = (base - 12.0).coerceAtLeast(30.0)
        val hi = (base + 12.0).coerceAtMost(220.0)
        return lo to hi
    }

    suspend fun currentResolvedWeightRange(): Pair<Double, Double> {
        val prefs = prefsRepo.getOnce()
        val latestValid = runCatching { bodyWeightDao.getLatestValid()?.kg }.getOrNull()
        return resolveWeightRange(prefs, latestValid)
    }

    @SuppressLint("MissingPermission")
    fun observeLiveScaleBroadcasts(): Flow<LiveScaleEvent> = callbackFlow {
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner
        if (scanner == null || !hasBlePermissions() || !isBluetoothEnabled()) {
            close()
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                emitResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::emitResult)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "Foreground BLE scan failed: errorCode=$errorCode")
            }

            private fun emitResult(result: ScanResult) {
                val serviceData = result.scanRecord?.serviceData ?: return
                if (serviceData.isEmpty()) return
                val uuidMap = serviceData.entries.associate { it.key.uuid to it.value }
                val parsed = XiaomiScaleBleParser.parseServiceData(uuidMap) ?: return
                val mac = result.device?.address?.uppercase(Locale.US) ?: return
                val devName = result.scanRecord?.deviceName
                    ?: runCatching { result.device?.name }.getOrNull()
                trySend(
                    LiveScaleEvent(
                        macAddress = mac,
                        deviceName = devName,
                        rssi = result.rssi,
                        packet = parsed
                    )
                )
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        try {
            scanner.startScan(null, settings, callback)
        } catch (se: SecurityException) {
            Log.w(TAG, "Missing BLE scan permission", se)
            close(se)
            return@callbackFlow
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start foreground BLE scan", e)
            close(e)
            return@callbackFlow
        }

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun syncBackgroundScan(force: Boolean = false) {
        val prefs = prefsRepo.getOnce()
        val mac = prefs.scaleMacAddress?.uppercase(Locale.US)
        if (!prefs.scaleBackgroundEnabled ||
            mac.isNullOrBlank() ||
            !hasBlePermissions() ||
            !isBluetoothEnabled() ||
            !BluetoothAdapter.checkBluetoothAddress(mac)
        ) {
            stopBackgroundScan()
            return
        }

        val nowMs = System.currentTimeMillis()
        if (!force && mac == lastRegisteredMac && (nowMs - lastSyncAtMs) < 10_000L) {
            return
        }

        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: return
        val filters = listOf(
            ScanFilter.Builder()
                .setDeviceAddress(mac)
                .build()
        )
        // Use SCAN_MODE_BALANCED (~2s window) so closed-app wake catches short 3-5s BIA bursts reliably
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        val pi = backgroundScanPendingIntent()
        try {
            scanner.stopScan(pi)
            scanner.startScan(filters, settings, pi)
            lastRegisteredMac = mac
            lastSyncAtMs = nowMs
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException starting background BLE scan", se)
        } catch (e: Exception) {
            Log.w(TAG, "Failed starting background BLE scan", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBackgroundScan() {
        lastRegisteredMac = null
        lastSyncAtMs = 0L
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: return
        try {
            scanner.stopScan(backgroundScanPendingIntent())
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    fun showCapturedNotification(composition: ScaleBodyComposition, pendingImpedance: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.scale_notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.scale_notif_channel_desc)
        }
        nm.createNotificationChannel(channel)

        val title = if (composition.fatPercent != null) {
            context.getString(R.string.scale_notif_title, composition.weightKg, composition.fatPercent)
        } else {
            context.getString(R.string.scale_notif_title_weight_only, composition.weightKg)
        }

        val body = when {
            composition.muscleMassKg != null && composition.waterPercent != null ->
                context.getString(
                    R.string.scale_notif_text_full,
                    composition.muscleMassKg,
                    composition.waterPercent,
                    composition.visceralFat
                )
            pendingImpedance -> context.getString(R.string.scale_notif_text_pending_imp)
            else -> context.getString(R.string.scale_notif_text_no_imp, composition.bmi, composition.bmrKcal)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, Routes.HEALTH)
        }
        val tapPi = PendingIntent.getActivity(
            context,
            REQ_OPEN_HEALTH,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tapPi)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID, notification)
    }

    private fun backgroundScanPendingIntent(): PendingIntent {
        val intent = Intent(context, XiaomiScaleBleReceiver::class.java).apply {
            action = ACTION_BLE_SCAN_RESULT
        }
        return PendingIntent.getBroadcast(
            context,
            REQ_BLE_SCAN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    companion object {
        private const val TAG = "XiaomiScaleBle"
        const val ACTION_BLE_SCAN_RESULT = "com.gymtracker.app.ACTION_XIAOMI_SCALE_BLE_SCAN"
        private const val REQ_BLE_SCAN = 4201
        private const val REQ_OPEN_HEALTH = 4202
        private const val NOTIF_ID = 4205
        private const val CHANNEL_ID = "xiaomi_scale_channel"
    }
}
