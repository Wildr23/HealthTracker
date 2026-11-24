package com.gymtracker.app.ui.workout

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.app.R
import com.gymtracker.app.data.db.ExerciseEntity
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.SetType
import com.gymtracker.app.data.db.WorkoutExerciseWithSets
import com.gymtracker.app.data.db.WorkoutSetEntity
import com.gymtracker.app.domain.PrEngine
import com.gymtracker.app.ui.common.formatDurationMinSec
import com.gymtracker.app.ui.common.isUkrainianLocale
import com.gymtracker.app.ui.common.labelRes
import com.gymtracker.app.ui.common.localizedName
import com.gymtracker.app.ui.components.MiniBodyMap
import com.gymtracker.app.ui.components.muscleIntensityForExercise
import com.gymtracker.app.ui.theme.CardElevated
import com.gymtracker.app.ui.theme.CardSurface
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.ObsidianBg
import com.gymtracker.app.ui.theme.ObsidianCard
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onNavigateBack: () -> Unit,
    onWorkoutFinished: (Long) -> Unit,
    onOpenExerciseDetail: (Long) -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isUk = isUkrainianLocale()
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    DisposableEffect(state.keepScreenAwake) {
        val window = (view.context as? Activity)?.window
        if (state.keepScreenAwake) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    var showFinishDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var showExerciseListSheet by remember { mutableStateOf(false) }
    var editingSetTarget by remember { mutableStateOf<Pair<WorkoutExerciseWithSets, WorkoutSetEntity>?>(null) }
    var editingNoteFor by remember { mutableStateOf<WorkoutExerciseWithSets?>(null) }

    val wf = state.workoutFull
    val orderedExercises = remember(wf) {
        wf?.exercises?.sortedBy { it.workoutExercise.orderIndex }.orEmpty()
    }
    val completedVolume = remember(orderedExercises) {
        orderedExercises.sumOf { ex ->
            ex.sets.filter { it.completedAt != null && it.setType != SetType.WARMUP }
                .sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
        }
    }

    val pagerState = rememberPagerState(pageCount = { orderedExercises.size.coerceAtLeast(1) })
    val currentExercise = orderedExercises.getOrNull(pagerState.currentPage)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    Column {
                        Text(
                            text = wf?.workout?.title ?: stringResource(R.string.active_workout_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatDurationMinSec(state.elapsedSec)} • ${completedVolume.roundToInt()} ${stringResource(R.string.unit_kg)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricBlue
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.togglePreWorkout(!(wf?.workout?.tookPreWorkout ?: false)) }) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = stringResource(R.string.today_preworkout_toggle),
                            tint = if (wf?.workout?.tookPreWorkout == true) GoldPr else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showExerciseListSheet = true }) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = stringResource(R.string.active_all_exercises))
                    }
                    Button(
                        onClick = { showFinishDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(stringResource(R.string.action_finish), maxLines = 1)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Segmented Exercise Progress Bar (Jefit style)
            if (orderedExercises.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    orderedExercises.forEachIndexed { idx, ex ->
                        val allSetsDone = ex.sets.isNotEmpty() && ex.sets.all { it.completedAt != null }
                        val isCurrent = idx == pagerState.currentPage
                        val segColor = when {
                            isCurrent -> ElectricBlue
                            allSetsDone -> EmeraldRest
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(segColor)
                                .clickable { scope.launch { pagerState.animateScrollToPage(idx) } }
                        )
                    }
                }
            }

            // Live PR Banner
            if (state.livePrHits.isNotEmpty()) {
                val topHit = state.livePrHits.first()
                ObsidianCard(
                    highlightedBorder = GoldPr,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = { viewModel.dismissLivePrBanner() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = GoldPr)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                R.string.workout_live_pr_banner,
                                "${stringResource(topHit.type.labelRes())}: ${PrEngine.formatKg(topHit.newValue)}"
                            ),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Main Content: 1 Exercise Per Page (HorizontalPager)
            if (orderedExercises.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { showAddExerciseDialog = true },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.workout_add_exercise), style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { pageIdx ->
                    val exItem = orderedExercises.getOrNull(pageIdx) ?: return@HorizontalPager
                    ExercisePagerPage(
                        item = exItem,
                        isUk = isUk,
                        bestE1rmKg = state.exerciseBestE1rmMap[exItem.exercise.id],
                        restSec = state.exerciseRestSecMap[exItem.exercise.id] ?: state.defaultRestSec,
                        onChangeRestSec = { sec -> viewModel.setExerciseRestSeconds(exItem.exercise.id, sec) },
                        onOpenDetail = { onOpenExerciseDetail(exItem.exercise.id) },
                        onOpenPlates = { w -> viewModel.showPlateCalculator(w) },
                        onOpenRepMax = { w, r ->
                            viewModel.showRepMaxTable(w, r, state.exerciseBestE1rmMap[exItem.exercise.id])
                        },
                        onEditNote = { editingNoteFor = exItem },
                        onTapSetValues = { set -> editingSetTarget = exItem to set },
                        onToggleSetDone = { set ->
                            viewModel.toggleSetDone(
                                workoutExerciseId = exItem.workoutExercise.id,
                                exerciseId = exItem.exercise.id,
                                set = set,
                                weightKg = set.weightKg,
                                reps = set.reps,
                                rpe = set.rpe,
                                setType = set.setType
                            )
                        },
                        onUpdateRpe = { set, rpeVal ->
                            viewModel.updateSetDraft(set, set.weightKg, set.reps, set.setType, rpeVal)
                        },
                        onUpdateCardio = { set, durSec, distKm, cal ->
                            viewModel.updateCardioSet(set, durSec, distKm, cal)
                        },
                        onAddSet = { viewModel.addSet(exItem.workoutExercise.id) },
                        onRemoveLastSet = { viewModel.removeLastSet(exItem.sets) }
                    )
                }
            }

            // Bottom Pinned Thumb Zone: Giant Emerald Rest Banner OR Next Exercise CTA
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (state.restRemainingSec > 0) {
                    Surface(
                        color = EmeraldRest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { viewModel.adjustRest(-15) }) {
                                Text("-15s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = formatDurationMinSec(state.restRemainingSec),
                                    color = Color.White,
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = stringResource(R.string.active_rest_banner_label),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            TextButton(onClick = { viewModel.adjustRest(15) }) {
                                Text("+15s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasNote = !currentExercise?.workoutExercise?.note.isNullOrBlank()
                    Surface(
                        onClick = { currentExercise?.let { editingNoteFor = it } },
                        shape = RoundedCornerShape(14.dp),
                        color = CardSurface,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            BadgedBox(
                                badge = {
                                    if (hasNote) {
                                        Badge(containerColor = ElectricBlue) { Text("1") }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = null)
                            }
                        }
                    }

                    Surface(
                        onClick = { showAddExerciseDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        color = CardSurface,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.workout_add_exercise))
                        }
                    }

                    if (state.restRemainingSec > 0) {
                        Button(
                            onClick = { viewModel.skipRest() },
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CardSurface,
                                contentColor = ElectricBlue
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.active_skip_rest),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    } else {
                        val isLast = orderedExercises.isNotEmpty() && pagerState.currentPage >= orderedExercises.lastIndex
                        Button(
                            onClick = {
                                if (orderedExercises.isEmpty() || isLast) {
                                    showFinishDialog = true
                                } else {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                            },
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    if (orderedExercises.isEmpty() || isLast) R.string.action_finish
                                    else R.string.active_next_exercise
                                ),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // Quick Set Editor Bottom Sheet (Kg / Reps / SetType / Steppers)
    editingSetTarget?.let { (exItem, set) ->
        SetEditBottomSheet(
            setNumber = exItem.sets.sortedBy { it.setIndex }.indexOfFirst { it.id == set.id } + 1,
            exerciseName = exItem.exercise.localizedName(isUk),
            isBodyweight = exItem.exercise.type == ExerciseType.BODYWEIGHT,
            set = set,
            onSave = { w, r, st ->
                viewModel.updateSetDraft(set, w, r, st, set.rpe)
                editingSetTarget = null
            },
            onOpenPlates = { w ->
                editingSetTarget = null
                viewModel.showPlateCalculator(w)
            },
            onDelete = {
                viewModel.deleteSet(set.id)
                editingSetTarget = null
            },
            onDismiss = { editingSetTarget = null }
        )
    }

    // Exercise Note Dialog
    editingNoteFor?.let { target ->
        var noteDraft by remember(target.workoutExercise.id) {
            mutableStateOf(target.workoutExercise.note.orEmpty())
        }
        AlertDialog(
            onDismissRequest = { editingNoteFor = null },
            title = { Text(target.exercise.localizedName(isUk)) },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    placeholder = { Text(stringResource(R.string.workout_note_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateNote(target.workoutExercise.id, noteDraft)
                    editingNoteFor = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingNoteFor = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // All Exercises List Bottom Sheet (jump, reorder, delete, discard)
    if (showExerciseListSheet) {
        ModalBottomSheet(onDismissRequest = { showExerciseListSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.active_all_exercises),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        showExerciseListSheet = false
                        showDiscardDialog = true
                    }) {
                        Text(stringResource(R.string.workout_discard), color = MaterialTheme.colorScheme.error)
                    }
                }
                FilterChip(
                    selected = state.keepScreenAwake,
                    onClick = { viewModel.setKeepScreenAwake(!state.keepScreenAwake) },
                    label = { Text(stringResource(R.string.keep_screen_awake)) }
                )
                orderedExercises.forEachIndexed { idx, ex ->
                    val doneCount = ex.sets.count { it.completedAt != null }
                    ObsidianCard(
                        highlightedBorder = if (idx == pagerState.currentPage) ElectricBlue else null,
                        onClick = {
                            showExerciseListSheet = false
                            scope.launch { pagerState.animateScrollToPage(idx) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiniBodyMap(
                                muscleIntensity = muscleIntensityForExercise(ex.exercise),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ex.exercise.localizedName(isUk), fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "$doneCount / ${ex.sets.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (doneCount == ex.sets.size && ex.sets.isNotEmpty()) EmeraldRest else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (idx > 0) {
                                IconButton(onClick = { viewModel.moveExercise(ex.workoutExercise.id, -1) }) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = null)
                                }
                            }
                            if (idx < orderedExercises.lastIndex) {
                                IconButton(onClick = { viewModel.moveExercise(ex.workoutExercise.id, 1) }) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = null)
                                }
                            }
                            IconButton(onClick = { viewModel.removeExercise(ex.workoutExercise.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showAddExerciseDialog) {
        AddExerciseDialog(
            exercises = state.allExercises,
            isUk = isUk,
            onSelect = { exId ->
                viewModel.addExercise(exId)
                showAddExerciseDialog = false
                scope.launch {
                    val target = orderedExercises.size
                    if (target > 0) pagerState.animateScrollToPage(target.coerceAtMost(orderedExercises.size))
                }
            },
            onDismiss = { showAddExerciseDialog = false }
        )
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text(stringResource(R.string.workout_finish_confirm_title)) },
            text = { Text(stringResource(R.string.workout_finish_confirm_body)) },
            confirmButton = {
                Button(onClick = {
                    showFinishDialog = false
                    viewModel.finishWorkout(onWorkoutFinished)
                }) { Text(stringResource(R.string.action_finish)) }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.workout_discard)) },
            text = { Text(stringResource(R.string.workout_discard_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.discardWorkout(onNavigateBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    state.plateBreakdown?.let { pb ->
        ModalBottomSheet(onDismissRequest = { viewModel.dismissPlateCalculator() }) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.plate_calc_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.plate_achievable, PrEngine.formatKg(pb.achievableWeightKg)),
                    style = MaterialTheme.typography.titleMedium,
                    color = GoldPr
                )
                if (pb.platesPerSide.isEmpty()) {
                    Text(stringResource(R.string.plate_bar_only))
                } else {
                    Text(stringResource(R.string.plate_per_side), style = MaterialTheme.typography.labelMedium)
                    pb.platesPerSide.forEach { pc ->
                        Text("• ${pc.countPerSide} × ${PrEngine.formatKg(pc.weightKg)} ${stringResource(R.string.unit_kg)}")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (state.repMaxRows.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { viewModel.dismissRepMaxTable() }) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.ex_detail_rep_max_title), style = MaterialTheme.typography.titleLarge)
                state.repMaxRows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${row.percent}%", fontWeight = FontWeight.Bold)
                        Text("~${row.estimatedMaxReps} ${stringResource(R.string.workout_reps_hint)}")
                        Text("${PrEngine.formatKg(row.targetWeightKg)} ${stringResource(R.string.unit_kg)}", color = ElectricBlue)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ExercisePagerPage(
    item: WorkoutExerciseWithSets,
    isUk: Boolean,
    bestE1rmKg: Double?,
    restSec: Int,
    onChangeRestSec: (Int) -> Unit,
    onOpenDetail: () -> Unit,
    onOpenPlates: (Double) -> Unit,
    onOpenRepMax: (Double?, Int?) -> Unit,
    onEditNote: () -> Unit,
    onTapSetValues: (WorkoutSetEntity) -> Unit,
    onToggleSetDone: (WorkoutSetEntity) -> Unit,
    onUpdateRpe: (WorkoutSetEntity, Float?) -> Unit,
    onUpdateCardio: (WorkoutSetEntity, Int?, Double?, Double?) -> Unit,
    onAddSet: () -> Unit,
    onRemoveLastSet: () -> Unit
) {
    val ex = item.exercise
    val sets = item.sets.sortedBy { it.setIndex }
    val activeSetIdx = sets.indexOfFirst { it.completedAt == null }
    val lastCompletedSet = sets.lastOrNull { it.completedAt != null }
    var restMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Exercise Header with MiniBodyMap + 1RM + quick tool icons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiniBodyMap(
                muscleIntensity = muscleIntensityForExercise(ex),
                modifier = Modifier.size(64.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = ex.localizedName(isUk),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(ex.primaryMuscle.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (ex.type == ExerciseType.WEIGHTED && bestE1rmKg != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("1RM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${PrEngine.formatKg(bestE1rmKg)} ${stringResource(R.string.unit_kg)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Rest Timer Pill + Action Tools Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                Surface(
                    onClick = { restMenuExpanded = true },
                    shape = RoundedCornerShape(10.dp),
                    color = CardSurface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.active_rest_timer), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = formatDurationMinSec(restSec),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                DropdownMenu(expanded = restMenuExpanded, onDismissRequest = { restMenuExpanded = false }) {
                    listOf(30, 60, 90, 120, 180).forEach { sec ->
                        DropdownMenuItem(
                            text = { Text(formatDurationMinSec(sec)) },
                            onClick = {
                                restMenuExpanded = false
                                onChangeRestSec(sec)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            IconButton(onClick = onOpenDetail) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = ElectricBlue)
            }
            if (ex.type == ExerciseType.WEIGHTED) {
                val activeW = sets.getOrNull(activeSetIdx.coerceAtLeast(0))?.weightKg ?: 60.0
                IconButton(onClick = { onOpenPlates(activeW) }) {
                    Icon(Icons.Default.Calculate, contentDescription = stringResource(R.string.workout_plates))
                }
                IconButton(onClick = {
                    val ref = sets.getOrNull(activeSetIdx.coerceAtLeast(0))
                    onOpenRepMax(ref?.weightKg, ref?.reps)
                }) {
                    Icon(Icons.Default.Percent, contentDescription = stringResource(R.string.workout_1rm_table))
                }
            }
        }

        // Note chip if present
        item.workoutExercise.note?.takeIf { it.isNotBlank() }?.let { note ->
            Surface(
                onClick = onEditNote,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "📝 $note",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoldPr,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (ex.type == ExerciseType.CARDIO) {
            // Cardio layout matching Jefit 110747
            sets.forEachIndexed { idx, s ->
                CardioSetCard(
                    setNumber = idx + 1,
                    set = s,
                    onUpdate = { dur, dist, cal -> onUpdateCardio(s, dur, dist, cal) },
                    onToggleDone = { onToggleSetDone(s) }
                )
            }
        } else {
            // Borderless Set Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.active_col_set),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp)
                )
                Text(
                    text = stringResource(R.string.active_col_kg),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.active_col_reps),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(48.dp))
            }

            sets.forEachIndexed { idx, s ->
                val isActive = idx == activeSetIdx
                val isDone = s.completedAt != null
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isActive -> CardElevated
                        isDone -> EmeraldRest.copy(alpha = 0.10f)
                        else -> Color.Transparent
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val typeSuffix = if (s.setType == SetType.WORK) "" else stringResource(s.setType.labelRes()).take(1)
                        Text(
                            text = "${idx + 1}$typeSuffix",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDone) EmeraldRest else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.width(40.dp)
                        )

                        val wDisplay = s.weightKg?.let { PrEngine.formatKg(it) } ?: "0"
                        Text(
                            text = wDisplay,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) ElectricBlue else MaterialTheme.colorScheme.onBackground,
                            textDecoration = if (isActive) TextDecoration.Underline else TextDecoration.None,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onTapSetValues(s) }
                                .padding(vertical = 4.dp)
                        )

                        val rDisplay = s.reps?.toString() ?: "0"
                        Text(
                            text = rDisplay,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) ElectricBlue else MaterialTheme.colorScheme.onBackground,
                            textDecoration = if (isActive) TextDecoration.Underline else TextDecoration.None,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onTapSetValues(s) }
                                .padding(vertical = 4.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isDone) EmeraldRest else CardSurface)
                                .clickable { onToggleSetDone(s) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isDone) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Ghost '+' Add Set / Remove row (Jefit 110755)
            val template = sets.lastOrNull()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddSet() }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.workout_add_set),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp)
                )
                Text(
                    text = template?.weightKg?.let { PrEngine.formatKg(it) } ?: "—",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = template?.reps?.toString() ?: "—",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f)
                )
                if (sets.size > 1) {
                    TextButton(onClick = onRemoveLastSet, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(
                            text = stringResource(R.string.active_remove_last_set),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }

            // 1-tap RPE pills for the most recently completed set
            if (lastCompletedSet != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        7f to R.string.workout_rpe_easy,
                        8.5f to R.string.workout_rpe_mod,
                        10f to R.string.workout_rpe_fail
                    ).forEach { (rpeVal, labelRes) ->
                        FilterChip(
                            selected = lastCompletedSet.rpe == rpeVal,
                            onClick = {
                                val next = if (lastCompletedSet.rpe == rpeVal) null else rpeVal
                                onUpdateRpe(lastCompletedSet, next)
                            },
                            label = { Text(stringResource(labelRes), maxLines = 1) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CardioSetCard(
    setNumber: Int,
    set: WorkoutSetEntity,
    onUpdate: (Int?, Double?, Double?) -> Unit,
    onToggleDone: () -> Unit
) {
    val isDone = set.completedAt != null
    var minText by remember(set.id, set.durationSec) {
        mutableStateOf(set.durationSec?.let { (it / 60).toString() }.orEmpty())
    }
    var distText by remember(set.id, set.distanceKm) {
        mutableStateOf(set.distanceKm?.let { PrEngine.formatKg(it) }.orEmpty())
    }
    var calText by remember(set.id, set.calories) {
        mutableStateOf(set.calories?.roundToInt()?.toString().orEmpty())
    }

    ObsidianCard(
        highlightedBorder = if (isDone) EmeraldRest else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#$setNumber", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isDone) EmeraldRest else CardElevated)
                        .clickable { onToggleDone() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minText,
                    onValueChange = {
                        minText = it
                        onUpdate(it.toIntOrNull()?.times(60), distText.replace(',', '.').toDoubleOrNull(), calText.toDoubleOrNull())
                    },
                    label = { Text(stringResource(R.string.active_cardio_duration)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = distText,
                    onValueChange = {
                        distText = it
                        onUpdate(minText.toIntOrNull()?.times(60), it.replace(',', '.').toDoubleOrNull(), calText.toDoubleOrNull())
                    },
                    label = { Text(stringResource(R.string.active_cardio_distance)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = calText,
                    onValueChange = {
                        calText = it
                        onUpdate(minText.toIntOrNull()?.times(60), distText.replace(',', '.').toDoubleOrNull(), it.toDoubleOrNull())
                    },
                    label = { Text(stringResource(R.string.active_cardio_calories)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SetEditBottomSheet(
    setNumber: Int,
    exerciseName: String,
    isBodyweight: Boolean,
    set: WorkoutSetEntity,
    onSave: (Double?, Int?, SetType) -> Unit,
    onOpenPlates: (Double) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var weightText by remember(set.id) { mutableStateOf(set.weightKg?.let { PrEngine.formatKg(it) } ?: "0") }
    var repsText by remember(set.id) { mutableStateOf(set.reps?.toString() ?: "10") }
    var setType by remember(set.id) { mutableStateOf(set.setType) }

    fun adjustWeight(delta: Double) {
        val cur = weightText.replace(',', '.').toDoubleOrNull() ?: 0.0
        weightText = PrEngine.formatKg((cur + delta).coerceAtLeast(0.0))
    }

    fun adjustReps(delta: Int) {
        val cur = repsText.toIntOrNull() ?: 0
        repsText = (cur + delta).coerceAtLeast(0).toString()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.active_edit_set, setNumber), style = MaterialTheme.typography.titleLarge)
                    Text(exerciseName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                }
            }

            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it },
                label = { Text(stringResource(R.string.active_col_kg)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(-5.0, -2.5, 2.5, 5.0).forEach { d ->
                    OutlinedButton(
                        onClick = { adjustWeight(d) },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (d > 0) "+${PrEngine.formatKg(d)}" else PrEngine.formatKg(d), maxLines = 1)
                    }
                }
            }

            OutlinedTextField(
                value = repsText,
                onValueChange = { repsText = it },
                label = { Text(stringResource(R.string.active_col_reps)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(-2, -1, 1, 2).forEach { d ->
                    OutlinedButton(
                        onClick = { adjustReps(d) },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (d > 0) "+$d" else "$d", maxLines = 1)
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SetType.entries.forEach { st ->
                    FilterChip(
                        selected = setType == st,
                        onClick = { setType = st },
                        label = { Text(stringResource(st.labelRes()), maxLines = 1) }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isBodyweight) {
                    val w = weightText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    OutlinedButton(
                        onClick = { onOpenPlates(w) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.workout_plates), maxLines = 1)
                    }
                }
                Button(
                    onClick = {
                        onSave(
                            weightText.replace(',', '.').toDoubleOrNull(),
                            repsText.toIntOrNull(),
                            setType
                        )
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AddExerciseDialog(
    exercises: List<ExerciseEntity>,
    isUk: Boolean,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, exercises) {
        val q = query.trim().lowercase()
        exercises.filter {
            q.isBlank() || it.nameEn.lowercase().contains(q) || it.nameUk.lowercase().contains(q)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_add_exercise)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.gym_search_exercises)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    itemsIndexed(filtered, key = { _, ex -> ex.id }) { _, ex ->
                        TextButton(
                            onClick = { onSelect(ex.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(ex.localizedName(isUk), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}
