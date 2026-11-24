package com.gymtracker.app.ui.health

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.app.R
import com.gymtracker.app.domain.PrEngine
import com.gymtracker.app.ui.common.formatDateShort
import com.gymtracker.app.ui.components.BodyWeightChart
import com.gymtracker.app.ui.scale.ScaleBadgePill
import com.gymtracker.app.ui.scale.ScaleCompositionDashboardCard
import com.gymtracker.app.ui.scale.toDisplay
import com.gymtracker.app.ui.theme.CoralEnergy
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.ObsidianCard
import com.gymtracker.app.ui.theme.TealAccent

@Composable
fun HealthScreen(
    onOpenXiaomiScale: () -> Unit = {},
    viewModel: HealthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMsg = stringResource(R.string.health_stats_saved)

    LaunchedEffect(state.savedBannerVisible) {
        if (state.savedBannerVisible) {
            snackbarHostState.showSnackbar(savedMsg)
            viewModel.clearSavedBanner()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.health_tab_body_stats), maxLines = 1) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.health_tab_weight_chart), maxLines = 1) }
            )
        }

        SnackbarHost(snackbarHostState)

        when (selectedTab) {
            0 -> BodyStatsFormTab(
                state = state,
                onOpenXiaomiScale = onOpenXiaomiScale,
                onSave = viewModel::saveBodyStats
            )
            1 -> WeightLogTab(
                state = state,
                onOpenXiaomiScale = onOpenXiaomiScale,
                onAddWeight = viewModel::addBodyWeight,
                onToggleOutlier = viewModel::toggleOutlier
            )
        }
    }
}

