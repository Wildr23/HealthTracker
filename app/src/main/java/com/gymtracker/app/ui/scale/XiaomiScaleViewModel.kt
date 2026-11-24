package com.gymtracker.app.ui.scale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.db.BodyWeightDao
import com.gymtracker.app.data.prefs.UserPreferences
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.scale.LiveScaleEvent
import com.gymtracker.app.data.scale.ParsedScalePacket
import com.gymtracker.app.data.scale.ScaleProtocol
import com.gymtracker.app.data.scale.ScaleReadingSnapshot
import com.gymtracker.app.data.scale.ScaleReadingsRepository
import com.gymtracker.app.data.scale.XiaomiScaleBleController
import com.gymtracker.app.data.scale.XiaomiScaleBleParser
import com.gymtracker.app.domain.ScaleBodyComposition
import com.gymtracker.app.domain.XiaomiBodyCompositionCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class LiveWeighStage {
    IDLE,
    SCANNING,
    WEIGHING,
    WAITING_IMPEDANCE,
    SAVED,
    OUT_OF_RANGE
}

data class DiscoveredScale(
    val macAddress: String,
    val name: String?,
    val rssi: Int,
    val protocol: ScaleProtocol,
    val lastWeightKg: Double
)

private data class LiveScanRuntimeState(
    val hasBlePermissions: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val stage: LiveWeighStage = LiveWeighStage.IDLE,
    val liveWeightKg: Double? = null,
    val liveImpedanceOhm: Int? = null,
    val liveProtocol: ScaleProtocol? = null,
    val liveMacAddress: String? = null,
    val liveDeviceName: String? = null,
    val liveComposition: ScaleBodyComposition? = null,
    val discoveredScales: Map<String, DiscoveredScale> = emptyMap(),
    val isIgnoringBatteryOptimizations: Boolean = false
)

data class XiaomiScaleUiState(
    val prefs: UserPreferences = UserPreferences(),
    val hasBlePermissions: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val stage: LiveWeighStage = LiveWeighStage.IDLE,
    val liveWeightKg: Double? = null,
    val liveImpedanceOhm: Int? = null,
    val liveProtocol: ScaleProtocol? = null,
    val liveMacAddress: String? = null,
    val liveDeviceName: String? = null,
    val liveComposition: ScaleBodyComposition? = null,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val resolvedRange: Pair<Double, Double> = 93.0 to 117.0,
    val discoveredScales: List<DiscoveredScale> = emptyList(),
    val latestSavedReading: ScaleReadingSnapshot? = null
)

