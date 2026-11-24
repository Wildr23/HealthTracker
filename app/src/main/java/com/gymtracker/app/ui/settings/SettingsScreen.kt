package com.gymtracker.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.app.R
import com.gymtracker.app.data.prefs.AppLanguage
import com.gymtracker.app.data.prefs.AppThemeMode
import com.gymtracker.app.ui.common.formatInstantShort
import com.gymtracker.app.ui.scale.ScaleBadgePill
import com.gymtracker.app.ui.theme.CoralEnergy
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.ObsidianCard
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenJefitImport: () -> Unit,
    onOpenConnectedDevices: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) viewModel.exportBackup(uri) }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importBackup(uri) }

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
                title = { Text(stringResource(R.string.settings_title)) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Appearance & Language
            item {
                SectionLabel(stringResource(R.string.settings_appearance))
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                AppThemeMode.DARK to R.string.theme_dark,
                                AppThemeMode.LIGHT to R.string.theme_light,
                                AppThemeMode.SYSTEM to R.string.theme_system
                            ).forEach { (mode, labelRes) ->
                                FilterChip(
                                    selected = state.prefs.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(stringResource(labelRes), maxLines = 1) }
                                )
                            }
                        }
                        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                AppLanguage.SYSTEM to R.string.lang_system,
                                AppLanguage.EN to R.string.lang_en,
                                AppLanguage.UK to R.string.lang_uk
                            ).forEach { (lang, labelRes) ->
                                FilterChip(
                                    selected = state.prefs.language == lang,
                                    onClick = { viewModel.setLanguage(lang) },
                                    label = { Text(stringResource(labelRes), maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            }

            // Connected Devices Hub (Watch HC + Xiaomi Scale BLE)
            item {
                LaunchedEffect(Unit) { viewModel.refreshDeviceStatuses() }
                SectionLabel(stringResource(R.string.devices_title))
                ObsidianCard(
                    onClick = onOpenConnectedDevices,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Watch, contentDescription = null, tint = ElectricBlue)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.devices_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.devices_settings_card_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (state.hcPermState?.coreReadGranted == true) {
                                    ScaleBadgePill(stringResource(R.string.devices_watch_chip_ok), EmeraldRest)
                                } else {
                                    ScaleBadgePill(stringResource(R.string.settings_not_granted), GoldPr)
                                }
                                if (state.prefs.scaleMacAddress != null) {
                                    ScaleBadgePill(stringResource(R.string.devices_scale_chip_ok), ElectricBlue)
                                }
                                if (state.prefs.scaleMacAddress != null && state.prefs.scaleBackgroundEnabled) {
                                    ScaleBadgePill(
                                        text = if (state.isIgnoringBatteryOpt) {
                                            stringResource(R.string.devices_bg_chip_ok)
                                        } else {
                                            stringResource(R.string.scale_battery_warn_chip)
                                        },
                                        color = if (state.isIgnoringBatteryOpt) EmeraldRest else GoldPr
                                    )
                                }
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Physiology & Timers
            item {
                var hrMaxText by remember(state.prefs.hrMax) { mutableStateOf(state.prefs.hrMax.toString()) }
                var secRepText by remember(state.prefs.estSecPerRep) { mutableStateOf(state.prefs.estSecPerRep.toString()) }
                var restText by remember(state.prefs.defaultRestSec) { mutableStateOf(state.prefs.defaultRestSec.toString()) }

                SectionLabel(stringResource(R.string.settings_physiology))
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = hrMaxText,
                                onValueChange = {
                                    hrMaxText = it
                                    it.toIntOrNull()?.let(viewModel::setHrMax)
                                },
                                label = { Text(stringResource(R.string.settings_hrmax)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = secRepText,
                                onValueChange = {
                                    secRepText = it
                                    it.toIntOrNull()?.let(viewModel::setEstSecPerRep)
                                },
                                label = { Text(stringResource(R.string.settings_sec_per_rep)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = restText,
                            onValueChange = {
                                restText = it
                                it.toIntOrNull()?.let(viewModel::setDefaultRestSec)
                            },
                            label = { Text(stringResource(R.string.settings_default_rest)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Data Migration & Backup
            item {
                SectionLabel(stringResource(R.string.settings_data_section))
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOpenJefitImport,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_import_jefit))
                        }
                        OutlinedButton(
                            onClick = { exportLauncher.launch("gym_tracker_backup.json") },
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_export_json))
                        }
                        OutlinedButton(
                            onClick = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_import_json))
                        }
                    }
                }
            }

            // Cloud Database (Supabase PostgreSQL)
            item {
                var urlText by remember(state.prefs.supabaseUrl) { mutableStateOf(state.prefs.supabaseUrl) }
                var keyText by remember(state.prefs.supabasePublishableKey) {
                    mutableStateOf(state.prefs.supabasePublishableKey)
                }
                var emailText by remember(state.prefs.supabaseUserEmail) {
                    mutableStateOf(state.prefs.supabaseUserEmail.orEmpty())
                }
                var passwordText by remember { mutableStateOf("") }
                var showServerFields by remember {
                    mutableStateOf(
                        state.prefs.supabaseUrl.isBlank() || state.prefs.supabasePublishableKey.isBlank()
                    )
                }

                val isSignedIn = !state.prefs.supabaseUserId.isNullOrBlank()

                SectionLabel(stringResource(R.string.cloud_section_title))
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.isSyncingCloud) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isSignedIn) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = if (isSignedIn) EmeraldRest else ElectricBlue
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSignedIn) {
                                        stringResource(
                                            R.string.cloud_status_connected,
                                            state.prefs.supabaseUserEmail ?: state.prefs.supabaseUserId!!
                                        )
                                    } else {
                                        stringResource(R.string.cloud_status_disconnected)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSignedIn) EmeraldRest else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (state.prefs.supabaseLastSyncEpochSec > 0L) {
                                        stringResource(
                                            R.string.cloud_last_sync,
                                            formatInstantShort(Instant.ofEpochSecond(state.prefs.supabaseLastSyncEpochSec))
                                        )
                                    } else {
                                        stringResource(R.string.cloud_last_sync_never)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { showServerFields = !showServerFields }) {
                                Text(if (showServerFields) "Hide" else "URL/Key")
                            }
                        }

                        if (showServerFields) {
                            OutlinedTextField(
                                value = urlText,
                                onValueChange = { urlText = it },
                                label = { Text(stringResource(R.string.cloud_url_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = keyText,
                                onValueChange = { keyText = it },
                                label = { Text(stringResource(R.string.cloud_key_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                onClick = { viewModel.saveSupabaseConfig(urlText, keyText) },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.cloud_save_config))
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                        if (!isSignedIn) {
                            OutlinedTextField(
                                value = emailText,
                                onValueChange = { emailText = it },
                                label = { Text(stringResource(R.string.cloud_email_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = passwordText,
                                onValueChange = { passwordText = it },
                                label = { Text(stringResource(R.string.cloud_password_label)) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.signInSupabase(emailText, passwordText) },
                                    enabled = !state.isSyncingCloud && emailText.isNotBlank() && passwordText.length >= 6,
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.cloud_sign_in), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.signUpSupabase(emailText, passwordText) },
                                    enabled = !state.isSyncingCloud && emailText.isNotBlank() && passwordText.length >= 6,
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.cloud_sign_up), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                    Text(
                                        text = stringResource(R.string.cloud_auto_sync),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.cloud_auto_sync_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.prefs.supabaseAutoSyncEnabled,
                                    onCheckedChange = viewModel::setCloudAutoSync
                                )
                            }
                            Button(
                                onClick = viewModel::syncCloudNow,
                                enabled = !state.isSyncingCloud,
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (state.isSyncingCloud) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CloudSync, contentDescription = null)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (state.isSyncingCloud) {
                                        stringResource(R.string.cloud_syncing)
                                    } else {
                                        stringResource(R.string.cloud_sync_now)
                                    }
                                )
                            }
                            OutlinedButton(
                                onClick = viewModel::restoreCloudNow,
                                enabled = !state.isSyncingCloud,
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.cloud_restore_now))
                            }
                            TextButton(
                                onClick = viewModel::signOutSupabase,
                                enabled = !state.isSyncingCloud,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.cloud_sign_out))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
    )
}
