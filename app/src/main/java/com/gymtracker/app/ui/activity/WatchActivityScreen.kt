package com.gymtracker.app.ui.activity

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.app.R
import com.gymtracker.app.data.health.CachedWatchActivity
import com.gymtracker.app.ui.common.formatDateShort
import com.gymtracker.app.ui.common.formatDurationMinSec
import com.gymtracker.app.ui.common.formatInstantShort
import com.gymtracker.app.ui.components.WatchDailyBarChart
import com.gymtracker.app.ui.scale.ScaleBadgePill
import com.gymtracker.app.ui.theme.CoralEnergy
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.ObsidianCard
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchActivityScreen(
    onBack: () -> Unit,
    onOpenConnectedDevices: () -> Unit,
    viewModel: WatchActivityViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedDayIdx by remember(state.selectedDays, state.chartMetric) { mutableStateOf<Int?>(null) }

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
                title = { Text(stringResource(R.string.watch_activity_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
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
            if (state.isSyncing) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            if (state.permissionState != null && state.permissionState?.distanceReadGranted == false) {
                item {
                    ObsidianCard(highlightedBorder = GoldPr, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = GoldPr)
                            Text(
                                text = stringResource(R.string.watch_distance_banner),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = onOpenConnectedDevices, shape = RoundedCornerShape(16.dp)) {
                                Text(stringResource(R.string.action_setup), maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Daily Steps / Distance / Kcal Bar Chart Card
            item {
                val dates = state.dailyHistory.mapNotNull { runCatching { LocalDate.parse(it.dateIso) }.getOrNull() }
                val values = state.dailyHistory.map { day ->
                    when (state.chartMetric) {
                        WatchChartMetric.STEPS -> day.steps.toDouble()
                        WatchChartMetric.DISTANCE_KM -> day.distanceKm
                        WatchChartMetric.ACTIVE_KCAL -> day.activeKcal
                    }
                }
                val goalVal = if (state.chartMetric == WatchChartMetric.STEPS) state.dailyStepGoal.toDouble() else null
                val accent = when (state.chartMetric) {
                    WatchChartMetric.STEPS -> ElectricBlue
                    WatchChartMetric.DISTANCE_KM -> EmeraldRest
                    WatchChartMetric.ACTIVE_KCAL -> CoralEnergy
                }

                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    7 to R.string.watch_activity_days_7,
                                    14 to R.string.watch_activity_days_14,
                                    30 to R.string.watch_activity_days_30
                                ).forEach { (d, lbl) ->
                                    FilterChip(
                                        selected = state.selectedDays == d,
                                        onClick = { viewModel.selectDays(d) },
                                        label = { Text(stringResource(lbl), maxLines = 1) }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                WatchChartMetric.STEPS to R.string.watch_activity_tab_steps,
                                WatchChartMetric.DISTANCE_KM to R.string.watch_activity_tab_distance,
                                WatchChartMetric.ACTIVE_KCAL to R.string.watch_activity_tab_kcal
                            ).forEach { (m, lbl) ->
                                FilterChip(
                                    selected = state.chartMetric == m,
                                    onClick = { viewModel.selectMetric(m) },
                                    label = { Text(stringResource(lbl), maxLines = 1) }
                                )
                            }
                        }

                        if (values.isNotEmpty()) {
                            val avg = values.average()
                            val total = values.sum()
                            val goalDays = state.dailyHistory.count { it.steps >= state.dailyStepGoal }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SummaryMiniStat(
                                    label = stringResource(R.string.watch_activity_avg_day),
                                    value = formatWatchMetric(avg, state.chartMetric)
                                )
                                SummaryMiniStat(
                                    label = stringResource(R.string.watch_activity_total),
                                    value = formatWatchMetric(total, state.chartMetric)
                                )
                                SummaryMiniStat(
                                    label = stringResource(R.string.watch_activity_goal_days),
                                    value = "$goalDays / ${state.dailyHistory.size}"
                                )
                            }

                            WatchDailyBarChart(
                                dates = dates,
                                values = values,
                                goalValue = goalVal,
                                accentColor = accent,
                                selectedIndex = selectedDayIdx,
                                onSelectIndex = { selectedDayIdx = it }
                            )

                            selectedDayIdx?.takeIf { it in state.dailyHistory.indices }?.let { idx ->
                                val item = state.dailyHistory[idx]
                                val d = dates.getOrNull(idx)
                                if (d != null) {
                                    Text(
                                        text = "${formatDateShort(d)}: ${item.steps} ${stringResource(R.string.today_steps).lowercase()} • ${String.format("%.2f", item.distanceKm)} ${stringResource(R.string.unit_km)} • ${item.activeKcal.roundToInt()} ${stringResource(R.string.unit_kcal)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ElectricBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Watch Workouts / Walks / Runs Filter & Feed
            item {
                Text(
                    text = stringResource(R.string.watch_activity_sessions_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        WatchActivityFilter.ALL to R.string.watch_filter_all,
                        WatchActivityFilter.WALK to R.string.watch_filter_walk,
                        WatchActivityFilter.RUN to R.string.watch_filter_run,
                        WatchActivityFilter.BIKE to R.string.watch_filter_bike,
                        WatchActivityFilter.OTHER to R.string.watch_filter_other
                    ).forEach { (flt, lbl) ->
                        FilterChip(
                            selected = state.filter == flt,
                            onClick = { viewModel.selectFilter(flt) },
                            label = { Text(stringResource(lbl), maxLines = 1) }
                        )
                    }
                }
            }

            if (state.activities.isEmpty()) {
                item {
                    ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Watch, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = stringResource(R.string.watch_activity_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.activities, key = { it.id }) { session ->
                    WatchSessionRowCard(session = session)
                }
            }
        }
    }
}

@Composable
private fun SummaryMiniStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WatchSessionRowCard(session: CachedWatchActivity) {
    val icon = activityIcon(session.exerciseTypeName)
    val typeLabel = stringResource(activityLabelRes(session.exerciseTypeName))
    val title = session.title?.takeIf { it.isNotBlank() } ?: typeLabel
    val kcal = session.totalKcal ?: session.activeKcal

    ObsidianCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatInstantShort(Instant.ofEpochSecond(session.startEpochSec))} • ${formatDurationMinSec(session.durationSec.toInt())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    session.distanceKm?.let { km ->
                        val paceStr = formatPaceOrSpeed(session.exerciseTypeName, km, session.durationSec)
                        ScaleBadgePill(
                            text = "${String.format("%.2f", km)} ${stringResource(R.string.unit_km)}${paceStr?.let { " • $it" }.orEmpty()}",
                            color = EmeraldRest
                        )
                    }
                    kcal?.let { k ->
                        ScaleBadgePill(
                            text = "${k.roundToInt()} ${stringResource(R.string.unit_kcal)}",
                            color = CoralEnergy
                        )
                    }
                    session.avgHr?.let { hr ->
                        ScaleBadgePill(
                            text = "♥ $hr${session.maxHr?.let { "/$it" }.orEmpty()}",
                            color = GoldPr
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun formatWatchMetric(value: Double, metric: WatchChartMetric): String = when (metric) {
    WatchChartMetric.STEPS -> "${value.roundToInt()}"
    WatchChartMetric.DISTANCE_KM -> "${String.format("%.1f", value)} ${stringResource(R.string.unit_km)}"
    WatchChartMetric.ACTIVE_KCAL -> "${value.roundToInt()} ${stringResource(R.string.unit_kcal)}"
}

private fun formatPaceOrSpeed(type: String, km: Double, durationSec: Long): String? {
    if (km < 0.05 || durationSec < 10) return null
    return if (type.startsWith("BIKING")) {
        val kmh = km / (durationSec / 3600.0)
        String.format("%.1f km/h", kmh)
    } else {
        val minPerKm = (durationSec / 60.0) / km
        val mins = minPerKm.toInt()
        val secs = ((minPerKm - mins) * 60.0).roundToInt().coerceIn(0, 59)
        String.format("%d:%02d /km", mins, secs)
    }
}

private fun activityIcon(type: String): ImageVector = when (type) {
    "WALKING" -> Icons.AutoMirrored.Filled.DirectionsWalk
    "HIKING" -> Icons.Default.Hiking
    "RUNNING", "RUNNING_TREADMILL" -> Icons.AutoMirrored.Filled.DirectionsRun
    "BIKING", "BIKING_STATIONARY" -> Icons.AutoMirrored.Filled.DirectionsBike
    "SWIMMING_POOL", "SWIMMING_OPEN_WATER" -> Icons.Default.Pool
    "STAIR_CLIMBING" -> Icons.Default.Stairs
    "YOGA" -> Icons.Default.SelfImprovement
    else -> Icons.Default.FitnessCenter
}

@StringRes
private fun activityLabelRes(type: String): Int = when (type) {
    "WALKING" -> R.string.watch_type_walk
    "HIKING" -> R.string.watch_type_hike
    "RUNNING" -> R.string.watch_type_run
    "RUNNING_TREADMILL" -> R.string.watch_type_treadmill
    "BIKING" -> R.string.watch_type_bike
    "BIKING_STATIONARY" -> R.string.watch_type_bike_indoor
    "SWIMMING_POOL", "SWIMMING_OPEN_WATER" -> R.string.watch_type_swim
    "STAIR_CLIMBING" -> R.string.watch_type_stairs
    "HIIT" -> R.string.watch_type_hiit
    "YOGA" -> R.string.watch_type_yoga
    "ELLIPTICAL" -> R.string.watch_type_elliptical
    "ROWING_MACHINE" -> R.string.watch_type_rowing
    else -> R.string.watch_type_other
}
