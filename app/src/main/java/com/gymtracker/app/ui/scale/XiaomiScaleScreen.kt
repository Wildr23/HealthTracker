package com.gymtracker.app.ui.scale

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.app.R
import com.gymtracker.app.data.scale.ScaleProtocol
import com.gymtracker.app.data.scale.ScaleReadingSnapshot
import com.gymtracker.app.domain.BodyCompositionMetric
import com.gymtracker.app.domain.MetricStatus
import com.gymtracker.app.domain.PrEngine
import com.gymtracker.app.domain.ScaleBodyComposition
import com.gymtracker.app.domain.ScaleBodyType
import com.gymtracker.app.domain.XiaomiBodyCompositionCalculator
import com.gymtracker.app.ui.common.formatInstantShort
import com.gymtracker.app.ui.theme.CoralEnergy
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.ObsidianCard
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

data class ScaleCompositionDisplay(
    val capturedAt: Instant,
    val weightKg: Double,
    val impedanceOhm: Int?,
    val bmi: Double,
    val idealWeightKg: Double,
    val bmrKcal: Int,
    val visceralFat: Int,
    val fatPercent: Double?,
    val leanBodyMassKg: Double?,
    val waterPercent: Double?,
    val muscleMassKg: Double?,
    val boneMassKg: Double?,
    val proteinPercent: Double?,
    val metabolicAge: Int?,
    val bodyType: ScaleBodyType?
)

fun ScaleReadingSnapshot.toDisplay(): ScaleCompositionDisplay = ScaleCompositionDisplay(
    capturedAt = Instant.ofEpochSecond(capturedAtEpochSec),
    weightKg = weightKg,
    impedanceOhm = impedanceOhm,
    bmi = bmi,
    idealWeightKg = idealWeightKg,
    bmrKcal = bmrKcal,
    visceralFat = visceralFat,
    fatPercent = fatPercent,
    leanBodyMassKg = leanBodyMassKg,
    waterPercent = waterPercent,
    muscleMassKg = muscleMassKg,
    boneMassKg = boneMassKg,
    proteinPercent = proteinPercent,
    metabolicAge = metabolicAge,
    bodyType = bodyTypeOrNull()
)