@HiltViewModel
class XiaomiScaleViewModel @Inject constructor(
    private val prefsRepo: UserPreferencesRepository,
    private val bodyWeightDao: BodyWeightDao,
    private val readingsRepository: ScaleReadingsRepository,
    private val bleController: XiaomiScaleBleController
) : ViewModel() {

    private val runtime = MutableStateFlow(
        LiveScanRuntimeState(
            hasBlePermissions = bleController.hasBlePermissions(),
            isBluetoothEnabled = bleController.isBluetoothEnabled(),
            isIgnoringBatteryOptimizations = bleController.isIgnoringBatteryOptimizations()
        )
    )

    private var scanJob: Job? = null
    private var impedanceFallbackJob: Job? = null
    private var savedWeightThisSession = false
    private var savedImpedanceThisSession = false
    private var lastLivePacket: ParsedScalePacket? = null

    val uiState: StateFlow<XiaomiScaleUiState> = combine(
        prefsRepo.preferences,
        bodyWeightDao.observeAll(),
        readingsRepository.latestReading,
        runtime
    ) { prefs, weights, latestSaved, rt ->
        val latestValidKg = weights.firstOrNull { !it.flaggedOutlier }?.kg
        val range = bleController.resolveWeightRange(prefs, latestValidKg)
        XiaomiScaleUiState(
            prefs = prefs,
            hasBlePermissions = rt.hasBlePermissions,
            isBluetoothEnabled = rt.isBluetoothEnabled,
            isScanning = rt.isScanning,
            stage = rt.stage,
            liveWeightKg = rt.liveWeightKg,
            liveImpedanceOhm = rt.liveImpedanceOhm,
            liveProtocol = rt.liveProtocol,
            liveMacAddress = rt.liveMacAddress,
            liveDeviceName = rt.liveDeviceName,
            liveComposition = rt.liveComposition,
            isIgnoringBatteryOptimizations = rt.isIgnoringBatteryOptimizations,
            resolvedRange = range,
            discoveredScales = rt.discoveredScales.values.sortedByDescending { it.rssi },
            latestSavedReading = latestSaved
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), XiaomiScaleUiState())

    fun refreshStatus(autoStartScan: Boolean = true) {
        val permOk = bleController.hasBlePermissions()
        val btOk = bleController.isBluetoothEnabled()
        val batOk = bleController.isIgnoringBatteryOptimizations()
        runtime.value = runtime.value.copy(
            hasBlePermissions = permOk,
            isBluetoothEnabled = btOk,
            isIgnoringBatteryOptimizations = batOk
        )
        viewModelScope.launch {
            bleController.syncBackgroundScan()
        }
        if (autoStartScan && permOk && btOk && scanJob == null) {
            startLiveScan()
        } else if ((!permOk || !btOk) && scanJob != null) {
            stopLiveScan()
        }
    }

    fun startLiveScan() {
        val permOk = bleController.hasBlePermissions()
        val btOk = bleController.isBluetoothEnabled()
        runtime.value = runtime.value.copy(
            hasBlePermissions = permOk,
            isBluetoothEnabled = btOk
        )
        if (!permOk || !btOk) return

        scanJob?.cancel()
        impedanceFallbackJob?.cancel()
        savedWeightThisSession = false
        savedImpedanceThisSession = false
        lastLivePacket = null

        runtime.value = runtime.value.copy(
            isScanning = true,
            stage = LiveWeighStage.SCANNING,
            liveWeightKg = null,
            liveImpedanceOhm = null,
            liveComposition = null
        )

        scanJob = viewModelScope.launch {
            bleController.observeLiveScaleBroadcasts().collect { event ->
                handleScaleEvent(event)
            }
        }
    }

    fun stopLiveScan() {
        scanJob?.cancel()
        scanJob = null
        impedanceFallbackJob?.cancel()
        impedanceFallbackJob = null
        val cur = runtime.value
        val nextStage = if (cur.stage == LiveWeighStage.SCANNING || cur.stage == LiveWeighStage.WEIGHING) {
            LiveWeighStage.IDLE
        } else {
            cur.stage
        }
        runtime.value = cur.copy(isScanning = false, stage = nextStage)
    }

    private suspend fun handleScaleEvent(event: LiveScaleEvent) {
        val prefs = prefsRepo.getOnce()
        val packet = event.packet

        val updatedMap = runtime.value.discoveredScales.toMutableMap()
        updatedMap[event.macAddress] = DiscoveredScale(
            macAddress = event.macAddress,
            name = event.deviceName,
            rssi = event.rssi,
            protocol = packet.protocol,
            lastWeightKg = packet.weightKg
        )
        runtime.value = runtime.value.copy(discoveredScales = updatedMap)

        var activeMac = prefs.scaleMacAddress
        if (activeMac.isNullOrBlank()) {
            prefsRepo.setScaleDevice(event.macAddress, event.deviceName)
            activeMac = event.macAddress
            bleController.syncBackgroundScan()
        }
        if (!activeMac.equals(event.macAddress, ignoreCase = true)) {
            return
        }

        val currentStage = runtime.value.stage
        if (packet.isRemoved) {
            if (currentStage == LiveWeighStage.WAITING_IMPEDANCE && !savedImpedanceThisSession) {
                // User stepped off before BIA completed; flush stabilized weight immediately
                flushFallbackWeight(event.deviceName ?: prefs.scaleName, event.macAddress)
            }
            return
        }

        lastLivePacket = packet
        val today = LocalDate.now()
        val age = (today.year - prefs.birthYear).coerceIn(10, 100)
        val comp = XiaomiBodyCompositionCalculator.calculate(
            weightKg = packet.weightKg,
            heightCm = prefs.heightCm,
            age = age,
            isMale = prefs.isMale,
            impedanceOhm = packet.impedanceOhm
        )

        if (!packet.isStabilized) {
            // New weigh-in starting on scale
            if (savedImpedanceThisSession || savedWeightThisSession) {
                savedWeightThisSession = false
                savedImpedanceThisSession = false
                impedanceFallbackJob?.cancel()
            }
            runtime.value = runtime.value.copy(
                stage = LiveWeighStage.WEIGHING,
                liveWeightKg = packet.weightKg,
                liveImpedanceOhm = null,
                liveProtocol = packet.protocol,
                liveMacAddress = event.macAddress,
                liveDeviceName = event.deviceName ?: prefs.scaleName,
                liveComposition = comp
            )
            return
        }

        // Stabilized packet
        val latestValidKg = runCatching { bodyWeightDao.getLatestValid()?.kg }.getOrNull()
        val range = bleController.resolveWeightRange(prefs, latestValidKg)
        val inRange = XiaomiScaleBleParser.isWithinUserRange(packet.weightKg, range.first, range.second)

        if (packet.hasImpedance) {
            impedanceFallbackJob?.cancel()
            runtime.value = runtime.value.copy(
                liveWeightKg = packet.weightKg,
                liveImpedanceOhm = packet.impedanceOhm,
                liveProtocol = packet.protocol,
                liveMacAddress = event.macAddress,
                liveDeviceName = event.deviceName ?: prefs.scaleName,
                liveComposition = comp
            )
            if (inRange) {
                if (!savedImpedanceThisSession) {
                    readingsRepository.saveWeighIn(
                        date = today,
                        composition = comp,
                        deviceName = event.deviceName ?: prefs.scaleName,
                        macAddress = event.macAddress
                    )
                    prefsRepo.recordScaleAutoCapture(
                        epochSec = System.currentTimeMillis() / 1000L,
                        hadImpedance = true
                    )
                    savedImpedanceThisSession = true
                    savedWeightThisSession = true
                }
                runtime.value = runtime.value.copy(stage = LiveWeighStage.SAVED)
            } else {
                runtime.value = runtime.value.copy(stage = LiveWeighStage.OUT_OF_RANGE)
            }
            return
        }

        // Stabilized weight-only packet
        if (savedImpedanceThisSession) return

        runtime.value = runtime.value.copy(
            liveWeightKg = packet.weightKg,
            liveProtocol = packet.protocol,
            liveMacAddress = event.macAddress,
            liveDeviceName = event.deviceName ?: prefs.scaleName,
            liveComposition = comp
        )

        if (!inRange) {
            runtime.value = runtime.value.copy(stage = LiveWeighStage.OUT_OF_RANGE)
            return
        }

        if (packet.protocol == ScaleProtocol.BODY_COMPOSITION_181B) {
            if (savedWeightThisSession) {
                runtime.value = runtime.value.copy(stage = LiveWeighStage.SAVED)
                return
            }
            runtime.value = runtime.value.copy(stage = LiveWeighStage.WAITING_IMPEDANCE)
            if (impedanceFallbackJob == null || impedanceFallbackJob?.isActive != true) {
                val devName = event.deviceName ?: prefs.scaleName
                impedanceFallbackJob = viewModelScope.launch {
                    delay(6000L)
                    if (!savedImpedanceThisSession && !savedWeightThisSession) {
                        flushFallbackWeight(devName, event.macAddress)
                    }
                }
            }
        } else {
            if (!savedWeightThisSession) {
                readingsRepository.saveWeighIn(
                    date = today,
                    composition = comp,
                    deviceName = event.deviceName ?: prefs.scaleName,
                    macAddress = event.macAddress
                )
                prefsRepo.recordScaleAutoCapture(
                    epochSec = System.currentTimeMillis() / 1000L,
                    hadImpedance = false
                )
                savedWeightThisSession = true
            }
            runtime.value = runtime.value.copy(stage = LiveWeighStage.SAVED)
        }
    }

    private suspend fun flushFallbackWeight(deviceName: String?, macAddress: String) {
        val comp = runtime.value.liveComposition ?: return
        readingsRepository.saveWeighIn(
            date = LocalDate.now(),
            composition = comp,
            deviceName = deviceName,
            macAddress = macAddress
        )
        prefsRepo.recordScaleAutoCapture(
            epochSec = System.currentTimeMillis() / 1000L,
            hadImpedance = false
        )
        savedWeightThisSession = true
        runtime.value = runtime.value.copy(stage = LiveWeighStage.SAVED)
    }

    fun saveCurrentManually() {
        val rt = runtime.value
        val comp = rt.liveComposition ?: return
        viewModelScope.launch {
            val prefs = prefsRepo.getOnce()
            readingsRepository.saveWeighIn(
                date = LocalDate.now(),
                composition = comp,
                deviceName = rt.liveDeviceName ?: prefs.scaleName,
                macAddress = rt.liveMacAddress ?: prefs.scaleMacAddress
            )
            prefsRepo.recordScaleAutoCapture(
                epochSec = System.currentTimeMillis() / 1000L,
                hadImpedance = comp.impedanceOhm != null
            )
            savedWeightThisSession = true
            if (comp.impedanceOhm != null) savedImpedanceThisSession = true
            runtime.value = runtime.value.copy(stage = LiveWeighStage.SAVED)
        }
    }

    fun bindScale(macAddress: String, name: String?) {
        viewModelScope.launch {
            prefsRepo.setScaleDevice(macAddress, name)
            bleController.syncBackgroundScan()
        }
    }

    fun unbindScale() {
        viewModelScope.launch {
            prefsRepo.clearScaleDevice()
            bleController.stopBackgroundScan()
        }
    }

    fun setBackgroundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setScaleBackgroundEnabled(enabled)
            bleController.syncBackgroundScan()
        }
    }

    fun setWeightRange(minKg: Double?, maxKg: Double?) {
        viewModelScope.launch {
            prefsRepo.setScaleWeightRange(minKg, maxKg)
        }
    }

    fun updateHeightCm(cm: Int) {
        viewModelScope.launch {
            prefsRepo.setHeightCm(cm)
            recalculateLivePreview()
        }
    }

    fun updateBirthYear(year: Int) {
        viewModelScope.launch {
            prefsRepo.setBirthYear(year)
            recalculateLivePreview()
        }
    }

    fun updateIsMale(isMale: Boolean) {
        viewModelScope.launch {
            prefsRepo.setIsMale(isMale)
            recalculateLivePreview()
        }
    }

    private suspend fun recalculateLivePreview() {
        val pkt = lastLivePacket ?: return
        val prefs = prefsRepo.getOnce()
        val age = (LocalDate.now().year - prefs.birthYear).coerceIn(10, 100)
        val recomputed = XiaomiBodyCompositionCalculator.calculate(
            weightKg = pkt.weightKg,
            heightCm = prefs.heightCm,
            age = age,
            isMale = prefs.isMale,
            impedanceOhm = pkt.impedanceOhm
        )
        runtime.value = runtime.value.copy(liveComposition = recomputed)
    }

    override fun onCleared() {
        scanJob?.cancel()
        impedanceFallbackJob?.cancel()
        super.onCleared()
    }
}
