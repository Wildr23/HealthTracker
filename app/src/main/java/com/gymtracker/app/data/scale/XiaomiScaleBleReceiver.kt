package com.gymtracker.app.data.scale

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gymtracker.app.data.db.BodyWeightDao
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.domain.XiaomiBodyCompositionCalculator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class XiaomiScaleBleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prefsRepo: UserPreferencesRepository

    @Inject
    lateinit var bodyWeightDao: BodyWeightDao

    @Inject
    lateinit var readingsRepository: ScaleReadingsRepository

    @Inject
    lateinit var bleController: XiaomiScaleBleController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != XiaomiScaleBleController.ACTION_BLE_SCAN_RESULT) return
        val results = intent.getParcelableArrayListExtra(
            BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
            ScanResult::class.java
        ) ?: return
        if (results.isEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleResults(results)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleResults(results: List<ScanResult>) {
        val prefs = prefsRepo.getOnce()
        if (!prefs.scaleBackgroundEnabled) return
        val boundMac = prefs.scaleMacAddress?.uppercase(Locale.US) ?: return

        data class Candidate(val packet: ParsedScalePacket, val name: String?)

        val candidates = results.mapNotNull { sr ->
            val mac = sr.device?.address?.uppercase(Locale.US) ?: return@mapNotNull null
            if (mac != boundMac) return@mapNotNull null
            val serviceData = sr.scanRecord?.serviceData ?: return@mapNotNull null
            val uuidMap = serviceData.entries.associate { it.key.uuid to it.value }
            val parsed = XiaomiScaleBleParser.parseServiceData(uuidMap) ?: return@mapNotNull null
            if (!parsed.isStabilized || parsed.isRemoved) return@mapNotNull null
            Candidate(parsed, sr.scanRecord?.deviceName ?: prefs.scaleName)
        }

        val best = candidates.sortedByDescending { if (it.packet.hasImpedance) 1 else 0 }
            .firstOrNull() ?: return

        val latestValidWeight = runCatching { bodyWeightDao.getLatestValid()?.kg }.getOrNull()
        val (minKg, maxKg) = bleController.resolveWeightRange(prefs, latestValidWeight)
        if (!XiaomiScaleBleParser.isWithinUserRange(best.packet.weightKg, minKg, maxKg)) {
            return
        }

        val nowSec = System.currentTimeMillis() / 1000L
        val elapsedSec = nowSec - prefs.scaleLastAutoEpochSec

        if (best.packet.hasImpedance) {
            if (elapsedSec in 0L..29L && prefs.scaleLastAutoHadImpedance) return
        } else {
            if (elapsedSec in 0L..59L) return
        }

        val today = LocalDate.now()
        val age = (today.year - prefs.birthYear).coerceIn(10, 100)
        val composition = XiaomiBodyCompositionCalculator.calculate(
            weightKg = best.packet.weightKg,
            heightCm = prefs.heightCm,
            age = age,
            isMale = prefs.isMale,
            impedanceOhm = best.packet.impedanceOhm
        )

        readingsRepository.saveWeighIn(
            date = today,
            composition = composition,
            deviceName = best.name ?: prefs.scaleName,
            macAddress = boundMac
        )
        prefsRepo.recordScaleAutoCapture(epochSec = nowSec, hadImpedance = best.packet.hasImpedance)
        bleController.showCapturedNotification(
            composition = composition,
            pendingImpedance = !best.packet.hasImpedance &&
                best.packet.protocol == ScaleProtocol.BODY_COMPOSITION_181B
        )
    }
}
