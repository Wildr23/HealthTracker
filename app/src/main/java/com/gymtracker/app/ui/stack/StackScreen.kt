package com.gymtracker.app.ui.stack

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.app.R
import com.gymtracker.app.data.db.SupplementCategory
import com.gymtracker.app.data.db.SupplementItemEntity
import com.gymtracker.app.data.db.SupplementScheduleEntity
import com.gymtracker.app.data.db.SupplementTrigger
import com.gymtracker.app.data.db.SupplementUnit
import com.gymtracker.app.data.db.SupplementWithSchedules
import com.gymtracker.app.data.repo.SupplementRepository
import com.gymtracker.app.domain.CaffeineSleepStatus
import com.gymtracker.app.domain.CyclePhase
import com.gymtracker.app.domain.InteractionKind
import com.gymtracker.app.domain.StackInteraction
import com.gymtracker.app.domain.StackTimeSlot
import com.gymtracker.app.domain.SupplementInteractionEngine
import com.gymtracker.app.domain.SupplementReferenceEntry
import com.gymtracker.app.ui.common.formatDateShort
import com.gymtracker.app.ui.common.isUkrainianLocale
import com.gymtracker.app.ui.components.CaffeineDecayChart
import com.gymtracker.app.ui.scale.ScaleBadgePill
import com.gymtracker.app.ui.theme.CardElevated
import com.gymtracker.app.ui.theme.CoralEnergy
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.ObsidianCard
import com.gymtracker.app.work.NextReminderInfo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