fun ScaleBodyComposition.toDisplay(capturedAt: Instant = Instant.now()): ScaleCompositionDisplay =
    ScaleCompositionDisplay(
        capturedAt = capturedAt,
        weightKg = weightKg,
        impedanceOhm = impedanceOhm,
        bmi = bmi,
        idealWeightKg = idealWeightKg,
        bmrKcal = bmrKcal,
        visceralFat = visceralFat,
        fatPercent = fatPercent,
        leanBodyMassKg = leanBodyMassKg,
        waterPercent = waterPercent,
        muscleMassKg = muscleMassKg,
        boneMassKg = boneMassKg,
        proteinPercent = proteinPercent,
        metabolicAge = metabolicAge,
        bodyType = bodyType
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun XiaomiScaleScreen(
    onBack: () -> Unit,
    viewModel: XiaomiScaleViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshStatus(autoStartScan = true)
    }

    val btEnableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshStatus(autoStartScan = true)
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus(autoStartScan = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            viewModel.stopLiveScan()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                title = {
                    Column {
                        Text(stringResource(R.string.scale_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = if (state.prefs.scaleMacAddress != null) {
                                stringResource(
                                    R.string.scale_subtitle_bound,
                                    state.prefs.scaleName ?: "Xiaomi Scale",
                                    state.prefs.scaleMacAddress!!
                                )
                            } else {
                                stringResource(R.string.scale_subtitle_unbound)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.hasBlePermissions) {
                item {
                    ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = ElectricBlue)
                                Text(
                                    text = stringResource(R.string.scale_perm_required_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = stringResource(R.string.scale_perm_required_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    blePermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_SCAN,
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.scale_grant_ble_perm), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else if (!state.isBluetoothEnabled) {
                item {
                    ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.BluetoothDisabled, contentDescription = null, tint = CoralEnergy)
                                Text(
                                    text = stringResource(R.string.scale_bt_off_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = stringResource(R.string.scale_bt_off_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.scale_enable_bt), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            if (state.prefs.scaleBackgroundEnabled && !state.isIgnoringBatteryOptimizations) {
                item {
                    ObsidianCard(highlightedBorder = GoldPr, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = GoldPr)
                                Text(
                                    text = stringResource(R.string.scale_battery_warn_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = GoldPr,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = stringResource(R.string.scale_battery_warn_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    runCatching { context.startActivity(intent) }
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.scale_battery_action))
                            }
                        }
                    }
                }
            }

            // Live Weigh-In Hero Card
            item {
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                when (state.stage) {
                                    LiveWeighStage.SCANNING,
                                    LiveWeighStage.WEIGHING,
                                    LiveWeighStage.WAITING_IMPEDANCE -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = ElectricBlue
                                        )
                                    }
                                    LiveWeighStage.SAVED -> {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldRest)
                                    }
                                    LiveWeighStage.OUT_OF_RANGE -> {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = CoralEnergy)
                                    }
                                    LiveWeighStage.IDLE -> {
                                        Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                val stageLabel = when (state.stage) {
                                    LiveWeighStage.IDLE -> stringResource(R.string.scale_stage_idle)
                                    LiveWeighStage.SCANNING -> stringResource(R.string.scale_stage_scanning)
                                    LiveWeighStage.WEIGHING -> stringResource(R.string.scale_stage_weighing)
                                    LiveWeighStage.WAITING_IMPEDANCE -> stringResource(R.string.scale_stage_measuring_imp)
                                    LiveWeighStage.SAVED -> stringResource(R.string.scale_stage_saved)
                                    LiveWeighStage.OUT_OF_RANGE -> stringResource(
                                        R.string.scale_stage_out_of_range,
                                        state.resolvedRange.first,
                                        state.resolvedRange.second
                                    )
                                }
                                Text(
                                    text = stageLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = when (state.stage) {
                                        LiveWeighStage.SAVED -> EmeraldRest
                                        LiveWeighStage.OUT_OF_RANGE -> CoralEnergy
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (state.hasBlePermissions && state.isBluetoothEnabled) {
                                OutlinedButton(
                                    onClick = {
                                        if (state.isScanning) viewModel.stopLiveScan() else viewModel.startLiveScan()
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (state.isScanning) {
                                            stringResource(R.string.scale_action_stop_scan)
                                        } else {
                                            stringResource(R.string.scale_action_start_scan)
                                        },
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        val shownWeight = state.liveWeightKg ?: state.latestSavedReading?.weightKg
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = shownWeight?.let { PrEngine.formatKg(it) } ?: "--",
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (state.stage) {
                                    LiveWeighStage.SAVED -> EmeraldRest
                                    LiveWeighStage.OUT_OF_RANGE -> CoralEnergy
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = stringResource(R.string.unit_kg),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        val protoTag = when (state.liveProtocol) {
                            ScaleProtocol.BODY_COMPOSITION_181B -> "0x181B"
                            ScaleProtocol.WEIGHT_SCALE_181D -> "0x181D"
                            null -> null
                        }
                        if (state.liveMacAddress != null || state.liveImpedanceOhm != null) {
                            Text(
                                text = buildList {
                                    state.liveDeviceName?.let { add(it) }
                                    state.liveMacAddress?.let { add(it) }
                                    protoTag?.let { add(it) }
                                    state.liveImpedanceOhm?.let { add("$it ${stringResource(R.string.scale_unit_ohm)}") }
                                }.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (state.stage == LiveWeighStage.OUT_OF_RANGE && state.liveComposition != null) {
                            Button(
                                onClick = viewModel::saveCurrentManually,
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPr, contentColor = Color.Black),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.scale_action_save_anyway), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Full Composition Dashboard (live weigh-in takes precedence over latest saved)
            val displayComp = state.liveComposition?.toDisplay()
                ?: state.latestSavedReading?.toDisplay()
            if (displayComp != null) {
                item {
                    ScaleCompositionDashboardCard(
                        data = displayComp,
                        heightCm = state.prefs.heightCm,
                        birthYear = state.prefs.birthYear,
                        isMale = state.prefs.isMale
                    )
                }
            }

            // Paired Scale & Discovered Devices
            item {
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.scale_device_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val boundMac = state.prefs.scaleMacAddress
                        if (boundMac != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.scale_bound_active,
                                        state.prefs.scaleName ?: "Xiaomi Scale",
                                        boundMac
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EmeraldRest,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = viewModel::unbindScale) {
                                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.scale_action_unbind))
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.scale_not_bound),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (state.discoveredScales.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            Text(
                                text = stringResource(R.string.scale_discovered_scales),
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricBlue
                            )
                            state.discoveredScales.forEach { dev ->
                                val isBound = dev.macAddress.equals(boundMac, ignoreCase = true)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dev.name ?: "Xiaomi Smart Scale",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${dev.macAddress} • ${PrEngine.formatKg(dev.lastWeightKg)} ${stringResource(R.string.unit_kg)} • ${dev.rssi} dBm",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isBound) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldRest)
                                    } else {
                                        OutlinedButton(
                                            onClick = { viewModel.bindScale(dev.macAddress, dev.name) },
                                            shape = RoundedCornerShape(14.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text(stringResource(R.string.scale_action_bind))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Background Capture & Family Weight Range Filter
            item {
                var minText by remember(state.prefs.scaleMinWeightKg) {
                    mutableStateOf(state.prefs.scaleMinWeightKg?.let { PrEngine.formatKg(it) }.orEmpty())
                }
                var maxText by remember(state.prefs.scaleMaxWeightKg) {
                    mutableStateOf(state.prefs.scaleMaxWeightKg?.let { PrEngine.formatKg(it) }.orEmpty())
                }

                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = stringResource(R.string.scale_bg_toggle_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.scale_bg_toggle_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.prefs.scaleBackgroundEnabled,
                                onCheckedChange = viewModel::setBackgroundEnabled
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                        Text(
                            text = stringResource(R.string.scale_range_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.scale_range_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ScaleBadgePill(
                            text = stringResource(
                                R.string.scale_range_pill,
                                state.resolvedRange.first,
                                state.resolvedRange.second
                            ),
                            color = ElectricBlue
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = minText,
                                onValueChange = { minText = it },
                                label = { Text(stringResource(R.string.scale_range_min)) },
                                placeholder = { Text(state.resolvedRange.first.roundToInt().toString()) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxText,
                                onValueChange = { maxText = it },
                                label = { Text(stringResource(R.string.scale_range_max)) },
                                placeholder = { Text(state.resolvedRange.second.roundToInt().toString()) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val lo = minText.replace(',', '.').toDoubleOrNull()
                                    val hi = maxText.replace(',', '.').toDoubleOrNull()
                                    if (lo != null && hi != null) {
                                        viewModel.setWeightRange(lo, hi)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(stringResource(R.string.scale_range_apply))
                            }
                            OutlinedButton(
                                onClick = {
                                    minText = ""
                                    maxText = ""
                                    viewModel.setWeightRange(null, null)
                                },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(stringResource(R.string.scale_range_reset))
                            }
                        }
                    }
                }
            }

            // Biometric Profile Card (for BIA Calculations)
            item {
                var heightText by remember(state.prefs.heightCm) { mutableStateOf(state.prefs.heightCm.toString()) }
                var yearText by remember(state.prefs.birthYear) { mutableStateOf(state.prefs.birthYear.toString()) }

                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.scale_profile_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.scale_profile_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = heightText,
                                onValueChange = { v ->
                                    heightText = v
                                    v.toIntOrNull()?.takeIf { it in 100..240 }?.let(viewModel::updateHeightCm)
                                },
                                label = { Text(stringResource(R.string.scale_profile_height)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = yearText,
                                onValueChange = { v ->
                                    yearText = v
                                    v.toIntOrNull()?.takeIf { it in 1920..2020 }?.let(viewModel::updateBirthYear)
                                },
                                label = { Text(stringResource(R.string.scale_profile_birth_year)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.prefs.isMale,
                                onClick = { viewModel.updateIsMale(true) },
                                label = { Text(stringResource(R.string.scale_gender_male)) }
                            )
                            FilterChip(
                                selected = !state.prefs.isMale,
                                onClick = { viewModel.updateIsMale(false) },
                                label = { Text(stringResource(R.string.scale_gender_female)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScaleCompositionDashboardCard(
    data: ScaleCompositionDisplay,
    heightCm: Int,
    birthYear: Int,
    isMale: Boolean,
    selectedMetric: BodyCompositionMetric? = null,
    onMetricClick: ((BodyCompositionMetric) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val age = (LocalDate.now().year - birthYear).coerceIn(10, 100)
    val dateStr = formatInstantShort(data.capturedAt)
    val kgUnit = stringResource(R.string.unit_kg)
    val kcalUnit = stringResource(R.string.scale_unit_kcal)
    val yrsUnit = stringResource(R.string.scale_unit_years)

    ObsidianCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.MonitorWeight, contentDescription = null, tint = ElectricBlue)
                    Text(
                        text = stringResource(R.string.scale_comp_dashboard_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                data.bodyType?.let { bt ->
                    ScaleBadgePill(
                        text = stringResource(bodyTypeLabelRes(bt)),
                        color = EmeraldRest
                    )
                }
            }

            Text(
                text = if (data.impedanceOhm != null) {
                    stringResource(R.string.scale_comp_impedance_caption, dateStr, data.impedanceOhm)
                } else {
                    stringResource(R.string.scale_comp_no_imp_caption, dateStr)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 2-Column Grid of Metrics
            val cells = buildList {
                add(
                    CompMetricCell(
                        label = stringResource(R.string.scale_metric_bmi),
                        value = String.format("%.1f", data.bmi),
                        status = XiaomiBodyCompositionCalculator.bmiStatus(data.bmi),
                        metric = BodyCompositionMetric.BMI
                    )
                )
                data.fatPercent?.let { fat ->
                    add(
                        CompMetricCell(
                            label = stringResource(R.string.scale_metric_fat),
                            value = String.format("%.1f%%", fat),
                            status = XiaomiBodyCompositionCalculator.fatStatus(fat, isMale, age),
                            metric = BodyCompositionMetric.FAT_PERCENT
                        )
                    )
                }
                data.muscleMassKg?.let { m ->
                    add(
                        CompMetricCell(
                            label = stringResource(R.string.scale_metric_muscle),
                            value = "${PrEngine.formatKg(m)} $kgUnit",
                            metric = BodyCompositionMetric.MUSCLE_MASS_KG
                        )
                    )
                }
                data.leanBodyMassKg?.let { lbm ->
                    add(
                        CompMetricCell(
                            label = stringResource(R.string.scale_metric_lbm),
                            value = "${PrEngine.formatKg(lbm)} $kgUnit",
                            metric = BodyCompositionMetric.LEAN_BODY_MASS_KG
                        )
                    )
                }
                data.waterPercent?.let { w ->
                    add(
                        CompMetricCell(
                            label = stringResource(R.string.scale_metric_water),
                            value = String.format("%.1f%%", w),
                            status = XiaomiBodyCompositionCalculator.waterStatus(w, isMale),
                            metric = BodyCompositionMetric.WATER_PERCENT
                        )
                    )
                }
                data.proteinPercent?.let { p ->
                    add(
                        CompMetricCell(
                            label = stringResource(R.string.scale_metric_protein),
                            value = String.format("%.1f%%", p),
                            status = XiaomiBodyCompositionCalculator.proteinStatus(p),
                            metric = BodyCompositionMetric.PROTEIN_PERCENT
                        )
                    )
                }
                add(
                    CompMetricCell(
                        label = stringResource(R.string.scale_metric_visceral),
                        value = data.visceralFat.toString(),
                        status = XiaomiBodyCompositionCalculator.visceralStatus(data.visceralFat),
                        metric = BodyCompositionMetric.VISCERAL_FAT
                    )
                )
                data.boneMassKg?.let { bone ->
                    add(
                        CompMetricCell(
                            label = stringResource(R.string.scale_metric_bone),
                            value = "${String.format("%.2f", bone)} $kgUnit",
                            metric = BodyCompositionMetric.BONE_MASS_KG
                        )
                    )
                }
                add(
                    CompMetricCell(
                        label = stringResource(R.string.scale_metric_bmr),
                        value = "${data.bmrKcal} $kcalUnit",
                        metric = BodyCompositionMetric.BMR_KCAL
                    )
                )
                data.metabolicAge?.let { ma ->
                    add(
                        CompMetricCell(
                            label = stringResource(R.string.scale_metric_metabolic_age),
                            value = "$ma $yrsUnit",
                            metric = BodyCompositionMetric.METABOLIC_AGE
                        )
                    )
                }
                add(
                    CompMetricCell(
                        label = stringResource(R.string.scale_metric_ideal_weight),
                        value = "${PrEngine.formatKg(data.idealWeightKg)} $kgUnit"
                    )
                )
                add(
                    CompMetricCell(
                        label = stringResource(R.string.scale_profile_height),
                        value = "$heightCm ${stringResource(R.string.unit_cm)}"
                    )
                )
            }

            cells.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { cell ->
                        val isSelected = selectedMetric != null && cell.metric == selectedMetric
                        val clickableMod = if (onMetricClick != null && cell.metric != null) {
                            Modifier.clickable { onMetricClick(cell.metric) }
                        } else Modifier
                        val borderMod = if (isSelected) {
                            Modifier.border(1.dp, ElectricBlue, RoundedCornerShape(12.dp))
                        } else Modifier

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .then(borderMod)
                                .background(
                                    if (isSelected) ElectricBlue.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                                .then(clickableMod)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = cell.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    cell.status?.let { st ->
                                        MetricStatusDotBadge(st)
                                    }
                                }
                                Text(
                                    text = cell.value,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private data class CompMetricCell(
    val label: String,
    val value: String,
    val status: MetricStatus? = null,
    val metric: BodyCompositionMetric? = null
)

@Composable
private fun MetricStatusDotBadge(status: MetricStatus) {
    val (color, labelRes) = when (status) {
        MetricStatus.LOW -> GoldPr to R.string.scale_status_low
        MetricStatus.NORMAL -> ElectricBlue to R.string.scale_status_normal
        MetricStatus.OPTIMAL -> EmeraldRest to R.string.scale_status_optimal
        MetricStatus.HIGH -> CoralEnergy to R.string.scale_status_high
    }
    ScaleBadgePill(text = stringResource(labelRes), color = color)
}

@Composable
fun ScaleBadgePill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@StringRes
fun bodyTypeLabelRes(type: ScaleBodyType): Int = when (type) {
    ScaleBodyType.OBESE -> R.string.scale_body_type_obese
    ScaleBodyType.OVERWEIGHT -> R.string.scale_body_type_overweight
    ScaleBodyType.THICK_SET -> R.string.scale_body_type_thick_set
    ScaleBodyType.LACK_EXERCISE -> R.string.scale_body_type_lack_exercise
    ScaleBodyType.BALANCED -> R.string.scale_body_type_balanced
    ScaleBodyType.BALANCED_MUSCULAR -> R.string.scale_body_type_balanced_muscular
    ScaleBodyType.SKINNY -> R.string.scale_body_type_skinny
    ScaleBodyType.BALANCED_SKINNY -> R.string.scale_body_type_balanced_skinny
    ScaleBodyType.SKINNY_MUSCULAR -> R.string.scale_body_type_skinny_muscular
}
