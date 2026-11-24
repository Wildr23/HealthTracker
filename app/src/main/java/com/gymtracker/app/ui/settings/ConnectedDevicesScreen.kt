package com.gymtracker.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.app.R
import com.gymtracker.app.ui.common.formatInstantShort
import com.gymtracker.app.ui.scale.ScaleBadgePill
import com.gymtracker.app.ui.theme.CoralEnergy
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.ObsidianCard
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedDevicesScreen(
    onBack: () -> Unit,
    onOpenXiaomiScale: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        viewModel.refreshHc()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDeviceStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    Column {
                        Text(stringResource(R.string.devices_title))
                        Text(
                            text = stringResource(R.string.devices_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: Smartwatch & Health Connect
            item {
                var stepGoalText by remember(state.prefs.dailyStepGoal) {
                    mutableStateOf(state.prefs.dailyStepGoal.toString())
                }

                Text(
                    text = stringResource(R.string.devices_watch_section).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                )
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val hc = state.hcPermState
                        if (hc != null) {
                            DevicePermRow(stringResource(R.string.settings_hc_core), hc.coreReadGranted, supported = true)
                            DevicePermRow(stringResource(R.string.devices_distance_perm), hc.distanceReadGranted, supported = true)
                            DevicePermRow(stringResource(R.string.settings_hc_history), hc.historyGranted, hc.historySupported)
                            DevicePermRow(stringResource(R.string.settings_hc_bg), hc.backgroundGranted, hc.backgroundSupported)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val req = viewModel.permissionsToRequest()
                                    if (req.isNotEmpty()) permLauncher.launch(req)
                                },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.today_grant_hc), maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { viewModel.scanOrigins() },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.settings_discover_sources), maxLines = 1)
                            }
                        }

                        Text(
                            text = stringResource(
                                R.string.settings_watch_source_label,
                                state.prefs.watchSourcePackage ?: "Auto"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricBlue
                        )
                        state.discoveredOrigins.forEach { orig ->
                            FilterChip(
                                selected = state.prefs.watchSourcePackage == orig.packageName,
                                onClick = { viewModel.selectOrigin(orig.packageName) },
                                leadingIcon = { Icon(Icons.Default.Watch, contentDescription = null) },
                                label = {
                                    Text("${orig.packageName} (${orig.sessionCount}s / ${orig.hrRecordCount}hr)")
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                        OutlinedTextField(
                            value = stepGoalText,
                            onValueChange = { v ->
                                stepGoalText = v
                                v.toIntOrNull()?.takeIf { it in 1000..50000 }?.let(viewModel::setDailyStepGoal)
                            },
                            label = { Text(stringResource(R.string.devices_step_goal)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Section 2: Xiaomi Smart Scale (XMTZC05HM) + Battery Unrestricted Guard
            item {
                Text(
                    text = stringResource(R.string.devices_scale_section).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                )
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val boundMac = state.prefs.scaleMacAddress
                        Text(
                            text = if (boundMac != null) {
                                stringResource(
                                    R.string.scale_bound_active,
                                    state.prefs.scaleName ?: "Xiaomi Scale",
                                    boundMac
                                )
                            } else {
                                stringResource(R.string.scale_not_bound)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (boundMac != null) EmeraldRest else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ScaleBadgePill(
                                text = stringResource(
                                    R.string.scale_range_pill,
                                    state.scaleWeightRange.first,
                                    state.scaleWeightRange.second
                                ),
                                color = ElectricBlue
                            )
                            ScaleBadgePill(
                                text = if (state.isIgnoringBatteryOpt) {
                                    stringResource(R.string.scale_battery_ok)
                                } else {
                                    stringResource(R.string.scale_battery_warn_chip)
                                },
                                color = if (state.isIgnoringBatteryOpt) EmeraldRest else GoldPr
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = stringResource(R.string.scale_bg_toggle_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.scale_bg_toggle_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.prefs.scaleBackgroundEnabled,
                                onCheckedChange = viewModel::setScaleBackgroundEnabled
                            )
                        }

                        if (!state.isIgnoringBatteryOpt) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = GoldPr)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.scale_battery_warn_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = GoldPr,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.scale_battery_warn_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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

                        if (state.prefs.scaleLastWakeEpochSec > 0L || state.prefs.scaleIgnoredFamilyCount > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            if (state.prefs.scaleLastWakeEpochSec > 0L) {
                                Text(
                                    text = stringResource(
                                        R.string.devices_last_wake,
                                        formatInstantShort(Instant.ofEpochSecond(state.prefs.scaleLastWakeEpochSec))
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (state.prefs.scaleIgnoredFamilyCount > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.devices_ignored_family,
                                        state.prefs.scaleIgnoredFamilyCount
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onOpenXiaomiScale,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.devices_open_scale_screen))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicePermRow(label: String, granted: Boolean, supported: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        val status = when {
            !supported -> stringResource(R.string.settings_unsupported)
            granted -> stringResource(R.string.settings_connected)
            else -> stringResource(R.string.settings_not_granted)
        }
        Text(
            text = status,
            fontWeight = FontWeight.SemiBold,
            color = if (granted) EmeraldRest else CoralEnergy
        )
    }
}
