package com.gymtracker.app.ui.stack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.app.R
import com.gymtracker.app.data.db.SupplementCategory
import com.gymtracker.app.data.db.SupplementItemEntity
import com.gymtracker.app.data.db.SupplementScheduleEntity
import com.gymtracker.app.data.db.SupplementTrigger
import com.gymtracker.app.data.db.SupplementUnit
import com.gymtracker.app.domain.CaffeineSleepStatus
import com.gymtracker.app.domain.CyclePhase
import com.gymtracker.app.domain.InteractionKind
import com.gymtracker.app.domain.StackTimeSlot
import com.gymtracker.app.domain.SupplementInteractionEngine
import com.gymtracker.app.ui.common.formatInstantShort
import com.gymtracker.app.ui.common.isUkrainianLocale
import com.gymtracker.app.ui.components.CaffeineDecayChart
import com.gymtracker.app.ui.scale.ScaleBadgePill
import com.gymtracker.app.ui.theme.CardElevated
import com.gymtracker.app.ui.theme.CoralEnergy
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.ObsidianCard
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StackScreen(
    viewModel: StackViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isUk = isUkrainianLocale()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingItem by remember { mutableStateOf<StackChecklistItemUi?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var restockTarget by remember { mutableStateOf<StackChecklistItemUi?>(null) }
    var showBedtimeDialog by remember { mutableStateOf(false) }
    var compatExpanded by remember { mutableStateOf(true) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setRemindersEnabled(granted)
    }

    val undoLabel = stringResource(R.string.stack_undo)
    val loggedPattern = stringResource(R.string.stack_logged_snackbar)
    LaunchedEffect(Unit) {
        viewModel.refreshNow()
        viewModel.events.collect { event ->
            when (event) {
                is StackSnackbarEvent.LoggedWithUndo -> {
                    val name = if (isUk) event.itemNameUk else event.itemNameEn
                    val res = snackbarHostState.showSnackbar(
                        message = String.format(loggedPattern, name),
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        viewModel.undoLog(event.logId)
                    }
                }
                StackSnackbarEvent.Restocked -> Unit
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.isStackEmpty) {
                FloatingActionButton(
                    onClick = {
                        editingItem = null
                        showAddSheet = true
                    },
                    containerColor = ElectricBlue
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.stack_add_manual))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.stack_tab_today), maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.stack_tab_inventory), maxLines = 1) }
                )
            }

            if (selectedTab == 0) {
                StackTodayTab(
                    state = state,
                    isUk = isUk,
                    compatExpanded = compatExpanded,
                    onToggleCompat = { compatExpanded = !compatExpanded },
                    onSeedStarterStack = { viewModel.seedDefaultStack() },
                    onOpenAddManual = {
                        editingItem = null
                        showAddSheet = true
                    },
                    onToggleItem = { viewModel.toggleTakenToday(it) },
                    onQuickCaffeine = { mg, en, uk -> viewModel.logQuickCaffeine(mg, en, uk) },
                    onRestockClick = { restockTarget = it },
                    onOpenBedtimePicker = { showBedtimeDialog = true }
                )
            } else {
                StackInventoryTab(
                    state = state,
                    isUk = isUk,
                    onToggleReminders = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@StackInventoryTab
                            }
                        }
                        viewModel.setRemindersEnabled(enabled)
                    },
                    onOpenBedtimePicker = { showBedtimeDialog = true },
                    onEditItem = {
                        editingItem = it
                        showAddSheet = true
                    },
                    onRestockItem = { restockTarget = it },
                    onDeleteLog = { viewModel.undoLog(it) }
                )
            }
        }
    }

    if (showAddSheet) {
        AddEditSupplementSheet(
            initial = editingItem,
            isUk = isUk,
            onDismiss = {
                showAddSheet = false
                editingItem = null
            },
            onSave = { item, schedule ->
                viewModel.saveSupplement(item, schedule)
                showAddSheet = false
                editingItem = null
            },
            onDelete = editingItem?.let { target ->
                {
                    viewModel.deleteSupplement(target.withSchedules.item.id)
                    showAddSheet = false
                    editingItem = null
                }
            }
        )
    }

    restockTarget?.let { target ->
        var amountStr by remember(target.withSchedules.item.id) { mutableStateOf("30") }
        AlertDialog(
            onDismissRequest = { restockTarget = null },
            title = {
                val nm = if (isUk) target.withSchedules.item.nameUk else target.withSchedules.item.nameEn
                Text(stringResource(R.string.stack_restock_dialog_title, nm))
            },
            text = {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(stringResource(R.string.stack_restock_amount_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val add = amountStr.replace(',', '.').toDoubleOrNull() ?: 30.0
                        if (add > 0.0) viewModel.restockSupplement(target.withSchedules.item.id, add)
                        restockTarget = null
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { restockTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showBedtimeDialog) {
        val currentMin = state.bedtimeMinutesOfDay
        var hourStr by remember(currentMin) {
            val h = currentMin / 60
            mutableStateOf(if (h == 0 && currentMin % 60 == 0) "24" else "%02d".format(h))
        }
        var minStr by remember(currentMin) { mutableStateOf("%02d".format(currentMin % 60)) }

        AlertDialog(
            onDismissRequest = { showBedtimeDialog = false },
            title = { Text(stringResource(R.string.stack_bedtime_label)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hourStr,
                            onValueChange = { hourStr = it.filter(Char::isDigit).take(2) },
                            label = { Text("HH (0-24)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minStr,
                            onValueChange = { minStr = it.filter(Char::isDigit).take(2) },
                            label = { Text("MM") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { hourStr = "23"; minStr = "00" }, label = { Text("23:00") })
                        AssistChip(onClick = { hourStr = "23"; minStr = "30" }, label = { Text("23:30") })
                        AssistChip(onClick = { hourStr = "24"; minStr = "00" }, label = { Text("24:00") })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val hRaw = hourStr.toIntOrNull() ?: 24
                        val h = if (hRaw >= 24) 0 else hRaw.coerceIn(0, 23)
                        val m = (minStr.toIntOrNull() ?: 0).coerceIn(0, 59)
                        viewModel.setBedtimeMinutes(h * 60 + m)
                        showBedtimeDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBedtimeDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StackTodayTab(
    state: StackUiState,
    isUk: Boolean,
    compatExpanded: Boolean,
    onToggleCompat: () -> Unit,
    onSeedStarterStack: () -> Unit,
    onOpenAddManual: () -> Unit,
    onToggleItem: (StackChecklistItemUi) -> Unit,
    onQuickCaffeine: (Int, String, String) -> Unit,
    onRestockClick: (StackChecklistItemUi) -> Unit,
    onOpenBedtimePicker: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.isStackEmpty) {
            item {
                ObsidianCard(highlightedBorder = ElectricBlue, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Medication, contentDescription = null, tint = ElectricBlue)
                            Text(
                                text = stringResource(R.string.stack_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = stringResource(R.string.stack_empty_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onSeedStarterStack,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.stack_seed_cta))
                        }
                        OutlinedButton(
                            onClick = onOpenAddManual,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.stack_add_manual))
                        }
                    }
                }
            }
        } else {
            // Daily Progress & Streak Card
            item {
                val total = state.totalDueTodayCount.coerceAtLeast(1)
                val prog = (state.takenTodayCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                val allDone = state.totalDueTodayCount > 0 && state.takenTodayCount >= state.totalDueTodayCount
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.stack_progress_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            ScaleBadgePill(
                                text = stringResource(
                                    R.string.stack_streak_adherence,
                                    state.streakDays,
                                    state.adherence14dPercent
                                ),
                                color = if (allDone) EmeraldRest else ElectricBlue
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${state.takenTodayCount} / ${state.totalDueTodayCount}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (allDone) EmeraldRest else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        LinearProgressIndicator(
                            progress = { prog },
                            color = if (allDone) EmeraldRest else ElectricBlue,
                            trackColor = CardElevated,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }

        // Low-Stock Banner
        if (state.lowStockItems.isNotEmpty()) {
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
                            Icon(Icons.Default.Warning, contentDescription = null, tint = GoldPr)
                            Text(
                                text = stringResource(R.string.stack_low_stock_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = GoldPr
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.lowStockItems.forEach { itemUi ->
                                val nm = if (isUk) itemUi.withSchedules.item.nameUk else itemUi.withSchedules.item.nameEn
                                val rem = itemUi.withSchedules.item.servingsRemaining?.roundToInt() ?: 0
                                AssistChip(
                                    onClick = { onRestockClick(itemUi) },
                                    label = { Text("$nm ($rem) +", maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Caffeine & Sleep Guard Card
        state.caffeineAnalysis?.let { caf ->
            item {
                val statusColor = when (caf.status) {
                    CaffeineSleepStatus.SAFE -> EmeraldRest
                    CaffeineSleepStatus.MODERATE -> GoldPr
                    CaffeineSleepStatus.HIGH -> CoralEnergy
                }
                val statusLabel = when (caf.status) {
                    CaffeineSleepStatus.SAFE -> stringResource(R.string.stack_caffeine_safe)
                    CaffeineSleepStatus.MODERATE -> stringResource(R.string.stack_caffeine_moderate)
                    CaffeineSleepStatus.HIGH -> stringResource(R.string.stack_caffeine_high)
                }
                val bedLabel = if (caf.bedtimeLocal == LocalTime.MIDNIGHT) "24:00"
                else caf.bedtimeLocal.format(DateTimeFormatter.ofPattern("HH:mm"))

                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                Icon(Icons.Default.Coffee, contentDescription = null, tint = GoldPr)
                                Text(
                                    text = stringResource(R.string.stack_caffeine_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CardElevated,
                                onClick = onOpenBedtimePicker
                            ) {
                                Text(
                                    text = stringResource(R.string.stack_caffeine_bed_target, bedLabel),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricBlue,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.stack_caffeine_now, caf.activeMgNow.roundToInt()),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${stringResource(R.string.stack_caffeine_bed_proj, bedLabel, caf.projectedMgAtBedtime.roundToInt())} • ${stringResource(R.string.stack_caffeine_total_today, caf.totalTodayMg)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                caf.safeSleepInstant?.let { safeAt ->
                                    if (caf.projectedMgAtBedtime > caf.sleepThresholdMg) {
                                        val timeStr = safeAt.atZone(ZoneId.systemDefault()).toLocalTime()
                                            .format(DateTimeFormatter.ofPattern("HH:mm"))
                                        Text(
                                            text = stringResource(R.string.stack_caffeine_safe_from, timeStr),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = GoldPr
                                        )
                                    }
                                }
                            }
                            ScaleBadgePill(text = statusLabel, color = statusColor)
                        }

                        if (caf.activeMgNow > 0.5 || caf.totalTodayMg > 0) {
                            CaffeineDecayChart(
                                analysis = caf,
                                now = Instant.now()
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = { onQuickCaffeine(75, "Espresso", "Еспресо") },
                                label = { Text(stringResource(R.string.stack_quick_espresso), maxLines = 1) }
                            )
                            AssistChip(
                                onClick = { onQuickCaffeine(100, "Coffee", "Кава") },
                                label = { Text(stringResource(R.string.stack_quick_coffee), maxLines = 1) }
                            )
                            AssistChip(
                                onClick = { onQuickCaffeine(160, "Energy Drink", "Енергетик") },
                                label = { Text(stringResource(R.string.stack_quick_energy), maxLines = 1) }
                            )
                        }
                    }
                }
            }
        }

        // Stack Compatibility & Intake Recommendations Card
        if (state.interactions.isNotEmpty()) {
            item {
                val hasConflicts = state.interactions.any { it.kind != InteractionKind.SYNERGY }
                ObsidianCard(
                    highlightedBorder = if (hasConflicts) GoldPr else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onToggleCompat)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                Icon(
                                    imageVector = if (hasConflicts) Icons.Default.Warning else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (hasConflicts) GoldPr else EmeraldRest
                                )
                                Text(
                                    text = stringResource(R.string.stack_compat_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = if (compatExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (compatExpanded) {
                            state.interactions.forEach { inter ->
                                val col = when (inter.kind) {
                                    InteractionKind.SYNERGY -> EmeraldRest
                                    InteractionKind.CAUTION -> GoldPr
                                    InteractionKind.CONFLICT -> CoralEnergy
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = if (isUk) inter.titleUk else inter.titleEn,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = col
                                    )
                                    Text(
                                        text = if (isUk) inter.detailUk else inter.detailEn,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Grouped Daily Checklist Sections
        val orderedSlots = listOf(
            StackTimeSlot.MORNING_MEAL to R.string.stack_slot_morning,
            StackTimeSlot.DAY_EVENING to R.string.stack_slot_day,
            StackTimeSlot.WORKOUT to R.string.stack_slot_workout,
            StackTimeSlot.BEDTIME to R.string.stack_slot_bedtime
        )

        orderedSlots.forEach { (slot, headerRes) ->
            val slotItems = state.dueTodayItems.filter { it.slot == slot }
            if (slotItems.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(headerRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(slotItems, key = { it.withSchedules.item.id }) { ui ->
                    StackChecklistRowCard(
                        itemUi = ui,
                        isUk = isUk,
                        onToggle = { onToggleItem(ui) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StackChecklistRowCard(
    itemUi: StackChecklistItemUi,
    isUk: Boolean,
    onToggle: () -> Unit
) {
    val item = itemUi.withSchedules.item
    val taken = itemUi.takenTodayLogId != null
    val name = if (isUk) item.nameUk else item.nameEn
    val doseStr = "${formatDose(item.defaultDose)} ${unitLabel(item.unit, isUk)}"
    val guide = if (isUk) itemUi.recommendation.timingUk else itemUi.recommendation.timingEn
    val trigLabel = itemUi.withSchedules.schedules.firstOrNull { it.enabled && it.deletedAt == null }?.let { s ->
        val base = stringResource(triggerLabelRes(s.triggerType))
        val time = s.timeOfDay?.format(DateTimeFormatter.ofPattern("HH:mm"))
        if (time != null) "$base • $time" else base
    }

    ObsidianCard(
        containerColor = if (taken) CardElevated.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surface,
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (taken) EmeraldRest else CardElevated),
                contentAlignment = Alignment.Center
            ) {
                if (taken) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (item.category == SupplementCategory.PRE_WORKOUT) Icons.Default.Bolt else Icons.Default.Medication,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (taken) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (taken) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(shape = RoundedCornerShape(8.dp), color = CardElevated) {
                        Text(
                            text = doseStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricBlue,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = listOfNotNull(trigLabel, guide).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!taken && itemUi.liveWarning != null) {
                    ScaleBadgePill(
                        text = if (isUk) itemUi.liveWarning.titleUk else itemUi.liveWarning.titleEn,
                        color = if (itemUi.liveWarning.kind == InteractionKind.CONFLICT) CoralEnergy else GoldPr
                    )
                }
            }

            item.servingsRemaining?.let { rem ->
                Text(
                    text = stringResource(R.string.stack_servings_short, formatDose(rem)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (itemUi.isLowStock) GoldPr else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StackInventoryTab(
    state: StackUiState,
    isUk: Boolean,
    onToggleReminders: (Boolean) -> Unit,
    onOpenBedtimePicker: () -> Unit,
    onEditItem: (StackChecklistItemUi) -> Unit,
    onRestockItem: (StackChecklistItemUi) -> Unit,
    onDeleteLog: (Long) -> Unit
) {
    val bedH = state.bedtimeMinutesOfDay / 60
    val bedM = state.bedtimeMinutesOfDay % 60
    val bedLabel = if (bedH == 0 && bedM == 0) "24:00" else "%02d:%02d".format(bedH, bedM)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Push Reminders & Bedtime Card
        item {
            ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = ElectricBlue)
                            Column {
                                Text(
                                    text = stringResource(R.string.stack_reminders_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.stack_reminders_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(checked = state.remindersEnabled, onCheckedChange = onToggleReminders)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenBedtimePicker)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.stack_bedtime_label), style = MaterialTheme.typography.bodyMedium)
                        Text(bedLabel, style = MaterialTheme.typography.titleSmall, color = ElectricBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(state.inventoryItems, key = { it.withSchedules.item.id }) { ui ->
            val item = ui.withSchedules.item
            val name = if (isUk) item.nameUk else item.nameEn
            val tip = if (isUk) ui.recommendation.tipUk else ui.recommendation.tipEn
            ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$name • ${formatDose(item.defaultDose)} ${unitLabel(item.unit, isUk)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(categoryLabelRes(item.category)) +
                                    ((item.caffeineMgPerServing ?: 0).takeIf { it > 0 }?.let { " • $it mg caffeine" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onEditItem(ui) }) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item.servingsRemaining?.let { rem ->
                            ScaleBadgePill(
                                text = stringResource(
                                    R.string.stack_servings_left,
                                    formatDose(rem),
                                    ui.daysRemaining ?: 0
                                ),
                                color = if (ui.isLowStock) GoldPr else EmeraldRest
                            )
                        }
                        when (val cyc = ui.cyclePhase) {
                            is CyclePhase.On -> ScaleBadgePill(
                                text = stringResource(R.string.stack_cycle_on, cyc.dayIndex, cyc.totalDays),
                                color = ElectricBlue
                            )
                            is CyclePhase.Off -> ScaleBadgePill(
                                text = stringResource(R.string.stack_cycle_off, cyc.dayIndex, cyc.totalDays),
                                color = GoldPr
                            )
                            CyclePhase.None -> Unit
                        }
                    }

                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { onRestockItem(ui) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("+ ${stringResource(R.string.stack_restock_btn)}")
                        }
                    }
                }
            }
        }

        // Recent Intake Logs
        item {
            Text(
                text = stringResource(R.string.stack_history_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (state.recentLogs.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.stack_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.recentLogs, key = { it.log.id }) { entry ->
                val itemName = entry.item?.let { if (isUk) it.nameUk else it.nameEn }
                    ?: "${entry.log.caffeineMg ?: 0} mg"
                val unitStr = entry.item?.let { unitLabel(it.unit, isUk) } ?: ""
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$itemName • ${formatDose(entry.log.dose)} $unitStr".trim(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatInstantShort(entry.log.takenAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteLog(entry.log.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class StackPresetSpec(
    val nameEn: String,
    val nameUk: String,
    val category: SupplementCategory,
    val dose: Double,
    val unit: SupplementUnit,
    val caffeineMg: Int,
    val servings: Double,
    val threshold: Double,
    val trigger: SupplementTrigger,
    val time: String
)

private val QUICK_PRESETS = listOf(
    StackPresetSpec("Creatine Monohydrate", "Креатин моногідрат", SupplementCategory.CREATINE, 5.0, SupplementUnit.G, 0, 60.0, 10.0, SupplementTrigger.AFTER_WORKOUT, ""),
    StackPresetSpec("Whey Protein", "Протеїн", SupplementCategory.PROTEIN, 1.0, SupplementUnit.SCOOP, 0, 30.0, 5.0, SupplementTrigger.AFTER_WORKOUT, ""),
    StackPresetSpec("Vitamin D3", "Вітамін D3", SupplementCategory.VITAMIN, 4000.0, SupplementUnit.IU, 0, 60.0, 10.0, SupplementTrigger.WITH_MEAL, "09:00"),
    StackPresetSpec("Omega-3", "Омега-3", SupplementCategory.OMEGA_3, 2.0, SupplementUnit.CAPSULE, 0, 60.0, 10.0, SupplementTrigger.WITH_MEAL, "09:00"),
    StackPresetSpec("Zinc", "Цинк", SupplementCategory.MINERAL, 25.0, SupplementUnit.MG, 0, 60.0, 10.0, SupplementTrigger.FIXED_TIME, "14:00"),
    StackPresetSpec("Magnesium", "Магній", SupplementCategory.MINERAL, 400.0, SupplementUnit.MG, 0, 60.0, 10.0, SupplementTrigger.BEDTIME, "23:15"),
    StackPresetSpec("Pre-Workout (Stim-Free)", "Передтрен (без кофеїну)", SupplementCategory.PRE_WORKOUT, 1.0, SupplementUnit.SCOOP, 0, 30.0, 5.0, SupplementTrigger.BEFORE_WORKOUT, ""),
    StackPresetSpec("Pre-Workout (200mg Caf)", "Передтрен (200 мг кофеїну)", SupplementCategory.PRE_WORKOUT, 1.0, SupplementUnit.SCOOP, 200, 30.0, 5.0, SupplementTrigger.BEFORE_WORKOUT, "")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEditSupplementSheet(
    initial: StackChecklistItemUi?,
    isUk: Boolean,
    onDismiss: () -> Unit,
    onSave: (SupplementItemEntity, SupplementScheduleEntity?) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initItem = initial?.withSchedules?.item
    val initSched = initial?.withSchedules?.schedules?.firstOrNull { it.deletedAt == null }

    var nameText by remember { mutableStateOf(initItem?.let { if (isUk) it.nameUk else it.nameEn } ?: "") }
    var category by remember { mutableStateOf(initItem?.category ?: SupplementCategory.OTHER) }
    var doseText by remember { mutableStateOf(initItem?.defaultDose?.let { formatDose(it) } ?: "1") }
    var unit by remember { mutableStateOf(initItem?.unit ?: SupplementUnit.CAPSULE) }
    var caffeineText by remember { mutableStateOf((initItem?.caffeineMgPerServing ?: 0).toString()) }
    var servingsText by remember { mutableStateOf(initItem?.servingsRemaining?.let { formatDose(it) } ?: "30") }
    var lowThreshText by remember { mutableStateOf(initItem?.lowStockThreshold?.let { formatDose(it) } ?: "5") }
    var trigger by remember { mutableStateOf(initSched?.triggerType ?: SupplementTrigger.WITH_MEAL) }
    var timeText by remember {
        mutableStateOf(
            initSched?.timeOfDay?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: when (trigger) {
                SupplementTrigger.WITH_MEAL -> "09:00"
                SupplementTrigger.BEDTIME -> "23:15"
                SupplementTrigger.FIXED_TIME -> "14:00"
                else -> ""
            }
        )
    }
    var cycleOnText by remember { mutableStateOf(initItem?.cycleDaysOn?.toString() ?: "") }
    var cycleOffText by remember { mutableStateOf(initItem?.cycleDaysOff?.toString() ?: "") }

    val previewItem = SupplementItemEntity(
        id = initItem?.id ?: 0L,
        nameEn = nameText.ifBlank { "Custom" },
        nameUk = nameText.ifBlank { "Добавка" },
        category = category,
        defaultDose = doseText.replace(',', '.').toDoubleOrNull() ?: 1.0,
        unit = unit,
        caffeineMgPerServing = caffeineText.toIntOrNull() ?: 0
    )
    val previewRec = SupplementInteractionEngine.recommendationFor(previewItem)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (initItem == null) stringResource(R.string.stack_add_title) else stringResource(R.string.stack_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (initItem == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QUICK_PRESETS.forEach { p ->
                        AssistChip(
                            onClick = {
                                nameText = if (isUk) p.nameUk else p.nameEn
                                category = p.category
                                doseText = formatDose(p.dose)
                                unit = p.unit
                                caffeineText = p.caffeineMg.toString()
                                servingsText = formatDose(p.servings)
                                lowThreshText = formatDose(p.threshold)
                                trigger = p.trigger
                                timeText = p.time
                            },
                            label = { Text(if (isUk) p.nameUk else p.nameEn, maxLines = 1) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text(stringResource(R.string.stack_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.stack_field_category), style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SupplementCategory.entries.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(stringResource(categoryLabelRes(c)), maxLines = 1) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = doseText,
                    onValueChange = { doseText = it },
                    label = { Text(stringResource(R.string.stack_field_dose)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = caffeineText,
                    onValueChange = { caffeineText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.stack_field_caffeine)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(stringResource(R.string.stack_field_unit), style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SupplementUnit.entries.forEach { u ->
                    FilterChip(
                        selected = unit == u,
                        onClick = { unit = u },
                        label = { Text(unitLabel(u, isUk), maxLines = 1) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = servingsText,
                    onValueChange = { servingsText = it },
                    label = { Text(stringResource(R.string.stack_field_servings)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lowThreshText,
                    onValueChange = { lowThreshText = it },
                    label = { Text(stringResource(R.string.stack_field_low_thresh)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(stringResource(R.string.stack_field_trigger), style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SupplementTrigger.entries.forEach { t ->
                    FilterChip(
                        selected = trigger == t,
                        onClick = {
                            trigger = t
                            if (timeText.isBlank()) {
                                timeText = when (t) {
                                    SupplementTrigger.WITH_MEAL -> "09:00"
                                    SupplementTrigger.FIXED_TIME -> "14:00"
                                    SupplementTrigger.BEDTIME -> "23:15"
                                    else -> ""
                                }
                            }
                        },
                        label = { Text(stringResource(triggerLabelRes(t)), maxLines = 1) }
                    )
                }
            }

            if (trigger == SupplementTrigger.FIXED_TIME || trigger == SupplementTrigger.WITH_MEAL || trigger == SupplementTrigger.BEDTIME) {
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text(stringResource(R.string.stack_field_time)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = cycleOnText,
                    onValueChange = { cycleOnText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.stack_field_cycle_on)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cycleOffText,
                    onValueChange = { cycleOffText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.stack_field_cycle_off)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            ObsidianCard(containerColor = CardElevated, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.stack_intake_guide_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isUk) "${previewRec.timingUk}. ${previewRec.tipUk}" else "${previewRec.timingEn}. ${previewRec.tipEn}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(18.dp)) {
                        Text(stringResource(R.string.action_delete), color = CoralEnergy)
                    }
                }
                Button(
                    onClick = {
                        val trimmed = nameText.trim()
                        if (trimmed.isEmpty()) return@Button
                        val parsedTime = timeText.trim().takeIf { it.isNotEmpty() }?.let { raw ->
                            runCatching { LocalTime.parse(raw, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
                        }
                        val cycOn = cycleOnText.toIntOrNull()?.takeIf { it > 0 }
                        val cycOff = cycleOffText.toIntOrNull()?.takeIf { it > 0 }
                        val itemEntity = (initItem ?: SupplementItemEntity(
                            nameEn = trimmed,
                            nameUk = trimmed,
                            category = category,
                            defaultDose = 1.0,
                            unit = unit
                        )).copy(
                            nameEn = if (!isUk || initItem == null) trimmed else initItem.nameEn,
                            nameUk = if (isUk || initItem == null) trimmed else initItem.nameUk,
                            category = category,
                            defaultDose = (doseText.replace(',', '.').toDoubleOrNull() ?: 1.0).coerceAtLeast(0.1),
                            unit = unit,
                            caffeineMgPerServing = caffeineText.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            servingsRemaining = servingsText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0),
                            lowStockThreshold = lowThreshText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0),
                            cycleDaysOn = cycOn,
                            cycleDaysOff = cycOff,
                            cycleStartDate = if (cycOn != null && cycOff != null) {
                                initItem?.cycleStartDate ?: LocalDate.now()
                            } else null
                        )
                        val schedEntity = SupplementScheduleEntity(
                            supplementId = itemEntity.id,
                            triggerType = trigger,
                            timeOfDay = parsedTime,
                            enabled = true
                        )
                        onSave(itemEntity, schedEntity)
                    },
                    enabled = nameText.isNotBlank(),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

private fun formatDose(v: Double): String =
    if (v % 1.0 == 0.0) v.roundToInt().toString() else String.format("%.1f", v)

private fun unitLabel(unit: SupplementUnit, isUk: Boolean): String = when (unit) {
    SupplementUnit.G -> if (isUk) "г" else "g"
    SupplementUnit.MG -> if (isUk) "мг" else "mg"
    SupplementUnit.MCG -> if (isUk) "мкг" else "mcg"
    SupplementUnit.IU -> if (isUk) "МО" else "IU"
    SupplementUnit.ML -> if (isUk) "мл" else "ml"
    SupplementUnit.CAPSULE -> if (isUk) "капс." else "caps"
    SupplementUnit.SCOOP -> if (isUk) "скуп" else "scoop"
}

@StringRes
private fun categoryLabelRes(cat: SupplementCategory): Int = when (cat) {
    SupplementCategory.PROTEIN -> R.string.supp_cat_protein
    SupplementCategory.CREATINE -> R.string.supp_cat_creatine
    SupplementCategory.PRE_WORKOUT -> R.string.supp_cat_preworkout
    SupplementCategory.OMEGA_3 -> R.string.supp_cat_omega3
    SupplementCategory.VITAMIN -> R.string.supp_cat_vitamin
    SupplementCategory.MINERAL -> R.string.supp_cat_mineral
    SupplementCategory.MEDICATION -> R.string.supp_cat_medication
    SupplementCategory.OTHER -> R.string.supp_cat_other
}

@StringRes
private fun triggerLabelRes(t: SupplementTrigger): Int = when (t) {
    SupplementTrigger.FIXED_TIME -> R.string.supp_trig_fixed
    SupplementTrigger.WITH_MEAL -> R.string.supp_trig_meal
    SupplementTrigger.BEFORE_WORKOUT -> R.string.supp_trig_before_gym
    SupplementTrigger.AFTER_WORKOUT -> R.string.supp_trig_after_gym
    SupplementTrigger.BEDTIME -> R.string.supp_trig_bedtime
}