@Composable
private fun BodyStatsFormTab(
    state: HealthUiState,
    onOpenXiaomiScale: () -> Unit,
    onSave: (
        kg: Double,
        fat: Double?,
        waist: Double?,
        chest: Double?,
        arms: Double?,
        forearms: Double?,
        shoulders: Double?,
        hips: Double?,
        thighs: Double?,
        calves: Double?,
        neck: Double?,
        heightCm: Int
    ) -> Unit
) {
    val latest = state.latestStats
    var weight by remember(latest?.kg) { mutableStateOf(latest?.kg?.let { PrEngine.formatKg(it) } ?: "105") }
    var fat by remember(latest?.fatPercent) { mutableStateOf(latest?.fatPercent?.let { PrEngine.formatKg(it) }.orEmpty()) }
    var waist by remember(latest?.waistCm) { mutableStateOf(latest?.waistCm?.let { PrEngine.formatKg(it) }.orEmpty()) }
    var chest by remember(latest?.chestCm) { mutableStateOf(latest?.chestCm?.let { PrEngine.formatKg(it) }.orEmpty()) }
    var arms by remember(latest?.armsCm) { mutableStateOf(latest?.armsCm?.let { PrEngine.formatKg(it) }.orEmpty()) }
    var forearms by remember(latest?.forearmsCm) { mutableStateOf(latest?.forearmsCm?.let { PrEngine.formatKg(it) }.orEmpty()) }
    var shoulders by remember(latest?.shouldersCm) { mutableStateOf(latest?.shouldersCm?.let { PrEngine.formatKg(it) }.orEmpty()) }
    var hips by remember(latest?.hipsCm) { mutableStateOf(latest?.hipsCm?.let { PrEngine.formatKg(it) }.orEmpty()) }
    var thighs by remember(latest?.thighsCm) { mutableStateOf(latest?.thighsCm?.let { PrEngine.formatKg(it) }.orEmpty()) }
    var calves by remember(latest?.calvesCm) { mutableStateOf(latest?.calvesCm?.let { PrEngine.formatKg(it) }.orEmpty()) }
    var neck by remember(latest?.neckCm) { mutableStateOf(latest?.neckCm?.let { PrEngine.formatKg(it) }.orEmpty()) }
    var height by remember(state.heightCm) { mutableStateOf(state.heightCm.toString()) }

    fun d(s: String): Double? = s.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            XiaomiScaleQuickCard(
                boundName = state.scaleBoundName,
                boundMac = state.scaleBoundMac,
                backgroundEnabled = state.scaleBackgroundEnabled,
                weightRange = state.scaleWeightRange,
                onOpenXiaomiScale = onOpenXiaomiScale
            )
        }
        item {
            ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val cm = stringResource(R.string.unit_cm)
                    StatInputRow(Icons.Default.MonitorWeight, stringResource(R.string.stat_weight), weight, { weight = it }, stringResource(R.string.unit_kg))
                    StatInputRow(Icons.Default.Percent, stringResource(R.string.stat_body_fat), fat, { fat = it }, "%")
                    StatInputRow(Icons.Default.Straighten, stringResource(R.string.stat_waist), waist, { waist = it }, cm)
                    StatInputRow(Icons.Default.AccessibilityNew, stringResource(R.string.stat_chest), chest, { chest = it }, cm)
                    StatInputRow(Icons.Default.FitnessCenter, stringResource(R.string.stat_arms), arms, { arms = it }, cm)
                    StatInputRow(Icons.Default.Straighten, stringResource(R.string.stat_forearms), forearms, { forearms = it }, cm)
                    StatInputRow(Icons.Default.AccessibilityNew, stringResource(R.string.stat_shoulders), shoulders, { shoulders = it }, cm)
                    StatInputRow(Icons.Default.Straighten, stringResource(R.string.stat_hips), hips, { hips = it }, cm)
                    StatInputRow(Icons.Default.Straighten, stringResource(R.string.stat_upper_leg), thighs, { thighs = it }, cm)
                    StatInputRow(Icons.Default.Straighten, stringResource(R.string.stat_lower_leg), calves, { calves = it }, cm)
                    StatInputRow(Icons.Default.Straighten, stringResource(R.string.stat_neck), neck, { neck = it }, cm)
                    StatInputRow(Icons.Default.Height, stringResource(R.string.stat_height), height, { height = it }, cm)
                }
            }
        }
        item {
            Button(
                onClick = {
                    val w = d(weight) ?: latest?.kg ?: 105.0
                    val h = height.toIntOrNull() ?: state.heightCm
                    onSave(w, d(fat), d(waist), d(chest), d(arms), d(forearms), d(shoulders), d(hips), d(thighs), d(calves), d(neck), h)
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
            }
        }
        item {
            ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.LocalHospital, contentDescription = null, tint = TealAccent)
                    Text(stringResource(R.string.health_medical_preview_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.health_medical_preview_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatInputRow(
    icon: ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("--", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )
    }
}

@Composable
private fun WeightLogTab(
    state: HealthUiState,
    onOpenXiaomiScale: () -> Unit,
    onAddWeight: (Double) -> Unit,
    onToggleOutlier: (com.gymtracker.app.data.db.BodyWeightEntity) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            XiaomiScaleQuickCard(
                boundName = state.scaleBoundName,
                boundMac = state.scaleBoundMac,
                backgroundEnabled = state.scaleBackgroundEnabled,
                weightRange = state.scaleWeightRange,
                onOpenXiaomiScale = onOpenXiaomiScale
            )
        }

        state.latestScaleReading?.let { snapshot ->
            item {
                ScaleCompositionDashboardCard(
                    data = snapshot.toDisplay(),
                    heightCm = state.heightCm,
                    birthYear = state.birthYear,
                    isMale = state.isMale
                )
            }
        }

        item {
            ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.health_bw_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { showAddDialog = true }, shape = RoundedCornerShape(20.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.health_bw_add))
                        }
                    }
                    if (state.bodyWeights.isNotEmpty()) {
                        BodyWeightChart(entries = state.bodyWeights)
                    }
                }
            }
        }

        items(state.bodyWeights, key = { it.id }) { bw ->
            val scaleSnap = state.scaleReadingsByDate[bw.date]
            ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${bw.kg} ${stringResource(R.string.unit_kg)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatDateShort(bw.date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (scaleSnap?.fatPercent != null && scaleSnap.muscleMassKg != null && scaleSnap.waterPercent != null) {
                            Text(
                                text = stringResource(
                                    R.string.scale_log_comp_line,
                                    scaleSnap.fatPercent,
                                    scaleSnap.muscleMassKg,
                                    scaleSnap.waterPercent
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricBlue
                            )
                        } else if (bw.fatPercent != null) {
                            Text(
                                text = stringResource(R.string.scale_log_fat_only, bw.fatPercent),
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricBlue
                            )
                        }
                    }
                    FilterChip(
                        selected = bw.flaggedOutlier,
                        onClick = { onToggleOutlier(bw) },
                        leadingIcon = if (bw.flaggedOutlier) {
                            { Icon(Icons.Default.Warning, contentDescription = null, tint = CoralEnergy) }
                        } else null,
                        label = { Text(stringResource(R.string.health_bw_outlier)) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.health_bw_add)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.unit_kg)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 20.0 }?.let(onAddWeight)
                    showAddDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XiaomiScaleQuickCard(
    boundName: String?,
    boundMac: String?,
    backgroundEnabled: Boolean,
    weightRange: Pair<Double, Double>,
    onOpenXiaomiScale: () -> Unit
) {
    ObsidianCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = ElectricBlue,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.scale_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ScaleBadgePill(
                        text = stringResource(R.string.scale_range_pill, weightRange.first, weightRange.second),
                        color = ElectricBlue
                    )
                    if (boundMac != null) {
                        ScaleBadgePill(
                            text = if (backgroundEnabled) {
                                stringResource(R.string.scale_bg_on_label)
                            } else {
                                stringResource(R.string.scale_bg_off_label)
                            },
                            color = if (backgroundEnabled) EmeraldRest else CoralEnergy
                        )
                    }
                }
                if (boundMac != null) {
                    Text(
                        text = "${boundName ?: "Xiaomi Scale"} • $boundMac",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onOpenXiaomiScale,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (boundMac != null) {
                        stringResource(R.string.scale_action_weigh_in)
                    } else {
                        stringResource(R.string.scale_action_setup)
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}
