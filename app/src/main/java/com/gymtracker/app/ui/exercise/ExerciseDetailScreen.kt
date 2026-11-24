package com.gymtracker.app.ui.exercise

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.app.R
import com.gymtracker.app.data.db.CalorieTier
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.OneRmFormula
import com.gymtracker.app.data.db.SetType
import com.gymtracker.app.domain.PrEngine
import com.gymtracker.app.ui.common.formatDateShort
import com.gymtracker.app.ui.common.formatDurationMinSec
import com.gymtracker.app.ui.common.isUkrainianLocale
import com.gymtracker.app.ui.common.labelRes
import com.gymtracker.app.ui.common.localizedName
import com.gymtracker.app.ui.components.BodyMapCanvas
import com.gymtracker.app.ui.components.BodyMapMode
import com.gymtracker.app.ui.components.MiniBodyMap
import com.gymtracker.app.ui.components.RelativeStrengthChart
import com.gymtracker.app.ui.components.StrengthProgressionChart
import com.gymtracker.app.ui.components.VolumeBarChart
import com.gymtracker.app.ui.components.muscleIntensityForExercise
import com.gymtracker.app.ui.theme.CardElevated
import com.gymtracker.app.ui.theme.CardSurface
import com.gymtracker.app.ui.theme.CoralEnergy
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.ObsidianCard
import com.gymtracker.app.ui.theme.TealAccent
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isUk = isUkrainianLocale()
    val ex = state.exercise
    val analysis = state.fullAnalysis

    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0=History, 1=Chart, 2=Guide
    var chartMode by rememberSaveable { mutableIntStateOf(0) } // 0=1RM, 1=Volume
    var showGoalDialog by remember { mutableStateOf(false) }

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
                title = {},
                actions = {
                    if (ex?.type == ExerciseType.WEIGHTED) {
                        val hasGoal = state.goalKg != null && state.goalKg!! > 0.0
                        Surface(
                            onClick = { showGoalDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            color = if (hasGoal) CardElevated else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (hasGoal) EmeraldRest.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = if (hasGoal) EmeraldRest else ElectricBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (hasGoal) {
                                        stringResource(R.string.ex_goal_badge, PrEngine.formatKg(state.goalKg!!))
                                    } else {
                                        stringResource(R.string.ex_set_goal)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (hasGoal) MaterialTheme.colorScheme.onBackground else ElectricBlue,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (ex == null || analysis == null) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Exercise Title Header matching Jefit History/Chart/Guide header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = ex.localizedName(isUk),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sub = if (isUk) {
                        "${ex.nameEn} • ${stringResource(ex.primaryMuscle.labelRes())}"
                    } else {
                        stringResource(ex.primaryMuscle.labelRes())
                    }
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MiniBodyMap(
                    muscleIntensity = muscleIntensityForExercise(ex),
                    modifier = Modifier.size(56.dp)
                )
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.ex_tab_history), maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.ex_tab_chart), maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.ex_tab_guide), maxLines = 1) }
                )
            }

            when (selectedTab) {
                0 -> ExerciseHistoryTab(
                    exType = ex.type,
                    sessions = state.historySessions,
                    onOpenWorkout = onOpenWorkout
                )
                1 -> ExerciseChartTab(
                    state = state,
                    chartMode = chartMode,
                    onChangeChartMode = { chartMode = it },
                    onSelectRange = viewModel::setTimeRange,
                    onSelectFormula = viewModel::setFormula,
                    onToggleMaxReps10 = viewModel::setMaxReps10Only,
                    onToggleCompactBreaks = viewModel::setCompactBreaks,
                    onSelectPoint = viewModel::selectPoint
                )
                2 -> ExerciseGuideTab(state = state)
            }
        }
    }

    if (showGoalDialog && ex != null) {
        var input by remember(state.goalKg) {
            mutableStateOf(state.goalKg?.let { PrEngine.formatKg(it) }.orEmpty())
        }
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text(stringResource(R.string.ex_goal_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(stringResource(R.string.unit_kg)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val v = input.replace(',', '.').toDoubleOrNull()
                    if (v != null && v > 0.0) {
                        viewModel.setGoalKg(v)
                    }
                    showGoalDialog = false
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.goalKg != null) {
                        TextButton(onClick = {
                            viewModel.setGoalKg(null)
                            showGoalDialog = false
                        }) {
                            Text(stringResource(R.string.ex_goal_clear), color = CoralEnergy)
                        }
                    }
                    TextButton(onClick = { showGoalDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        )
    }
}

@Composable
private fun ExerciseHistoryTab(
    exType: ExerciseType,
    sessions: List<ExerciseHistorySessionUi>,
    onOpenWorkout: (Long) -> Unit
) {
    if (sessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.ex_no_history),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sessions, key = { it.workoutId }) { session ->
            ObsidianCard(
                onClick = { onOpenWorkout(session.workoutId) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = formatDateShort(session.date),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (session.prBadges.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = GoldPr.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "🏆 PR",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = GoldPr,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            session.workoutTitle?.takeIf { it.isNotBlank() }?.let { title ->
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Jefit-style borderless 4-column table header
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.active_col_set),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(34.dp)
                        )
                        Text(
                            text = if (exType == ExerciseType.CARDIO) stringResource(R.string.active_col_dur) else stringResource(R.string.ex_col_weight_reps),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.ex_col_rest),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(56.dp)
                        )
                        if (exType == ExerciseType.WEIGHTED) {
                            Text(
                                text = stringResource(R.string.summary_best_1rm),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(74.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

                    session.sets.forEach { set ->
                        val weightRepsText = when (exType) {
                            ExerciseType.CARDIO -> {
                                val min = (set.durationSec ?: 0) / 60
                                val dist = set.distanceKm?.let { " • ${PrEngine.formatKg(it)} ${stringResource(R.string.unit_km)}" }.orEmpty()
                                "$min ${stringResource(R.string.unit_min)}$dist"
                            }
                            ExerciseType.BODYWEIGHT -> "${set.reps ?: 0} ${stringResource(R.string.workout_reps_hint)}"
                            ExerciseType.WEIGHTED -> {
                                val w = PrEngine.formatKg(set.weightKg ?: 0.0)
                                "$w ${stringResource(R.string.unit_kg)} × ${set.reps ?: 0}"
                            }
                        }
                        val typeTag = when (set.setType) {
                            SetType.WARMUP -> " (W)"
                            SetType.DROP -> " (D)"
                            SetType.FAILURE -> " (F)"
                            SetType.WORK -> ""
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${set.setNumber}",
                                fontWeight = FontWeight.SemiBold,
                                color = if (set.setType == SetType.WARMUP) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.width(34.dp)
                            )
                            Text(
                                text = "$weightRepsText$typeTag",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = if (set.setType == SetType.WARMUP) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = set.restSec?.let { formatDurationMinSec(it) } ?: "—",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(56.dp)
                            )
                            if (exType == ExerciseType.WEIGHTED) {
                                Text(
                                    text = set.e1rmKg?.let { "${PrEngine.formatKg(it)} ${stringResource(R.string.unit_kg)}" } ?: "—",
                                    fontWeight = if (set.isBestSet) FontWeight.Bold else FontWeight.Normal,
                                    color = if (set.isBestSet) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(74.dp)
                                )
                            }
                        }
                    }

                    session.note?.takeIf { it.isNotBlank() }?.let { n ->
                        Text(
                            text = "📝 $n",
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldPr
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseChartTab(
    state: ExerciseDetailUiState,
    chartMode: Int,
    onChangeChartMode: (Int) -> Unit,
    onSelectRange: (ChartTimeRange) -> Unit,
    onSelectFormula: (OneRmFormula) -> Unit,
    onToggleMaxReps10: (Boolean) -> Unit,
    onToggleCompactBreaks: (Boolean) -> Unit,
    onSelectPoint: (com.gymtracker.app.domain.SessionPrPoint?) -> Unit
) {
    val ex = state.exercise ?: return
    val analysis = state.fullAnalysis ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (ex.type == ExerciseType.WEIGHTED) {
            // Jefit-inspired pill time range selector
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(
                            ChartTimeRange.D14 to R.string.ex_range_14d,
                            ChartTimeRange.M1 to R.string.ex_range_1m,
                            ChartTimeRange.M3 to R.string.ex_range_3m,
                            ChartTimeRange.M6 to R.string.ex_range_6m,
                            ChartTimeRange.Y1 to R.string.ex_range_1y,
                            ChartTimeRange.ALL to R.string.ex_range_all
                        ).forEach { (tr, lblRes) ->
                            val active = state.timeRange == tr
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (active) ElectricBlue else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onSelectRange(tr) }
                            ) {
                                Text(
                                    text = stringResource(lblRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Chart Metric Toggle (1 Rep Max | Volume) + Formula & Break options
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = chartMode == 0,
                            onClick = { onChangeChartMode(0) },
                            label = { Text(stringResource(R.string.ex_chart_1rm), maxLines = 1) }
                        )
                        FilterChip(
                            selected = chartMode == 1,
                            onClick = { onChangeChartMode(1) },
                            label = { Text(stringResource(R.string.ex_chart_volume), maxLines = 1) }
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        OneRmFormula.entries.forEach { f ->
                            FilterChip(
                                selected = state.formula == f,
                                onClick = { onSelectFormula(f) },
                                label = { Text(f.name, maxLines = 1) }
                            )
                        }
                        FilterChip(
                            selected = state.maxReps10Only,
                            onClick = { onToggleMaxReps10(!state.maxReps10Only) },
                            label = { Text(stringResource(R.string.ex_detail_max_reps_10), maxLines = 1) }
                        )
                        FilterChip(
                            selected = state.compactBreaks,
                            onClick = { onToggleCompactBreaks(!state.compactBreaks) },
                            label = {
                                Text(
                                    text = stringResource(
                                        if (state.compactBreaks) R.string.ex_detail_compact_breaks
                                        else R.string.ex_detail_calendar_axis
                                    ),
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }

            // Chart Card
            item {
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (chartMode == 0) {
                            Text(stringResource(R.string.ex_detail_chart_title), style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("● ${stringResource(R.string.ex_detail_legend_e1rm)}", color = GoldPr, style = MaterialTheme.typography.labelMedium)
                                Text("● ${stringResource(R.string.ex_detail_legend_max_w)}", color = ElectricBlue, style = MaterialTheme.typography.labelMedium)
                                Text("| ${stringResource(R.string.ex_detail_legend_break)}", color = CoralEnergy, style = MaterialTheme.typography.labelMedium)
                            }
                            StrengthProgressionChart(
                                points = state.filteredPoints,
                                breaks = if (state.timeRange == ChartTimeRange.ALL) analysis.trainingBreaks else emptyList(),
                                compactBreaks = state.compactBreaks,
                                selectedPoint = state.selectedPoint,
                                onSelectPoint = onSelectPoint,
                                goalKg = state.goalKg,
                                goalLabel = state.goalKg?.let { stringResource(R.string.ex_goal_line_label, PrEngine.formatKg(it)) }
                            )
                        } else {
                            Text(stringResource(R.string.ex_detail_volume_title), style = MaterialTheme.typography.titleMedium)
                            VolumeBarChart(points = state.filteredPoints)
                        }

                        state.selectedPoint?.let { sel ->
                            ObsidianCard(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${formatDateShort(sel.date)} • ${sel.bestSetLabel}", fontWeight = FontWeight.Bold)
                                    Text(sel.allSetsSummary, style = MaterialTheme.typography.bodyMedium)
                                    sel.peakHr?.let { hr -> Text("Peak HR: $hr bpm", color = CoralEnergy, style = MaterialTheme.typography.labelMedium) }
                                    sel.note?.let { n -> Text(n, color = GoldPr, style = MaterialTheme.typography.labelMedium) }
                                }
                            }
                        }
                    }
                }
            }

            // Min / Max KPI cards for selected period (matching Jefit Chart tab)
            if (state.filteredPoints.isNotEmpty()) {
                item {
                    val minPt = if (chartMode == 0) {
                        state.filteredPoints.filter { it.bestE1rmKg != null }.minByOrNull { it.bestE1rmKg!! }
                    } else {
                        state.filteredPoints.minByOrNull { it.totalVolumeKg }
                    }
                    val maxPt = if (chartMode == 0) {
                        state.filteredPoints.filter { it.bestE1rmKg != null }.maxByOrNull { it.bestE1rmKg!! }
                    } else {
                        state.filteredPoints.maxByOrNull { it.totalVolumeKg }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ObsidianCard(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val minVal = if (chartMode == 0) minPt?.bestE1rmKg?.let { "${PrEngine.formatKg(it)} ${stringResource(R.string.unit_kg)}" } else minPt?.let { "${it.totalVolumeKg.roundToInt()} ${stringResource(R.string.unit_kg)}" }
                                Text(minVal ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${stringResource(R.string.ex_chart_min)}${minPt?.let { " (${formatDateShort(it.date)})" }.orEmpty()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        ObsidianCard(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val maxVal = if (chartMode == 0) maxPt?.bestE1rmKg?.let { "${PrEngine.formatKg(it)} ${stringResource(R.string.unit_kg)}" } else maxPt?.let { "${it.totalVolumeKg.roundToInt()} ${stringResource(R.string.unit_kg)}" }
                                Text(maxVal ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ElectricBlue)
                                Text(
                                    text = "${stringResource(R.string.ex_chart_max)}${maxPt?.let { " (${formatDateShort(it.date)})" }.orEmpty()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 5-PR Trophy Grid
            item {
                val recs = analysis.records
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = GoldPr)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ex_detail_prs_title), style = MaterialTheme.typography.titleMedium)
                        }
                        PrRow(
                            label = stringResource(R.string.pr_best_e1rm),
                            value = recs.bestE1rmKg?.let { "${PrEngine.formatKg(it)} ${stringResource(R.string.unit_kg)} (${recs.bestE1rmSetLabel.orEmpty()})" } ?: "—",
                            date = recs.bestE1rmDate?.let(::formatDateShort)
                        )
                        PrRow(
                            label = stringResource(R.string.pr_max_weight),
                            value = recs.maxWeightKg?.let { "${PrEngine.formatKg(it)} ${stringResource(R.string.unit_kg)} × ${recs.maxWeightReps ?: "-"}" } ?: "—",
                            date = recs.maxWeightDate?.let(::formatDateShort)
                        )
                        PrRow(
                            label = stringResource(R.string.pr_max_reps_weight),
                            value = recs.maxRepsAtTopWeightLabel ?: "—",
                            date = null
                        )
                        PrRow(
                            label = stringResource(R.string.pr_max_set_vol),
                            value = recs.maxSetVolumeKg?.let { "${it.roundToInt()} ${stringResource(R.string.unit_kg)} (${recs.maxSetVolumeLabel.orEmpty()})" } ?: "—",
                            date = recs.maxSetVolumeDate?.let(::formatDateShort)
                        )
                        PrRow(
                            label = stringResource(R.string.pr_max_session_vol),
                            value = recs.maxSessionVolumeKg?.let { "${it.roundToInt()} ${stringResource(R.string.unit_kg)}" } ?: "—",
                            date = recs.maxSessionVolumeDate?.let(::formatDateShort)
                        )
                    }
                }
            }

            // Era Comeback Comparison Card
            val era = analysis.eraComparison
            if (era.era2019To2021PeakE1rm != null || era.era2023PeakE1rm != null) {
                item {
                    ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(stringResource(R.string.ex_detail_era_title), style = MaterialTheme.typography.titleMedium)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                EraCol(stringResource(R.string.ex_detail_era_2019), era.era2019To2021PeakE1rm)
                                EraCol(stringResource(R.string.ex_detail_era_2023), era.era2023PeakE1rm)
                                EraCol(stringResource(R.string.ex_detail_era_2026), era.current2026PlusE1rm)
                            }
                            era.recoveryVsBestPriorPercent?.let { pct ->
                                Text(
                                    text = stringResource(R.string.ex_detail_era_recovery, pct),
                                    color = if (pct >= 100) GoldPr else ElectricBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Working Weights % Table
            if (state.repMaxRows.isNotEmpty()) {
                item {
                    ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(stringResource(R.string.ex_detail_rep_max_title), style = MaterialTheme.typography.titleMedium)
                            state.repMaxRows.forEach { row ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${row.percent}%", fontWeight = FontWeight.Bold)
                                    Text("~${row.estimatedMaxReps} ${stringResource(R.string.workout_reps_hint)}")
                                    Text("${PrEngine.formatKg(row.targetWeightKg)} ${stringResource(R.string.unit_kg)}", color = ElectricBlue)
                                }
                            }
                        }
                    }
                }
            }

            // Relative Strength Chart
            if (state.relativeStrengthRatios.isNotEmpty()) {
                item {
                    ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.ex_detail_rel_title), style = MaterialTheme.typography.titleMedium)
                            RelativeStrengthChart(ratios = state.relativeStrengthRatios)
                        }
                    }
                }
            }
        } else {
            // Cardio / Bodyweight chart fallback
            item {
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ex_detail_volume_title), style = MaterialTheme.typography.titleMedium)
                        VolumeBarChart(points = state.filteredPoints)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseGuideTab(state: ExerciseDetailUiState) {
    val ex = state.exercise ?: return
    val intensity = remember(ex) { muscleIntensityForExercise(ex) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ex_guide_target_muscles),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    BodyMapCanvas(
                        muscleIntensity = intensity,
                        showBack = true,
                        mode = BodyMapMode.FOCUS,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(225.dp)
                    )
                }
            }
        }

        if (state.muscleShares.isNotEmpty()) {
            item {
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.muscleShares.forEachIndexed { idx, (muscle, pct) ->
                            val barColor = if (idx == 0) ElectricBlue else ElectricBlue.copy(alpha = 0.65f)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(muscle.labelRes()),
                                        fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Medium,
                                        color = if (idx == 0) ElectricBlue else MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "$pct%",
                                        fontWeight = FontWeight.Bold,
                                        color = barColor
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (pct / 100f).coerceIn(0.05f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = barColor,
                                    trackColor = CardElevated,
                                    gapSize = 0.dp,
                                    drawStopIndicator = {}
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(stringResource(R.string.ex_guide_details), style = MaterialTheme.typography.titleMedium)
                    val typeText = when (ex.type) {
                        ExerciseType.WEIGHTED -> stringResource(R.string.ex_type_weighted)
                        ExerciseType.BODYWEIGHT -> stringResource(R.string.ex_type_bodyweight)
                        ExerciseType.CARDIO -> stringResource(R.string.ex_type_cardio)
                    }
                    val tierText = when (ex.calorieTier) {
                        CalorieTier.TIER_1_LOWER_COMPOUND -> stringResource(R.string.ex_tier_lower)
                        CalorieTier.TIER_2_UPPER_COMPOUND -> stringResource(R.string.ex_tier_upper)
                        CalorieTier.TIER_3_ISOLATION -> stringResource(R.string.ex_tier_isolation)
                        CalorieTier.CARDIO -> stringResource(R.string.ex_tier_cardio)
                    }
                    GuideDetailRow(stringResource(R.string.ex_guide_type), typeText)
                    GuideDetailRow(stringResource(R.string.ex_guide_tier), tierText)
                    GuideDetailRow(stringResource(R.string.ex_guide_sessions), "${state.sessionCount}")
                }
            }
        }
    }
}

@Composable
private fun GuideDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PrRow(label: String, value: String, date: String?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
        date?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun EraCol(label: String, valueKg: Double?) {
    Column {
        Text(
            text = valueKg?.let { "${PrEngine.formatKg(it)} ${stringResource(R.string.unit_kg)}" } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
