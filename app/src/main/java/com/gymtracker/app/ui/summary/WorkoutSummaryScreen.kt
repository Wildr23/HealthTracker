package com.gymtracker.app.ui.summary

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.app.R
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.HcStatus
import com.gymtracker.app.domain.PrEngine
import com.gymtracker.app.ui.common.formatDateShort
import com.gymtracker.app.ui.common.formatDurationMinSec
import com.gymtracker.app.ui.common.formatInstantShort
import com.gymtracker.app.ui.common.isUkrainianLocale
import com.gymtracker.app.ui.common.labelRes
import com.gymtracker.app.ui.common.localizedName
import com.gymtracker.app.ui.components.BodyMapCanvas
import com.gymtracker.app.ui.components.BodyMapModalSheet
import com.gymtracker.app.ui.components.ChartExerciseBlock
import com.gymtracker.app.ui.components.MiniBodyMap
import com.gymtracker.app.ui.components.WorkoutHrChart
import com.gymtracker.app.ui.components.computeWorkoutMuscleBreakdown
import com.gymtracker.app.ui.components.labelRes
import com.gymtracker.app.ui.components.muscleIntensityForExercise
import com.gymtracker.app.ui.theme.CardElevated
import com.gymtracker.app.ui.theme.CoralEnergy
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.HrZoneColors
import com.gymtracker.app.ui.theme.ObsidianCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutSummaryScreen(
    onBack: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    viewModel: WorkoutSummaryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isUk = isUkrainianLocale()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBodyMapModal by remember { mutableStateOf(false) }

    val wf = state.workoutFull
    val dateTitle = remember(wf) {
        wf?.workout?.startedAt?.let { formatDateShort(it.atZone(ZoneId.systemDefault()).toLocalDate()) }
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
                        Text(
                            text = dateTitle ?: stringResource(R.string.summary_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        wf?.workout?.title?.let { t ->
                            Text(t, style = MaterialTheme.typography.labelMedium, color = ElectricBlue)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshWatchSync() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.summary_delete_workout))
                    }
                }
            )
        }
    ) { padding ->
        if (wf == null) return@Scaffold

        val orderedExercises = wf.exercises.sortedBy { it.workoutExercise.orderIndex }
        val setTimes = orderedExercises.flatMap { ex -> ex.sets.mapNotNull { it.completedAt } }
        val blocks = orderedExercises.mapNotNull { ex ->
            val start = ex.blockMetrics?.startTime ?: return@mapNotNull null
            val end = ex.blockMetrics.endTime ?: return@mapNotNull null
            ChartExerciseBlock(start, end, ex.exercise.localizedName(isUk))
        }
        val m = wf.metrics
        val (muscleIntensity, muscleBreakdown) = remember(orderedExercises) {
            computeWorkoutMuscleBreakdown(orderedExercises)
        }
        val startTimeStr = remember(wf.workout.startedAt) {
            DateTimeFormatter.ofPattern("HH:mm").format(wf.workout.startedAt.atZone(ZoneId.systemDefault()))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isSyncingHc) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            // Jefit-inspired Top Hero: Start Time Pill + Giant Duration + Front/Back BodyMap + 3-Col KPI Strip
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(shape = RoundedCornerShape(16.dp), color = CardElevated) {
                            Text(
                                text = stringResource(R.string.summary_started_at, startTimeStr),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        if (wf.workout.tookPreWorkout) {
                            AssistChip(
                                onClick = {},
                                leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null, tint = GoldPr) },
                                label = { Text(stringResource(R.string.summary_preworkout_badge)) }
                            )
                        }
                    }

                    if (state.pendingPostWorkoutSupplements.isNotEmpty()) {
                        ObsidianCard(
                            containerColor = CardElevated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.summary_post_workout_stack_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EmeraldRest
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    state.pendingPostWorkoutSupplements.forEach { dose ->
                                        val supp = dose.item
                                        val label = if (isUk) supp.nameUk else supp.nameEn
                                        AssistChip(
                                            onClick = { viewModel.logPostWorkoutSupplement(supp.id, dose.scheduleId) },
                                            label = { Text("+ $label", maxLines = 1) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (m != null) {
                        Text(
                            text = "${(m.durationSec / 60).coerceAtLeast(1)} ${stringResource(R.string.unit_min)}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Unlocked Interactive BodyMap Hero
                    ObsidianCard(
                        onClick = { showBodyMapModal = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BodyMapCanvas(
                                muscleIntensity = state.muscleAnalysis?.subMuscleIntensity ?: muscleIntensity,
                                showBack = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(175.dp)
                            )
                            val stimItems = state.muscleAnalysis?.groupResults.orEmpty()
                            if (stimItems.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    stimItems.take(4).forEach { sItem ->
                                        val pillColor = when {
                                            sItem.stimulusPercent >= 85 -> EmeraldRest
                                            sItem.stimulusPercent >= 50 -> ElectricBlue
                                            else -> GoldPr
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = pillColor.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = "${stringResource(sItem.group.labelRes())} • ${sItem.stimulusPercent}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = pillColor,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = stringResource(R.string.summary_tap_bodymap),
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricBlue
                            )
                        }
                    }

                    // 3-Column KPI Strip (Volume | Sets/Reps | Watch Kcal & HR)
                    if (m != null) {
                        ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatCol(
                                    label = stringResource(R.string.workout_volume_label),
                                    value = "${m.volumeKg.roundToInt()} ${stringResource(R.string.unit_kg)}"
                                )
                                StatCol(
                                    label = stringResource(R.string.summary_sets_reps_label),
                                    value = "${m.totalSets} × ${m.totalReps}"
                                )
                                StatCol(
                                    label = stringResource(R.string.summary_kcal_hr_label),
                                    value = "${m.kcalSmartTotal.roundToInt()} • ♥${m.avgHr ?: "—"}"
                                )
                            }
                        }
                    }
                }
            }

            // Smart Hybrid Calories + Watch Status Card
            if (m != null) {
                item {
                    ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = CoralEnergy)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.summary_smart_calories_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${m.kcalSmartTotal.roundToInt()} ${stringResource(R.string.unit_kcal)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = CoralEnergy,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val tons = String.format(Locale.US, "%.1f", m.volumeKg / 1000.0)
                            val breakdownText = if (m.hasWatchCalories) {
                                stringResource(
                                    R.string.summary_smart_breakdown,
                                    m.kcalWatch.roundToInt(),
                                    tons,
                                    m.kcalAnaerobicDelta.roundToInt(),
                                    m.kcalEpoc.roundToInt()
                                )
                            } else {
                                stringResource(
                                    R.string.summary_smart_estimated_breakdown,
                                    m.kcalAnaerobicDelta.roundToInt(),
                                    m.kcalEpoc.roundToInt()
                                )
                            }
                            Text(
                                text = breakdownText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AssistChip(
                                    onClick = { viewModel.refreshWatchSync() },
                                    leadingIcon = { Icon(Icons.Default.Watch, contentDescription = null, tint = EmeraldRest) },
                                    label = { Text(stringResource(wf.workout.hcStatus.labelRes()), maxLines = 1) }
                                )
                                OutlinedButton(
                                    onClick = { viewModel.loadCandidateSessions() },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(stringResource(R.string.summary_rematch), maxLines = 1)
                                }
                                if (wf.workout.hcStatus == HcStatus.MATCHED) {
                                    IconButton(onClick = { viewModel.unlinkWatchSession() }) {
                                        Icon(Icons.Default.LinkOff, contentDescription = stringResource(R.string.summary_unlink))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Heart Rate Chart + Zones Card
            if (state.hrSamples.isNotEmpty() || m?.avgHr != null) {
                item {
                    ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = CoralEnergy)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.summary_hr_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            if (m != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    StatCol(stringResource(R.string.summary_avg_hr), m.avgHr?.let { "$it bpm" } ?: "—")
                                    StatCol(stringResource(R.string.summary_max_hr), m.maxHr?.let { "$it bpm" } ?: "—")
                                    StatCol(stringResource(R.string.summary_trimp), String.format(Locale.US, "%.1f", m.edwardsTrimp))
                                }
                            }
                            if (state.hrSamples.isNotEmpty()) {
                                WorkoutHrChart(
                                    samples = state.hrSamples,
                                    setTimes = setTimes,
                                    blocks = blocks,
                                    hrMax = state.hrMax
                                )
                            }
                            if (m != null && m.zoneSeconds.sum() > 0) {
                                val totalZ = m.zoneSeconds.sum().toFloat()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                ) {
                                    m.zoneSeconds.forEachIndexed { idx, sec ->
                                        if (sec > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(sec / totalZ)
                                                    .height(10.dp)
                                                    .background(HrZoneColors[idx])
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    m.zoneSeconds.forEachIndexed { idx, sec ->
                                        Text(
                                            text = "Z${idx + 1}:${sec / 60}m",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = HrZoneColors[idx]
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.summary_exercises_header, orderedExercises.size),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Exercise Logs (Jefit 110928 clean table + Superset dashed connectors + Best 1RM in ElectricBlue)
            itemsIndexed(orderedExercises, key = { _, it -> it.workoutExercise.id }) { exIdx, exWithSets ->
                val ex = exWithSets.exercise
                val block = exWithSets.blockMetrics
                val sortedSets = exWithSets.sets.sortedBy { it.setIndex }
                val bestE1rm = sortedSets.mapNotNull { state.setMetricsById[it.id]?.e1rmEpley }.maxOrNull()

                val curSuperset = exWithSets.workoutExercise.supersetGroup ?: state.inferredSupersetsByExId[ex.id]
                val nextItem = orderedExercises.getOrNull(exIdx + 1)
                val nextSuperset = nextItem?.let {
                    it.workoutExercise.supersetGroup ?: state.inferredSupersetsByExId[it.exercise.id]
                }
                val hasSupersetNext = curSuperset != null && curSuperset == nextSuperset

                ObsidianCard(
                    onClick = { onOpenExercise(ex.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MiniBodyMap(
                                muscleIntensity = muscleIntensityForExercise(ex),
                                modifier = Modifier.size(44.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ex.localizedName(isUk),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                block?.maxHr?.let { maxB ->
                                    Text(
                                        text = "♥ ${block.avgHr ?: "—"}/$maxB bpm",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CoralEnergy
                                    )
                                }
                            }
                            Icon(Icons.Default.BarChart, contentDescription = null, tint = ElectricBlue)
                        }

                        exWithSets.workoutExercise.note?.takeIf { it.isNotBlank() }?.let { note ->
                            Text("📝 $note", style = MaterialTheme.typography.bodyMedium, color = GoldPr)
                        }

                        // Table Header
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.active_col_set), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(34.dp))
                            Text(
                                text = if (ex.type == ExerciseType.CARDIO) stringResource(R.string.active_col_dur) else "${stringResource(R.string.active_col_kg)} × ${stringResource(R.string.active_col_reps)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (ex.type == ExerciseType.WEIGHTED) {
                                Text(stringResource(R.string.summary_best_1rm), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                        sortedSets.forEachIndexed { idx, s ->
                            val sm = state.setMetricsById[s.id]
                            val mainStr = when (ex.type) {
                                ExerciseType.CARDIO -> {
                                    val min = (s.durationSec ?: 0) / 60
                                    val dist = s.distanceKm?.let { " • ${PrEngine.formatKg(it)} ${stringResource(R.string.unit_km)}" }.orEmpty()
                                    "${min} ${stringResource(R.string.unit_min)}$dist"
                                }
                                ExerciseType.BODYWEIGHT -> "${s.reps ?: 0} ${stringResource(R.string.workout_reps_hint)}"
                                ExerciseType.WEIGHTED -> "${PrEngine.formatKg(s.weightKg ?: 0.0)} ${stringResource(R.string.unit_kg)} × ${s.reps ?: 0}"
                            }
                            val e1 = sm?.e1rmEpley
                            val isBestSet = e1 != null && bestE1rm != null && kotlin.math.abs(e1 - bestE1rm) < 0.01

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${idx + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(34.dp))
                                    Text(mainStr, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    if (e1 != null) {
                                        Text(
                                            text = "${PrEngine.formatKg(e1)} ${stringResource(R.string.unit_kg)}",
                                            color = if (isBestSet) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isBestSet) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                                if (sm?.peakHr != null || sm?.restSec != null || sm?.hrRecovery60 != null) {
                                    val approxPrefix = if (sm.sampleCount < 2 && sm.peakHr != null) "≈" else ""
                                    val peakStr = sm.peakHr?.let { "$approxPrefix$it" } ?: "—"
                                    val restStr = sm.restSec?.let { formatDurationMinSec(it) } ?: "—"
                                    val recStr = sm.hrRecovery60?.let { drop -> if (drop >= 0) "-$drop" else "+${-drop}" } ?: "—"
                                    Text(
                                        text = stringResource(R.string.summary_per_set_hr, peakStr, restStr, recStr),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 34.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (hasSupersetNext) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 30.dp)
                            .height(22.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        Canvas(modifier = Modifier.width(18.dp).height(22.dp)) {
                            drawLine(
                                color = lineColor,
                                start = Offset(size.width / 2f, 0f),
                                end = Offset(size.width / 2f, size.height),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                            )
                        }
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.background, CircleShape)
                        )
                    }
                }
            }
        }

        if (showBodyMapModal) {
            BodyMapModalSheet(
                title = wf.workout.title ?: dateTitle.orEmpty(),
                muscleIntensity = state.muscleAnalysis?.subMuscleIntensity ?: muscleIntensity,
                breakdown = muscleBreakdown,
                stimulusItems = state.muscleAnalysis?.groupResults.orEmpty(),
                onDismiss = { showBodyMapModal = false }
            )
        }
    }

    state.candidateSessions?.let { list ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCandidates() },
            title = { Text(stringResource(R.string.summary_pick_session_title)) },
            text = {
                if (list.isEmpty()) {
                    Text(stringResource(R.string.summary_no_candidate_sessions))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.forEach { cand ->
                            OutlinedButton(
                                onClick = { viewModel.selectCandidateSession(cand.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(cand.title ?: cand.exerciseTypeName)
                                    Text(
                                        "${formatInstantShort(cand.startTime)} → ${formatInstantShort(cand.endTime)}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissCandidates() }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.summary_delete_workout)) },
            text = { Text(stringResource(R.string.summary_delete_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteWorkout(onBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun StatCol(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