private data class ScheduleDraft(
    val key: String = UUID.randomUUID().toString(),
    val existingId: Long = 0L,
    val remoteId: String? = null,
    val trigger: SupplementTrigger = SupplementTrigger.WITH_MEAL,
    val timeOfDay: LocalTime? = LocalTime.of(9, 0),
    val daysOfWeekMask: Int = 0b1111111
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StackScreen(
    viewModel: StackViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isUk = isUkrainianLocale()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingWithSchedules by remember { mutableStateOf<SupplementWithSchedules?>(null) }
    var prefillEntry by remember { mutableStateOf<SupplementReferenceEntry?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showCompatGuide by remember { mutableStateOf(false) }
    var restockTarget by remember { mutableStateOf<StackShelfItemUi?>(null) }
    var showBedtimeDialog by remember { mutableStateOf(false) }
    var timeTargetDose by remember { mutableStateOf<StackDoseItemUi?>(null) }
    var timeTargetLog by remember { mutableStateOf<StackLogEntryUi?>(null) }
    var caffeineDialogTarget by remember { mutableStateOf<StackLogEntryUi?>(null) }
    var showCustomCaffeineDialog by remember { mutableStateOf(false) }

    var hasNotifPerm by remember { mutableStateOf(checkNotifPerm(context)) }
    var canExactAlarm by remember { mutableStateOf(checkExactAlarmPerm(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPerm = checkNotifPerm(context)
                canExactAlarm = checkExactAlarmPerm(context)
                viewModel.refreshNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
        if (granted) {
            viewModel.setRemindersEnabled(true)
        } else {
            viewModel.refreshNow()
        }
    }

    val undoLabel = stringResource(R.string.stack_undo)
    val loggedPattern = stringResource(R.string.stack_logged_snackbar)
    val batchLoggedPattern = stringResource(R.string.stack_logged_batch_snackbar)
    val testScheduledMsg = stringResource(R.string.stack_test_push_scheduled)

    LaunchedEffect(Unit) {
        viewModel.refreshNow()
        viewModel.events.collect { event ->
            when (event) {
                is StackSnackbarEvent.LoggedWithUndo -> {
                    val label = if (isUk) event.itemNameUk else event.itemNameEn
                    val msg = if (event.isBatch) {
                        String.format(batchLoggedPattern, event.logIds.size, label)
                    } else {
                        String.format(loggedPattern, label)
                    }
                    val res = snackbarHostState.showSnackbar(
                        message = msg,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        viewModel.undoLogs(event.logIds)
                    }
                }
                StackSnackbarEvent.Restocked -> Unit
                StackSnackbarEvent.TestReminderScheduled -> {
                    snackbarHostState.showSnackbar(
                        message = testScheduledMsg,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    val openNotifPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val openExactAlarmSettings: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            )
            runCatching { context.startActivity(intent) }.onFailure {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.isStackEmpty && selectedTab != 2) {
                FloatingActionButton(
                    onClick = {
                        editingWithSchedules = null
                        prefillEntry = null
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
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.stack_tab_history), maxLines = 1) }
                )
            }

            when (selectedTab) {
                0 -> StackTodayTab(
                    state = state,
                    isUk = isUk,
                    hasNotifPerm = hasNotifPerm,
                    canExactAlarm = canExactAlarm,
                    onRequestNotifPerm = openNotifPermission,
                    onOpenExactAlarmSettings = openExactAlarmSettings,
                    onOpenCompatGuide = { showCompatGuide = true },
                    onSeedStarterStack = { viewModel.seedDefaultStack() },
                    onOpenAddManual = {
                        editingWithSchedules = null
                        prefillEntry = null
                        showAddSheet = true
                    },
                    onToggleDose = { viewModel.toggleDose(it) },
                    onOpenDoseTimePicker = { timeTargetDose = it },
                    onTakeAllSlot = { grp, en, uk -> viewModel.takeAllInSlot(grp, en, uk) },
                    onEditItem = { sw ->
                        editingWithSchedules = sw
                        prefillEntry = null
                        showAddSheet = true
                    },
                    onQuickCaffeine = { mg, en, uk -> viewModel.logQuickCaffeine(mg, en, uk) },
                    onOpenCustomCaffeine = { showCustomCaffeineDialog = true },
                    onEditCaffeineLog = { caffeineDialogTarget = it },
                    onRestockClick = { restockTarget = it },
                    onOpenBedtimePicker = { showBedtimeDialog = true }
                )
                1 -> StackInventoryTab(
                    state = state,
                    isUk = isUk,
                    hasNotifPerm = hasNotifPerm,
                    canExactAlarm = canExactAlarm,
                    onRequestNotifPerm = openNotifPermission,
                    onOpenExactAlarmSettings = openExactAlarmSettings,
                    onToggleReminders = { enabled ->
                        if (enabled && !hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@StackInventoryTab
                        }
                        viewModel.setRemindersEnabled(enabled)
                    },
                    onTestPush = {
                        if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.scheduleTestReminder()
                        }
                    },
                    onOpenBedtimePicker = { showBedtimeDialog = true },
                    onOpenCompatGuide = { showCompatGuide = true },
                    onEditItem = { shelf ->
                        editingWithSchedules = shelf.withSchedules
                        prefillEntry = null
                        showAddSheet = true
                    },
                    onRestockItem = { restockTarget = it }
                )
                else -> StackHistoryTab(
                    state = state,
                    isUk = isUk,
                    onEditLogTime = { entry ->
                        val isCaf = entry.item?.notes == SupplementRepository.QUICK_CAFFEINE_TAG ||
                            (entry.log.caffeineMg ?: 0) > 0
                        if (isCaf) caffeineDialogTarget = entry else timeTargetLog = entry
                    },
                    onDeleteLog = { viewModel.undoLogs(listOf(it)) }
                )
            }
        }
    }

    if (showCompatGuide) {
        CompatibilityReferenceSheet(
            isUk = isUk,
            stackInteractions = state.interactions,
            onDismiss = { showCompatGuide = false },
            onAddPreset = { entry ->
                showCompatGuide = false
                editingWithSchedules = null
                prefillEntry = entry
                showAddSheet = true
            }
        )
    }

    if (showAddSheet) {
        AddEditSupplementSheet(
            initial = editingWithSchedules,
            prefill = prefillEntry,
            existingStack = state.shelfItems.map { it.withSchedules },
            bedtimeMinutesOfDay = state.bedtimeMinutesOfDay,
            isUk = isUk,
            onDismiss = {
                showAddSheet = false
                editingWithSchedules = null
                prefillEntry = null
            },
            onSave = { item, schedules ->
                viewModel.saveSupplement(item, schedules)
                showAddSheet = false
                editingWithSchedules = null
                prefillEntry = null
            },
            onDelete = editingWithSchedules?.let { target ->
                {
                    viewModel.deleteSupplement(target.item.id)
                    showAddSheet = false
                    editingWithSchedules = null
                    prefillEntry = null
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

    timeTargetDose?.let { dose ->
        val zone = ZoneId.systemDefault()
        val existingLogId = dose.takenTodayLogId
        val baseDate = dose.takenAt?.atZone(zone)?.toLocalDate() ?: LocalDate.now(zone)
        val initTime = dose.takenAt?.atZone(zone)?.toLocalTime() ?: LocalTime.now(zone)
        val schedTime = dose.schedule?.timeOfDay
        val name = if (isUk) dose.item.nameUk else dose.item.nameEn
        IntakeTimePickerDialog(
            title = "$name • ${stringResource(R.string.stack_time_dialog_title)}",
            baseDate = baseDate,
            initialTime = initTime,
            scheduledTime = schedTime,
            onDismiss = { timeTargetDose = null },
            onConfirm = { newInstant ->
                if (existingLogId != null) {
                    viewModel.updateLogTime(existingLogId, newInstant)
                } else {
                    viewModel.logDoseAtTime(dose, newInstant)
                }
                timeTargetDose = null
            }
        )
    }

    timeTargetLog?.let { entry ->
        val zone = ZoneId.systemDefault()
        val zdt = entry.log.takenAt.atZone(zone)
        val name = entry.item?.let { if (isUk) it.nameUk else it.nameEn }
            ?: stringResource(R.string.stack_time_dialog_title)
        IntakeTimePickerDialog(
            title = "$name • ${stringResource(R.string.stack_time_dialog_title)}",
            baseDate = zdt.toLocalDate(),
            initialTime = zdt.toLocalTime(),
            scheduledTime = null,
            onDismiss = { timeTargetLog = null },
            onConfirm = { newInstant ->
                viewModel.updateLogTime(entry.log.id, newInstant)
                timeTargetLog = null
            }
        )
    }

    if (showCustomCaffeineDialog) {
        val zone = ZoneId.systemDefault()
        CaffeineLogTimeDialog(
            title = stringResource(R.string.stack_caffeine_dialog_title),
            baseDate = LocalDate.now(zone),
            initialTime = LocalTime.now(zone),
            initialMg = 100,
            onDismiss = { showCustomCaffeineDialog = false },
            onConfirm = { mg, instant ->
                viewModel.logQuickCaffeine(mg, "Coffee / Caffeine", "Кава / Кофеїн", instant)
                showCustomCaffeineDialog = false
            }
        )
    }

    caffeineDialogTarget?.let { entry ->
        val zone = ZoneId.systemDefault()
        val zdt = entry.log.takenAt.atZone(zone)
        val initMg = entry.log.caffeineMg ?: entry.item?.caffeineMgPerServing ?: 100
        CaffeineLogTimeDialog(
            title = stringResource(R.string.stack_caffeine_edit_title),
            baseDate = zdt.toLocalDate(),
            initialTime = zdt.toLocalTime(),
            initialMg = initMg,
            onDismiss = { caffeineDialogTarget = null },
            onDelete = {
                viewModel.undoLogs(listOf(entry.log.id))
                caffeineDialogTarget = null
            },
            onConfirm = { mg, newInstant ->
                viewModel.updateLogTime(entry.log.id, newInstant, caffeineMgOverride = mg)
                caffeineDialogTarget = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun IntakeTimePickerDialog(
    title: String,
    baseDate: LocalDate,
    initialTime: LocalTime,
    scheduledTime: LocalTime?,
    onDismiss: () -> Unit,
    onConfirm: (Instant) -> Unit
) {
    val zone = ZoneId.systemDefault()
    var selectedTime by remember { mutableStateOf(initialTime) }
    var pickerRev by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = {
                            selectedTime = LocalTime.now(zone)
                            pickerRev++
                        },
                        label = { Text(stringResource(R.string.stack_time_now), maxLines = 1) }
                    )
                    listOf(30L to "-30м", 60L to "-1г", 120L to "-2г").forEach { (mins, label) ->
                        AssistChip(
                            onClick = {
                                selectedTime = LocalTime.now(zone).minusMinutes(mins)
                                pickerRev++
                            },
                            label = { Text(label, maxLines = 1) }
                        )
                    }
                    scheduledTime?.let { st ->
                        val stStr = st.format(DateTimeFormatter.ofPattern("HH:mm"))
                        AssistChip(
                            onClick = {
                                selectedTime = st
                                pickerRev++
                            },
                            label = { Text(stringResource(R.string.stack_time_on_schedule, stStr), maxLines = 1) }
                        )
                    }
                }

                key(pickerRev) {
                    val tpState = rememberTimePickerState(
                        initialHour = selectedTime.hour,
                        initialMinute = selectedTime.minute,
                        is24Hour = true
                    )
                    LaunchedEffect(tpState.hour, tpState.minute) {
                        selectedTime = LocalTime.of(tpState.hour, tpState.minute)
                    }
                    TimePicker(state = tpState)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosenInstant = baseDate.atTime(selectedTime).atZone(zone).toInstant()
                    onConfirm(chosenInstant)
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CaffeineLogTimeDialog(
    title: String,
    baseDate: LocalDate,
    initialTime: LocalTime,
    initialMg: Int,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onConfirm: (Int, Instant) -> Unit
) {
    val zone = ZoneId.systemDefault()
    var mgText by remember { mutableStateOf(initialMg.coerceAtLeast(1).toString()) }
    var selectedTime by remember { mutableStateOf(initialTime) }
    var pickerRev by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(75, 100, 160).forEach { presetMg ->
                        FilterChip(
                            selected = mgText.toIntOrNull() == presetMg,
                            onClick = { mgText = presetMg.toString() },
                            label = { Text("$presetMg mg", maxLines = 1) }
                        )
                    }
                }
                OutlinedTextField(
                    value = mgText,
                    onValueChange = { mgText = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.stack_field_caffeine)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = {
                            selectedTime = LocalTime.now(zone)
                            pickerRev++
                        },
                        label = { Text(stringResource(R.string.stack_time_now), maxLines = 1) }
                    )
                    listOf(30L to "-30м", 60L to "-1г", 120L to "-2г", 180L to "-3г").forEach { (mins, label) ->
                        AssistChip(
                            onClick = {
                                selectedTime = LocalTime.now(zone).minusMinutes(mins)
                                pickerRev++
                            },
                            label = { Text(label, maxLines = 1) }
                        )
                    }
                }

                key(pickerRev) {
                    val tpState = rememberTimePickerState(
                        initialHour = selectedTime.hour,
                        initialMinute = selectedTime.minute,
                        is24Hour = true
                    )
                    LaunchedEffect(tpState.hour, tpState.minute) {
                        selectedTime = LocalTime.of(tpState.hour, tpState.minute)
                    }
                    TimePicker(state = tpState)
                }
            }
        },
        confirmButton = {
            val parsedMg = mgText.toIntOrNull()?.coerceIn(1, 1500) ?: 100
            Button(
                onClick = {
                    val chosenInstant = baseDate.atTime(selectedTime).atZone(zone).toInstant()
                    onConfirm(parsedMg, chosenInstant)
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.action_delete), color = CoralEnergy)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StackTodayTab(
    state: StackUiState,
    isUk: Boolean,
    hasNotifPerm: Boolean,
    canExactAlarm: Boolean,
    onRequestNotifPerm: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenCompatGuide: () -> Unit,
    onSeedStarterStack: () -> Unit,
    onOpenAddManual: () -> Unit,
    onToggleDose: (StackDoseItemUi) -> Unit,
    onOpenDoseTimePicker: (StackDoseItemUi) -> Unit,
    onTakeAllSlot: (StackSlotGroupUi, String, String) -> Unit,
    onEditItem: (SupplementWithSchedules) -> Unit,
    onQuickCaffeine: (Int, String, String) -> Unit,
    onOpenCustomCaffeine: () -> Unit,
    onEditCaffeineLog: (StackLogEntryUi) -> Unit,
    onRestockClick: (StackShelfItemUi) -> Unit,
    onOpenBedtimePicker: () -> Unit
) {
    val expandedOverrides = remember { mutableStateMapOf<StackTimeSlot, Boolean>() }

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
            // 1. Slim Daily Checklist Header Card (Apple Health / SuppCo ring + streak + compat shortcut)
            item {
                val total = state.totalDueTodayCount.coerceAtLeast(1)
                val prog = (state.takenTodayCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                val allDone = state.totalDueTodayCount > 0 && state.takenTodayCount >= state.totalDueTodayCount

                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                            CircularProgressIndicator(
                                progress = { prog },
                                color = if (allDone) EmeraldRest else ElectricBlue,
                                trackColor = CardElevated,
                                strokeWidth = 5.dp,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier.fillMaxSize()
                            )
                            Text(
                                text = "${state.takenTodayCount}/${state.totalDueTodayCount}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (allDone) EmeraldRest else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        Box {
                            Surface(
                                shape = CircleShape,
                                color = CardElevated,
                                onClick = onOpenCompatGuide
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = stringResource(R.string.stack_compat_guide_btn),
                                    tint = if (state.activeConflictCount > 0) CoralEnergy else ElectricBlue,
                                    modifier = Modifier.padding(10.dp).size(20.dp)
                                )
                            }
                            if (state.activeConflictCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(CoralEnergy)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Permission Alert Banner (ONLY when reminders enabled and permission missing)
        if (state.remindersEnabled && (!hasNotifPerm || !canExactAlarm)) {
            item {
                val isNotifMissing = !hasNotifPerm
                ObsidianCard(highlightedBorder = GoldPr, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = GoldPr)
                        Text(
                            text = if (isNotifMissing) stringResource(R.string.stack_perm_notif_banner)
                            else stringResource(R.string.stack_perm_exact_banner),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = if (isNotifMissing) onRequestNotifPerm else onOpenExactAlarmSettings,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(R.string.stack_perm_grant_btn), maxLines = 1)
                        }
                    }
                }
            }
        }

        // 3. Slim Active Conflict Strip (ONLY if CONFLICT exists)
        if (state.activeConflictCount > 0) {
            item {
                ObsidianCard(
                    highlightedBorder = CoralEnergy,
                    onClick = onOpenCompatGuide,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CoralEnergy, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.stack_conflict_banner_count, state.activeConflictCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = CoralEnergy,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 4. Chronological Grouped Time-Slot Cards (Medisafe / Apple Health pattern)
        items(state.slotGroups, key = { "slot_${it.slot.name}" }) { group ->
            val slotShortEn = slotShortLabelEn(group.slot)
            val slotShortUk = slotShortLabelUk(group.slot)
            val isExpanded = expandedOverrides[group.slot] ?: !group.isComplete

            StackSlotGroupCard(
                group = group,
                isUk = isUk,
                isExpanded = isExpanded,
                onToggleExpand = { expandedOverrides[group.slot] = !isExpanded },
                onTakeAll = { onTakeAllSlot(group, slotShortEn, slotShortUk) },
                onToggleDose = onToggleDose,
                onOpenDoseTimePicker = onOpenDoseTimePicker,
                onEditItem = onEditItem
            )
        }

        // 5. Low-Stock Banner (only if low stock)
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
                            Icon(Icons.Default.Warning, contentDescription = null, tint = GoldPr, modifier = Modifier.size(18.dp))
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

        // 6. Compact Caffeine & Sleep Guard Card (below checklist!)
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Coffee, contentDescription = null, tint = GoldPr, modifier = Modifier.size(18.dp))
                                Text(
                                    text = stringResource(R.string.stack_caffeine_title),
                                    style = MaterialTheme.typography.titleSmall,
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
                                    style = MaterialTheme.typography.titleMedium,
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

                        if (state.todayCaffeineLogs.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                state.todayCaffeineLogs.forEach { entry ->
                                    val mgVal = entry.log.caffeineMg ?: entry.item?.caffeineMgPerServing ?: 0
                                    val hhmm = entry.log.takenAt.atZone(ZoneId.systemDefault()).toLocalTime()
                                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = CardElevated,
                                        onClick = { onEditCaffeineLog(entry) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = GoldPr, modifier = Modifier.size(13.dp))
                                            Text(
                                                text = "$mgVal mg • $hhmm",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
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
                            AssistChip(
                                onClick = onOpenCustomCaffeine,
                                leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.stack_quick_caffeine_custom), maxLines = 1) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StackSlotGroupCard(
    group: StackSlotGroupUi,
    isUk: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onTakeAll: () -> Unit,
    onToggleDose: (StackDoseItemUi) -> Unit,
    onOpenDoseTimePicker: (StackDoseItemUi) -> Unit,
    onEditItem: (SupplementWithSchedules) -> Unit
) {
    val slotTitle = stringResource(slotShortLabelRes(group.slot))
    val slotIcon = slotIcon(group.slot)
    val haptics = LocalHapticFeedback.current

    ObsidianCard(
        highlightedBorder = if (group.isComplete) EmeraldRest.copy(alpha = 0.45f) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Group Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (group.isComplete) Icons.Default.Check else slotIcon,
                    contentDescription = null,
                    tint = if (group.isComplete) EmeraldRest else ElectricBlue,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (group.isComplete) {
                        stringResource(R.string.stack_slot_done_header, slotTitle, group.takenCount, group.totalCount)
                    } else {
                        slotTitle
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (group.isComplete) EmeraldRest else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                if (!group.isComplete && group.pendingCount >= 2) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ElectricBlue.copy(alpha = 0.16f),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTakeAll()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.stack_take_all),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                } else if (!group.isComplete) {
                    Text(
                        text = "${group.takenCount}/${group.totalCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    group.doses.forEach { doseUi ->
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                        StackDoseRow(
                            doseUi = doseUi,
                            isUk = isUk,
                            onToggle = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleDose(doseUi)
                            },
                            onPickTime = { onOpenDoseTimePicker(doseUi) },
                            onEdit = { onEditItem(doseUi.withSchedules) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StackDoseRow(
    doseUi: StackDoseItemUi,
    isUk: Boolean,
    onToggle: () -> Unit,
    onPickTime: () -> Unit,
    onEdit: () -> Unit
) {
    val item = doseUi.item
    val taken = doseUi.takenTodayLogId != null
    val name = if (isUk) item.nameUk else item.nameEn
    val doseStr = "${formatDose(item.defaultDose)} ${unitLabel(item.unit, isUk)}"

    val takenHhmm = if (taken && doseUi.takenAt != null) {
        doseUi.takenAt.atZone(ZoneId.systemDefault()).toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } else null

    val scheduleSubtitle = if (doseUi.schedule != null) {
        val trig = stringResource(triggerLabelRes(doseUi.schedule.triggerType))
        val time = doseUi.schedule.timeOfDay?.format(DateTimeFormatter.ofPattern("HH:mm"))
        if (time != null) "$time • $trig" else trig
    } else {
        stringResource(R.string.stack_no_schedule_hint)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 14.dp, end = 4.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (taken) EmeraldRest else CardElevated)
                .then(
                    if (!taken) Modifier.border(1.5.dp, ElectricBlue.copy(alpha = 0.7f), CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (taken) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            } else if (item.category == SupplementCategory.PRE_WORKOUT) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(16.dp)
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
                        color = if (taken) MaterialTheme.colorScheme.onSurfaceVariant else ElectricBlue,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            if (takenHhmm != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldRest.copy(alpha = 0.14f),
                    onClick = onPickTime
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.stack_taken_at, takenHhmm),
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldRest,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = stringResource(R.string.stack_edit_time_cd),
                            tint = EmeraldRest,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = scheduleSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!taken && doseUi.liveWarning?.kind == InteractionKind.CONFLICT) {
                ScaleBadgePill(
                    text = if (isUk) doseUi.liveWarning.titleUk else doseUi.liveWarning.titleEn,
                    color = CoralEnergy
                )
            }
        }

        if (!taken) {
            IconButton(onClick = onPickTime) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = stringResource(R.string.stack_log_custom_time_cd),
                    tint = ElectricBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.stack_edit_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StackInventoryTab(
    state: StackUiState,
    isUk: Boolean,
    hasNotifPerm: Boolean,
    canExactAlarm: Boolean,
    onRequestNotifPerm: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onToggleReminders: (Boolean) -> Unit,
    onTestPush: () -> Unit,
    onOpenBedtimePicker: () -> Unit,
    onOpenCompatGuide: () -> Unit,
    onEditItem: (StackShelfItemUi) -> Unit,
    onRestockItem: (StackShelfItemUi) -> Unit
) {
    val bedH = state.bedtimeMinutesOfDay / 60
    val bedM = state.bedtimeMinutesOfDay % 60
    val bedLabel = if (bedH == 0 && bedM == 0) "24:00" else "%02d:%02d".format(bedH, bedM)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Reminders, Permissions Diagnostic, Next Reminder, Test Push & Bedtime Card
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

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = (if (hasNotifPerm) EmeraldRest else GoldPr).copy(alpha = 0.16f),
                            onClick = { if (!hasNotifPerm) onRequestNotifPerm() }
                        ) {
                            Text(
                                text = stringResource(
                                    if (hasNotifPerm) R.string.stack_perm_notif_ok else R.string.stack_perm_notif_missing
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (hasNotifPerm) EmeraldRest else GoldPr,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = (if (canExactAlarm) EmeraldRest else GoldPr).copy(alpha = 0.16f),
                            onClick = { if (!canExactAlarm) onOpenExactAlarmSettings() }
                        ) {
                            Text(
                                text = stringResource(
                                    if (canExactAlarm) R.string.stack_perm_exact_ok else R.string.stack_perm_exact_missing
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (canExactAlarm) EmeraldRest else GoldPr,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    val nextText = formatNextReminder(state.nextReminder, isUk)
                    Text(
                        text = nextText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.nextReminder != null) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTestPush,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.stack_test_push_btn), maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onOpenBedtimePicker,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(bedLabel, maxLines = 1)
                        }
                    }

                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenCompatGuide)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.stack_compat_guide_btn),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        items(state.shelfItems, key = { it.withSchedules.item.id }) { ui ->
            val item = ui.withSchedules.item
            val activeSchedules = ui.withSchedules.schedules.filter { it.enabled && it.deletedAt == null }
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
                                    ((item.caffeineMgPerServing ?: 0).takeIf { it > 0 }?.let { " • $it mg caffeine" } ?: "") +
                                    (if (ui.dueTodayCount > 0) " • ${ui.takenTodayCount}/${ui.dueTodayCount}" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onEditItem(ui) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.stack_edit_title))
                        }
                    }

                    // All schedules rendered as compact chips (supports 2+ doses/day!)
                    if (activeSchedules.isEmpty()) {
                        OutlinedButton(
                            onClick = { onEditItem(ui) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            border = BorderStroke(1.dp, GoldPr),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(R.string.stack_add_schedule_btn), color = GoldPr)
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            activeSchedules.forEach { sched ->
                                val trigStr = stringResource(triggerLabelRes(sched.triggerType))
                                val timeStr = sched.timeOfDay?.format(DateTimeFormatter.ofPattern("HH:mm"))
                                val label = if (timeStr != null) "$timeStr • $trigStr" else trigStr
                                Surface(shape = RoundedCornerShape(10.dp), color = CardElevated) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricBlue,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    val synText = if (isUk) ui.recommendation.synergiesUk else ui.recommendation.synergiesEn
                    val avoidText = if (isUk) ui.recommendation.avoidUk else ui.recommendation.avoidEn
                    if (synText.isNotBlank() && synText != "—") {
                        Text(
                            text = "${stringResource(R.string.stack_synergy_label)}: $synText",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldRest
                        )
                    }
                    if (avoidText.isNotBlank()) {
                        Text(
                            text = "${stringResource(R.string.stack_avoid_label)}: $avoidText",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldPr
                        )
                    }

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
    }
}

@Composable
private fun StackHistoryTab(
    state: StackUiState,
    isUk: Boolean,
    onEditLogTime: (StackLogEntryUi) -> Unit,
    onDeleteLog: (Long) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)
    val grouped = remember(state.recentLogs) {
        state.recentLogs.groupBy { it.log.takenAt.atZone(zone).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (grouped.isEmpty()) {
            item {
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.stack_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            grouped.forEach { (date, entries) ->
                item(key = "hdr_$date") {
                    val headerText = when (date) {
                        today -> stringResource(R.string.stack_history_today)
                        yesterday -> stringResource(R.string.stack_history_yesterday)
                        else -> formatDateShort(date)
                    }
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(entries, key = { it.log.id }) { entry ->
                    val itemName = entry.item?.let { if (isUk) it.nameUk else it.nameEn }
                        ?: "${entry.log.caffeineMg ?: 0} mg"
                    val unitStr = entry.item?.let { unitLabel(it.unit, isUk) } ?: ""
                    val timeStr = entry.log.takenAt.atZone(zone).toLocalTime()
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                    val cafStr = entry.log.caffeineMg?.takeIf { it > 0 }?.let { " • $it mg" } ?: ""

                    ObsidianCard(
                        onClick = { onEditLogTime(entry) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "$timeStr • $itemName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (entry.log.linkedWorkoutId != null) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = ElectricBlue.copy(alpha = 0.16f)) {
                                            Text(
                                                text = stringResource(R.string.stack_history_workout_badge),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ElectricBlue,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${formatDose(entry.log.dose)} $unitStr$cafStr".trim(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onEditLogTime(entry) }) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = stringResource(R.string.stack_edit_time_cd),
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(18.dp)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompatibilityReferenceSheet(
    isUk: Boolean,
    stackInteractions: List<StackInteraction>,
    onDismiss: () -> Unit,
    onAddPreset: (SupplementReferenceEntry) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val catalog = remember { SupplementInteractionEngine.referenceCatalog() }
    var query by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf<SupplementCategory?>(null) }

    val filtered = remember(query, selectedCat, isUk) {
        val q = query.trim().lowercase()
        catalog.filter { entry ->
            (selectedCat == null || entry.category == selectedCat) &&
                (q.isEmpty() ||
                    entry.nameUk.lowercase().contains(q) ||
                    entry.nameEn.lowercase().contains(q) ||
                    entry.recommendation.synergiesUk.lowercase().contains(q) ||
                    entry.recommendation.avoidUk.lowercase().contains(q) ||
                    entry.recommendation.synergiesEn.lowercase().contains(q) ||
                    entry.recommendation.avoidEn.lowercase().contains(q))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.stack_compat_guide_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.stack_compat_guide_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCat == null,
                    onClick = { selectedCat = null },
                    label = { Text(stringResource(R.string.watch_filter_all), maxLines = 1) }
                )
                listOf(
                    SupplementCategory.CREATINE,
                    SupplementCategory.PROTEIN,
                    SupplementCategory.PRE_WORKOUT,
                    SupplementCategory.VITAMIN,
                    SupplementCategory.MINERAL,
                    SupplementCategory.OMEGA_3,
                    SupplementCategory.OTHER
                ).forEach { cat ->
                    FilterChip(
                        selected = selectedCat == cat,
                        onClick = { selectedCat = if (selectedCat == cat) null else cat },
                        label = { Text(stringResource(categoryLabelRes(cat)), maxLines = 1) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (stackInteractions.isNotEmpty() && query.isBlank() && selectedCat == null) {
                    item {
                        ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.stack_compat_your_stack),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricBlue
                                )
                                stackInteractions.forEach { inter ->
                                    val col = when (inter.kind) {
                                        InteractionKind.SYNERGY -> EmeraldRest
                                        InteractionKind.CAUTION -> GoldPr
                                        InteractionKind.CONFLICT -> CoralEnergy
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            text = if (isUk) inter.titleUk else inter.titleEn,
                                            style = MaterialTheme.typography.labelMedium,
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

                items(filtered, key = { it.tag.name }) { entry ->
                    val name = if (isUk) entry.nameUk else entry.nameEn
                    val rec = entry.recommendation
                    ObsidianCard(containerColor = CardElevated, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "$name • ${formatDose(entry.defaultDose)} ${unitLabel(entry.unit, isUk)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isUk) rec.timingUk else rec.timingEn,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ElectricBlue
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onAddPreset(entry) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(stringResource(R.string.stack_add_to_stack_short), maxLines = 1)
                                }
                            }
                            Text(
                                text = if (isUk) rec.tipUk else rec.tipEn,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${stringResource(R.string.stack_synergy_label)}: ${if (isUk) rec.synergiesUk else rec.synergiesEn}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldRest
                            )
                            Text(
                                text = "${stringResource(R.string.stack_avoid_label)}: ${if (isUk) rec.avoidUk else rec.avoidEn}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CoralEnergy
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEditSupplementSheet(
    initial: SupplementWithSchedules?,
    prefill: SupplementReferenceEntry?,
    existingStack: List<SupplementWithSchedules>,
    bedtimeMinutesOfDay: Int,
    isUk: Boolean,
    onDismiss: () -> Unit,
    onSave: (SupplementItemEntity, List<SupplementScheduleEntity>) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initItem = initial?.item
    val initScheds = initial?.schedules?.filter { it.deletedAt == null }.orEmpty()
    val allPresets = remember { SupplementInteractionEngine.referenceCatalog() }

    var nameText by remember {
        mutableStateOf(
            initItem?.let { if (isUk) it.nameUk else it.nameEn }
                ?: prefill?.let { if (isUk) it.nameUk else it.nameEn }
                ?: ""
        )
    }
    var category by remember { mutableStateOf(initItem?.category ?: prefill?.category ?: SupplementCategory.OTHER) }
    var doseText by remember {
        mutableStateOf(
            initItem?.defaultDose?.let { formatDose(it) }
                ?: prefill?.defaultDose?.let { formatDose(it) }
                ?: "1"
        )
    }
    var unit by remember { mutableStateOf(initItem?.unit ?: prefill?.unit ?: SupplementUnit.CAPSULE) }
    var caffeineText by remember {
        mutableStateOf((initItem?.caffeineMgPerServing ?: prefill?.caffeineMg ?: 0).toString())
    }
    var servingsText by remember { mutableStateOf(initItem?.servingsRemaining?.let { formatDose(it) } ?: "60") }
    var lowThreshText by remember { mutableStateOf(initItem?.lowStockThreshold?.let { formatDose(it) } ?: "10") }

    var scheduleDrafts by remember {
        mutableStateOf(
            if (initScheds.isNotEmpty()) {
                initScheds.map { s ->
                    ScheduleDraft(
                        existingId = s.id,
                        remoteId = s.remoteId,
                        trigger = s.triggerType,
                        timeOfDay = s.timeOfDay,
                        daysOfWeekMask = s.daysOfWeekMask
                    )
                }
            } else {
                val trig = prefill?.defaultTrigger ?: SupplementTrigger.WITH_MEAL
                val defaultT = prefill?.defaultTime?.takeIf { it.isNotBlank() }?.let { raw ->
                    runCatching { LocalTime.parse(raw, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
                } ?: defaultTimeForTrigger(trig)
                listOf(ScheduleDraft(trigger = trig, timeOfDay = defaultT))
            }
        )
    }

    var cycleExpanded by remember {
        mutableStateOf((initItem?.cycleDaysOn ?: 0) > 0 && (initItem?.cycleDaysOff ?: 0) > 0)
    }
    var cycleOnText by remember { mutableStateOf(initItem?.cycleDaysOn?.toString() ?: "") }
    var cycleOffText by remember { mutableStateOf(initItem?.cycleDaysOff?.toString() ?: "") }
    var timePickerDraftKey by remember { mutableStateOf<String?>(null) }

    val previewItem = SupplementItemEntity(
        id = initItem?.id ?: 0L,
        nameEn = nameText.ifBlank { "Custom" },
        nameUk = nameText.ifBlank { "Добавка" },
        category = category,
        defaultDose = doseText.replace(',', '.').toDoubleOrNull() ?: 1.0,
        unit = unit,
        caffeineMgPerServing = caffeineText.toIntOrNull() ?: 0
    )
    val previewSchedules = scheduleDrafts.mapIndexed { idx, d ->
        SupplementScheduleEntity(
            id = if (d.existingId > 0L) d.existingId else -(idx + 1L),
            supplementId = previewItem.id,
            triggerType = d.trigger,
            timeOfDay = if (needsTimeOfDay(d.trigger)) d.timeOfDay else null,
            daysOfWeekMask = d.daysOfWeekMask,
            enabled = true
        )
    }
    val previewRec = SupplementInteractionEngine.recommendationFor(previewItem)
    val liveStackChecks = remember(
        nameText, category, doseText, unit, caffeineText, scheduleDrafts, existingStack, bedtimeMinutesOfDay
    ) {
        if (nameText.isBlank()) emptyList()
        else SupplementInteractionEngine.previewForCandidate(
            candidate = previewItem,
            schedules = previewSchedules,
            existingStack = existingStack,
            bedtimeMinutesOfDay = bedtimeMinutesOfDay
        )
    }

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
                    allPresets.forEach { p ->
                        AssistChip(
                            onClick = {
                                nameText = if (isUk) p.nameUk else p.nameEn
                                category = p.category
                                doseText = formatDose(p.defaultDose)
                                unit = p.unit
                                caffeineText = p.caffeineMg.toString()
                                servingsText = "60"
                                lowThreshText = "10"
                                val parsedT = p.defaultTime.takeIf { it.isNotBlank() }?.let { raw ->
                                    runCatching { LocalTime.parse(raw, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
                                } ?: defaultTimeForTrigger(p.defaultTrigger)
                                scheduleDrafts = listOf(ScheduleDraft(trigger = p.defaultTrigger, timeOfDay = parsedT))
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

            // Multi-Schedule Builder (supports 2+ doses/day e.g. Omega morning + evening, Creatine morning + post-workout!)
            Text(
                text = stringResource(R.string.stack_schedules_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            scheduleDrafts.forEachIndexed { idx, draft ->
                ObsidianCard(containerColor = CardElevated, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.stack_dose_n, idx + 1),
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricBlue,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (needsTimeOfDay(draft.trigger)) {
                                    val tLabel = (draft.timeOfDay ?: defaultTimeForTrigger(draft.trigger) ?: LocalTime.of(9, 0))
                                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ElectricBlue.copy(alpha = 0.16f),
                                        onClick = { timePickerDraftKey = draft.key }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                                            Text(tLabel, style = MaterialTheme.typography.labelLarge, color = ElectricBlue, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                if (scheduleDrafts.size > 1) {
                                    IconButton(
                                        onClick = {
                                            scheduleDrafts = scheduleDrafts.filterNot { it.key == draft.key }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_delete), tint = CoralEnergy, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SupplementTrigger.entries.forEach { t ->
                                FilterChip(
                                    selected = draft.trigger == t,
                                    onClick = {
                                        val nextTime = if (needsTimeOfDay(t)) {
                                            draft.timeOfDay ?: defaultTimeForTrigger(t)
                                        } else null
                                        scheduleDrafts = scheduleDrafts.map {
                                            if (it.key == draft.key) it.copy(trigger = t, timeOfDay = nextTime) else it
                                        }
                                    },
                                    label = { Text(stringResource(triggerLabelRes(t)), maxLines = 1) }
                                )
                            }
                        }

                        // Compact 7-day selector
                        val dayLetters = if (isUk) listOf("П", "В", "С", "Ч", "П", "С", "Н") else listOf("M", "T", "W", "T", "F", "S", "S")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            dayLetters.forEachIndexed { dayIdx, letter ->
                                val bit = 1 shl dayIdx
                                val selected = (draft.daysOfWeekMask and bit) != 0
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) ElectricBlue else MaterialTheme.colorScheme.surface)
                                        .clickable {
                                            val toggled = draft.daysOfWeekMask xor bit
                                            val nextMask = if (toggled == 0) bit else toggled
                                            scheduleDrafts = scheduleDrafts.map {
                                                if (it.key == draft.key) it.copy(daysOfWeekMask = nextMask) else it
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = letter,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (scheduleDrafts.size < 5) {
                OutlinedButton(
                    onClick = {
                        scheduleDrafts = scheduleDrafts + suggestNextScheduleDraft(category, scheduleDrafts)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.stack_add_another_dose))
                }
            }

            // Collapsible Cycle ON / OFF section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { cycleExpanded = !cycleExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.stack_cycle_optional_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (cycleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = cycleExpanded) {
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
            }

            ObsidianCard(containerColor = CardElevated, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                    val synText = if (isUk) previewRec.synergiesUk else previewRec.synergiesEn
                    val avoidText = if (isUk) previewRec.avoidUk else previewRec.avoidEn
                    if (synText.isNotBlank() && synText != "—") {
                        Text(
                            text = "${stringResource(R.string.stack_synergy_label)}: $synText",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldRest
                        )
                    }
                    if (avoidText.isNotBlank()) {
                        Text(
                            text = "${stringResource(R.string.stack_avoid_label)}: $avoidText",
                            style = MaterialTheme.typography.labelSmall,
                            color = CoralEnergy
                        )
                    }
                    if (liveStackChecks.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        Text(
                            text = stringResource(R.string.stack_live_compat_header),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        liveStackChecks.forEach { inter ->
                            val col = when (inter.kind) {
                                InteractionKind.SYNERGY -> EmeraldRest
                                InteractionKind.CAUTION -> GoldPr
                                InteractionKind.CONFLICT -> CoralEnergy
                            }
                            Text(
                                text = if (isUk) "• ${inter.titleUk}" else "• ${inter.titleEn}",
                                style = MaterialTheme.typography.labelSmall,
                                color = col
                            )
                        }
                    }
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
                        val schedEntities = scheduleDrafts.map { d ->
                            val base = SupplementScheduleEntity(
                                id = d.existingId,
                                supplementId = itemEntity.id,
                                triggerType = d.trigger,
                                timeOfDay = if (needsTimeOfDay(d.trigger)) {
                                    d.timeOfDay ?: defaultTimeForTrigger(d.trigger)
                                } else null,
                                daysOfWeekMask = d.daysOfWeekMask,
                                enabled = true
                            )
                            if (d.remoteId != null) base.copy(remoteId = d.remoteId) else base
                        }
                        onSave(itemEntity, schedEntities)
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

    timePickerDraftKey?.let { key ->
        val draft = scheduleDrafts.firstOrNull { it.key == key }
        if (draft != null) {
            val initT = draft.timeOfDay ?: defaultTimeForTrigger(draft.trigger) ?: LocalTime.of(9, 0)
            val tpState = rememberTimePickerState(
                initialHour = initT.hour,
                initialMinute = initT.minute,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { timePickerDraftKey = null },
                title = { Text(stringResource(R.string.stack_field_time)) },
                text = { TimePicker(state = tpState) },
                confirmButton = {
                    Button(
                        onClick = {
                            val chosen = LocalTime.of(tpState.hour, tpState.minute)
                            scheduleDrafts = scheduleDrafts.map {
                                if (it.key == key) it.copy(timeOfDay = chosen) else it
                            }
                            timePickerDraftKey = null
                        }
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { timePickerDraftKey = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

private fun checkNotifPerm(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun checkExactAlarmPerm(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return am.canScheduleExactAlarms()
}

private fun needsTimeOfDay(trigger: SupplementTrigger): Boolean =
    trigger == SupplementTrigger.FIXED_TIME ||
        trigger == SupplementTrigger.WITH_MEAL ||
        trigger == SupplementTrigger.BEDTIME

private fun defaultTimeForTrigger(trigger: SupplementTrigger): LocalTime? = when (trigger) {
    SupplementTrigger.WITH_MEAL -> LocalTime.of(9, 0)
    SupplementTrigger.FIXED_TIME -> LocalTime.of(14, 0)
    SupplementTrigger.BEDTIME -> LocalTime.of(23, 15)
    SupplementTrigger.BEFORE_WORKOUT, SupplementTrigger.AFTER_WORKOUT -> null
}

private fun suggestNextScheduleDraft(
    category: SupplementCategory,
    existing: List<ScheduleDraft>
): ScheduleDraft {
    if ((category == SupplementCategory.CREATINE || category == SupplementCategory.PROTEIN) &&
        existing.none { it.trigger == SupplementTrigger.AFTER_WORKOUT }
    ) {
        return ScheduleDraft(trigger = SupplementTrigger.AFTER_WORKOUT, timeOfDay = null)
    }
    val hasEvening = existing.any {
        val h = it.timeOfDay?.hour ?: return@any false
        h >= 18
    }
    val hasMorning = existing.any {
        val h = it.timeOfDay?.hour ?: return@any false
        h < 12
    }
    return when {
        !hasEvening -> ScheduleDraft(trigger = SupplementTrigger.WITH_MEAL, timeOfDay = LocalTime.of(20, 0))
        !hasMorning -> ScheduleDraft(trigger = SupplementTrigger.WITH_MEAL, timeOfDay = LocalTime.of(9, 0))
        else -> ScheduleDraft(trigger = SupplementTrigger.FIXED_TIME, timeOfDay = LocalTime.of(14, 0))
    }
}

@Composable
private fun formatNextReminder(info: NextReminderInfo?, isUk: Boolean): String {
    if (info == null) return stringResource(R.string.stack_next_reminder_none)
    val today = LocalDate.now(info.triggerAt.zone)
    val targetDate = info.triggerAt.toLocalDate()
    val timeStr = info.triggerAt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    val itemsStr = (if (isUk) info.itemNamesUk else info.itemNamesEn).take(3).joinToString(", ")
    return when (targetDate) {
        today -> stringResource(R.string.stack_next_reminder_today, timeStr, itemsStr)
        today.plusDays(1) -> stringResource(R.string.stack_next_reminder_tomorrow, timeStr, itemsStr)
        else -> {
            val dayStr = "${formatDateShort(targetDate)} $timeStr"
            stringResource(R.string.stack_next_reminder_day, dayStr, itemsStr)
        }
    }
}

private fun slotIcon(slot: StackTimeSlot): ImageVector = when (slot) {
    StackTimeSlot.MORNING -> Icons.Default.WbSunny
    StackTimeSlot.AFTERNOON -> Icons.Default.Schedule
    StackTimeSlot.WORKOUT -> Icons.Default.FitnessCenter
    StackTimeSlot.EVENING -> Icons.Default.WbTwilight
    StackTimeSlot.BEDTIME -> Icons.Default.Bedtime
    StackTimeSlot.ANYTIME -> Icons.Default.Medication
}

@StringRes
private fun slotShortLabelRes(slot: StackTimeSlot): Int = when (slot) {
    StackTimeSlot.MORNING -> R.string.stack_slot_morning_short
    StackTimeSlot.AFTERNOON -> R.string.stack_slot_afternoon_short
    StackTimeSlot.WORKOUT -> R.string.stack_slot_workout_short
    StackTimeSlot.EVENING -> R.string.stack_slot_evening_short
    StackTimeSlot.BEDTIME -> R.string.stack_slot_bedtime_short
    StackTimeSlot.ANYTIME -> R.string.stack_slot_anytime_short
}

private fun slotShortLabelEn(slot: StackTimeSlot): String = when (slot) {
    StackTimeSlot.MORNING -> "Morning"
    StackTimeSlot.AFTERNOON -> "Afternoon"
    StackTimeSlot.WORKOUT -> "Workout"
    StackTimeSlot.EVENING -> "Evening"
    StackTimeSlot.BEDTIME -> "Bedtime"
    StackTimeSlot.ANYTIME -> "Anytime"
}

private fun slotShortLabelUk(slot: StackTimeSlot): String = when (slot) {
    StackTimeSlot.MORNING -> "Ранок"
    StackTimeSlot.AFTERNOON -> "День"
    StackTimeSlot.WORKOUT -> "Тренування"
    StackTimeSlot.EVENING -> "Вечір"
    StackTimeSlot.BEDTIME -> "Перед сном"
    StackTimeSlot.ANYTIME -> "Будь-коли"
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
