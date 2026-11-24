package com.gymtracker.app.ui.today

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.app.R
import com.gymtracker.app.ui.common.formatDurationMinSec
import com.gymtracker.app.ui.common.formatInstantShort
import com.gymtracker.app.ui.common.labelRes
import com.gymtracker.app.ui.components.BodyMapCanvas
import com.gymtracker.app.ui.components.BodyMapMode
import com.gymtracker.app.ui.components.MiniBodyMap
import com.gymtracker.app.ui.components.computeRoutineDayMuscleBreakdown
import com.gymtracker.app.ui.components.computeWorkoutMuscleBreakdown
import com.gymtracker.app.ui.components.labelRes
import com.gymtracker.app.ui.theme.CardElevated
import com.gymtracker.app.ui.theme.CoralEnergy
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.ObsidianCard
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    onOpenActiveWorkout: (Long) -> Unit,
    onOpenWorkoutSummary: (Long) -> Unit,
    onOpenJefitImport: () -> Unit,
    onOpenMuscleRecovery: () -> Unit = {},
    onOpenWatchActivity: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        viewModel.refreshHealth()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (state.isRefreshingHc) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }

        // Active workout resume banner
        state.activeWorkout?.let { active ->
            item {
                ObsidianCard(
                    highlightedBorder = EmeraldRest,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    onClick = { onOpenActiveWorkout(active.workout.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = EmeraldRest)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.today_resume_workout),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = active.workout.title ?: stringResource(R.string.active_workout_title),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Button(
                            onClick = { onOpenActiveWorkout(active.workout.id) },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(stringResource(R.string.action_resume), maxLines = 1)
                        }
                    }
                }
            }
        }

        // HC Permissions Banner
        val perm = state.permissionState
        if (perm != null && !perm.coreReadGranted) {
            item {
                ObsidianCard(
                    highlightedBorder = GoldPr,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Watch, contentDescription = null, tint = GoldPr)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.today_hc_permission_banner),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val req = viewModel.requiredPermissionsToRequest()
                                if (req.isNotEmpty()) permLauncher.launch(req)
                            },
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(stringResource(R.string.today_grant_hc), maxLines = 1)
                        }
                    }
                }
            }
        }

        // Pending watch sync banner
        if (state.pendingHcCount > 0) {
            item {
                ObsidianCard(
                    containerColor = CardElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.today_pending_sync_banner, state.pendingHcCount),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { viewModel.syncPendingNow() }) {
                            Text(stringResource(R.string.today_sync_now), maxLines = 1)
                        }
                    }
                }
            }
        }

        // Start Workout Hero Card
        if (state.activeWorkout == null) {
            item {
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.today_start_workout),
                            style = MaterialTheme.typography.titleLarge
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                leadingIcon = { Icon(Icons.Default.Watch, contentDescription = null, tint = ElectricBlue) },
                                label = { Text(stringResource(R.string.gym_watch_reminder), maxLines = 2) }
                            )
                            FilterChip(
                                selected = state.tookPreWorkout,
                                onClick = { viewModel.setPreWorkout(!state.tookPreWorkout) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = if (state.tookPreWorkout) GoldPr else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                label = { Text(stringResource(R.string.today_preworkout_toggle), maxLines = 1) }
                            )
                        }

                        val days = state.routines.flatMap { it.days }.sortedBy { it.day.orderIndex }
                        if (days.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                days.forEach { d ->
                                    val (muscles, _) = computeRoutineDayMuscleBreakdown(d.exercises)
                                    ObsidianCard(
                                        containerColor = CardElevated,
                                        onClick = {
                                            viewModel.startWorkout(d.day.id, d.day.name, onOpenActiveWorkout)
                                        },
                                        modifier = Modifier.width(196.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            MiniBodyMap(
                                                muscleIntensity = muscles,
                                                modifier = Modifier.size(44.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = d.day.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${d.exercises.size} ${stringResource(R.string.unit_ex_short)}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.startWorkout(null, null, onOpenActiveWorkout) },
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.today_empty_workout),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // OHealth Watch Summary Card
        item {
            ObsidianCard(
                onClick = onOpenWatchActivity,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Watch, contentDescription = null, tint = ElectricBlue)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.today_ohealth_card),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.refreshHealth() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.today_watch_details),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val sum = state.todaySummary
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricTile(
                            label = stringResource(R.string.today_steps),
                            value = sum?.steps?.toString() ?: "—"
                        )
                        MetricTile(
                            label = stringResource(R.string.today_distance),
                            value = sum?.distanceKm?.let { "${String.format("%.1f", it)} ${stringResource(R.string.unit_km)}" } ?: "—"
                        )
                        MetricTile(
                            label = stringResource(R.string.today_active_kcal),
                            value = sum?.activeKcal?.let { "${it.roundToInt()}" } ?: "—"
                        )
                        MetricTile(
                            label = stringResource(R.string.today_sleep),
                            value = sum?.sleepMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "—"
                        )
                    }
                    sum?.steps?.let { steps ->
                        val goal = state.dailyStepGoal.coerceAtLeast(1000)
                        val prog = (steps.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { prog },
                            color = if (steps >= goal) EmeraldRest else ElectricBlue,
                            trackColor = CardElevated,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        MetricTile(
                            label = stringResource(R.string.today_spo2),
                            value = sum?.latestSpo2Percent?.let { "${it.roundToInt()}%" } ?: "—"
                        )
                        MetricTile(
                            label = stringResource(R.string.today_rhr),
                            value = sum?.latestRestingHr?.let { "$it ${stringResource(R.string.unit_bpm)}" } ?: "—"
                        )
                    }
                }
            }
        }

        // Muscle Recovery Status Card
        state.recovery?.let { rec ->
            item {
                val badgeColor = when {
                    rec.overallReadinessPercent >= 85 -> EmeraldRest
                    rec.overallReadinessPercent >= 60 -> GoldPr
                    else -> CoralEnergy
                }
                val recoveringItems = rec.items.filter { it.recoveryPercent < 100 }.sortedBy { it.recoveryPercent }
                val injuredItems = rec.items.filter { it.isInjured }

                ObsidianCard(
                    onClick = onOpenMuscleRecovery,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BodyMapCanvas(
                            muscleIntensity = rec.muscleFatigueMap,
                            showBack = true,
                            mode = BodyMapMode.RECOVERY,
                            injuredMuscles = rec.injuredMuscleSet,
                            modifier = Modifier.size(width = 96.dp, height = 84.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.recovery_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = badgeColor.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "${rec.overallReadinessPercent}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (recoveringItems.isEmpty() && injuredItems.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.recovery_all_fresh),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                recoveringItems.take(2).forEach { itItem ->
                                    Text(
                                        text = "${stringResource(itItem.group.labelRes())}: ${itItem.recoveryPercent}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (injuredItems.isNotEmpty()) {
                                    val injLabels = injuredItems.map { "${stringResource(it.group.labelRes())} (!)" }
                                    Text(
                                        text = injLabels.joinToString(", "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CoralEnergy
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Last Workout or Jefit Import CTA
        item {
            val last = state.lastWorkout
            if (last == null) {
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(stringResource(R.string.today_no_workouts), style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = onOpenJefitImport, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.today_import_cta))
                        }
                    }
                }
            } else {
                val (muscles, _) = computeWorkoutMuscleBreakdown(last.exercises)
                ObsidianCard(
                    onClick = { onOpenWorkoutSummary(last.workout.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniBodyMap(
                            muscleIntensity = muscles,
                            modifier = Modifier.size(64.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.today_last_workout),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricBlue,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = stringResource(last.workout.hcStatus.labelRes()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldRest
                                )
                            }
                            Text(
                                text = last.workout.title ?: stringResource(R.string.active_workout_title),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatInstantShort(last.workout.startedAt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val m = last.metrics
                            if (m != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(formatDurationMinSec(m.durationSec), fontWeight = FontWeight.SemiBold)
                                    Text("${m.volumeKg.roundToInt()} ${stringResource(R.string.unit_kg)}")
                                    Text(
                                        "${m.kcalSmartTotal.roundToInt()} ${stringResource(R.string.unit_kcal)}",
                                        color = CoralEnergy,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(m.avgHr?.let { "♥ $it" } ?: "—")
                                }
                            }
                            Text(
                                text = pluralStringResource(R.plurals.workouts_count, state.totalWorkoutCount, state.totalWorkoutCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
