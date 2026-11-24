package com.gymtracker.app.ui.gym

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.app.R
import com.gymtracker.app.data.db.RoutineDayWithExercises
import com.gymtracker.app.data.db.WorkoutFull
import com.gymtracker.app.domain.PrEngine
import com.gymtracker.app.ui.common.formatDurationMinSec
import com.gymtracker.app.ui.common.formatInstantShort
import com.gymtracker.app.ui.common.isUkrainianLocale
import com.gymtracker.app.ui.common.labelRes
import com.gymtracker.app.ui.common.localizedName
import com.gymtracker.app.ui.components.BodyMapCanvas
import com.gymtracker.app.ui.components.BodyMapModalSheet
import com.gymtracker.app.ui.components.BodyMapMode
import com.gymtracker.app.ui.components.MiniBodyMap
import com.gymtracker.app.ui.components.MuscleCategoryCircle
import com.gymtracker.app.ui.components.MuscleCategoryGroup
import com.gymtracker.app.ui.components.computeRoutineDayMuscleBreakdown
import com.gymtracker.app.ui.components.labelRes as recGroupLabelRes
import com.gymtracker.app.ui.components.muscleIntensityForExercise
import com.gymtracker.app.ui.theme.CardElevated
import com.gymtracker.app.ui.theme.CoralEnergy
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.ObsidianCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GymScreen(
    onOpenActiveWorkout: (Long) -> Unit,
    onOpenWorkoutSummary: (Long) -> Unit,
    onOpenExerciseDetail: (Long) -> Unit,
    onOpenMuscleRecovery: () -> Unit = {},
    onOpenWatchActivity: () -> Unit = {},
    viewModel: GymViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isUk = isUkrainianLocale()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.gym_tab_routines), maxLines = 1) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.gym_tab_exercises), maxLines = 1) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(stringResource(R.string.gym_tab_progress), maxLines = 1) }
            )
        }

        when (selectedTab) {
            0 -> RoutinesTab(
                state = state,
                isUk = isUk,
                onSelectDay = viewModel::selectRoutineDay,
                onStartDay = { dayId, name -> viewModel.startRoutineDay(dayId, name, onOpenActiveWorkout) }
            )
            1 -> ExercisesTab(
                state = state,
                isUk = isUk,
                onQueryChange = viewModel::setSearchQuery,
                onSelectCategory = viewModel::selectCategory,
                onOpenExercise = onOpenExerciseDetail
            )
            2 -> ProgressTab(
                state = state,
                isUk = isUk,
                onToggleExternal = viewModel::setShowExternalCardio,
                onRefreshPending = viewModel::refreshPendingHc,
                onOpenWorkout = onOpenWorkoutSummary,
                onOpenRecovery = onOpenMuscleRecovery,
                onOpenWatchActivity = onOpenWatchActivity
            )
        }
    }
}

@Composable
private fun RoutinesTab(
    state: GymUiState,
    isUk: Boolean,
    onSelectDay: (Long) -> Unit,
    onStartDay: (Long, String) -> Unit
) {
    var subMode by rememberSaveable { mutableIntStateOf(0) } // 0 = Overview, 1 = Day Details
    var showBodyMapForDay by remember { mutableStateOf<RoutineDayWithExercises?>(null) }

    val allDays = remember(state.routines) {
        state.routines.flatMap { it.days }.sortedBy { it.day.orderIndex }
    }
    val selectedDay = remember(allDays, state.selectedRoutineDayId) {
        allDays.firstOrNull { it.day.id == state.selectedRoutineDayId } ?: allDays.firstOrNull()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Split Title + Sub-tabs (Overview | Day Details) matching Jefit 110727
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = state.routines.firstOrNull()?.routine?.name ?: stringResource(R.string.gym_tab_routines),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                AssistChip(
                    onClick = {},
                    leadingIcon = { Icon(Icons.Default.Watch, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp)) },
                    label = { Text(stringResource(R.string.gym_watch_reminder), maxLines = 2, style = MaterialTheme.typography.labelMedium) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = subMode == 0,
                        onClick = { subMode = 0 },
                        label = { Text(stringResource(R.string.routines_overview)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = subMode == 1,
                        onClick = { subMode = 1 },
                        label = { Text(stringResource(R.string.routines_day_details)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (allDays.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.gym_no_routines), style = MaterialTheme.typography.bodyLarge)
                }
            } else if (subMode == 0) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allDays, key = { it.day.id }) { dayWithEx ->
                        val isSelected = dayWithEx.day.id == selectedDay?.day?.id
                        val (muscles, _) = remember(dayWithEx.exercises) {
                            computeRoutineDayMuscleBreakdown(dayWithEx.exercises)
                        }
                        val estMin = remember(dayWithEx.exercises) {
                            val setsMin = dayWithEx.exercises.sumOf { it.dayExercise.targetSets * 3 }
                            setsMin.coerceAtLeast(15)
                        }
                        val lastDate = state.lastDoneByDayId[dayWithEx.day.id]
                        val lastLabel = lastDate?.let { d ->
                            val diff = ChronoUnit.DAYS.between(d, LocalDate.now()).toInt()
                            when {
                                diff <= 0 -> stringResource(R.string.routines_today)
                                diff == 1 -> stringResource(R.string.routines_yesterday)
                                else -> stringResource(R.string.routines_days_ago, diff)
                            }
                        } ?: stringResource(R.string.routines_never)

                        ObsidianCard(
                            highlightedBorder = if (isSelected) ElectricBlue else null,
                            onClick = { onSelectDay(dayWithEx.day.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MiniBodyMap(
                                    muscleIntensity = muscles,
                                    modifier = Modifier.size(58.dp)
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = CardElevated
                                        ) {
                                            Text(
                                                text = dayTagLabel(dayWithEx.day.name),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = ElectricBlue,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        val ready = state.dayReadinessByDayId[dayWithEx.day.id]
                                        if (ready != null) {
                                            val rColor = when {
                                                ready >= 85 -> EmeraldRest
                                                ready >= 60 -> GoldPr
                                                else -> CoralEnergy
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = rColor.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.recovery_day_badge, ready),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = rColor,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = dayWithEx.day.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "~$estMin ${stringResource(R.string.unit_min)} • ${dayWithEx.exercises.size} ${stringResource(R.string.unit_ex_short)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = lastLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (selectedDay != null) {
                val orderedDayExercises = selectedDay.exercises.sortedBy { it.dayExercise.orderIndex }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(selectedDay.day.name, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = stringResource(R.string.routines_exercises_count, orderedDayExercises.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = { showBodyMapForDay = selectedDay }) {
                                Icon(Icons.Default.AccessibilityNew, contentDescription = null, tint = ElectricBlue)
                                Spacer(Modifier.width(6.dp))
                                Text("BodyMap")
                            }
                        }
                    }

                    itemsIndexed(orderedDayExercises, key = { _, it -> it.dayExercise.id }) { idx, de ->
                        val nextDe = orderedDayExercises.getOrNull(idx + 1)
                        val isLinkedSuperset = de.dayExercise.supersetGroup != null &&
                            nextDe?.dayExercise?.supersetGroup == de.dayExercise.supersetGroup

                        ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MiniBodyMap(
                                    muscleIntensity = muscleIntensityForExercise(de.exercise),
                                    modifier = Modifier.size(52.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(de.exercise.localizedName(isUk), style = MaterialTheme.typography.titleMedium)
                                    val repsSummary = de.dayExercise.targetReps?.let { " × $it" } ?: ""
                                    Text(
                                        text = stringResource(R.string.routines_sets_count, de.dayExercise.targetSets) + repsSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (isLinkedSuperset) {
                            SupersetConnector()
                        } else {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // Bottom floating Start Workout CTA matching Jefit 110727
        if (selectedDay != null) {
            Button(
                onClick = { onStartDay(selectedDay.day.id, selectedDay.day.name) },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .height(52.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.today_start_workout),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }

    showBodyMapForDay?.let { day ->
        val (muscles, breakdown) = remember(day) { computeRoutineDayMuscleBreakdown(day.exercises) }
        BodyMapModalSheet(
            title = day.day.name,
            muscleIntensity = muscles,
            breakdown = breakdown,
            onDismiss = { showBodyMapForDay = null }
        )
    }
}

@Composable
private fun SupersetConnector() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 34.dp)
            .height(22.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        Canvas(modifier = Modifier.width(20.dp).height(22.dp)) {
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

private fun dayTagLabel(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.contains("chest") -> "TUE"
        lower.contains("back") -> "THU"
        lower.contains("leg") -> "SAT"
        else -> "ANY"
    }
}

@Composable
private fun ExercisesTab(
    state: GymUiState,
    isUk: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectCategory: (MuscleCategoryGroup?) -> Unit,
    onOpenExercise: (Long) -> Unit
) {
    val showGrid = state.selectedCategory == null && state.searchQuery.isBlank()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.gym_search_exercises)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Mode switcher: Body Grid | All List
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = showGrid,
                    onClick = {
                        onQueryChange("")
                        onSelectCategory(null)
                    },
                    label = { Text(stringResource(R.string.exercises_grid_mode)) }
                )
                FilterChip(
                    selected = !showGrid,
                    onClick = {
                        if (state.selectedCategory == null) onSelectCategory(MuscleCategoryGroup.ALL)
                    },
                    label = { Text(stringResource(R.string.exercises_list_mode)) }
                )
                state.selectedCategory?.takeIf { it != MuscleCategoryGroup.ALL }?.let { cat ->
                    FilterChip(
                        selected = true,
                        onClick = { onSelectCategory(null) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(stringResource(cat.labelRes)) }
                    )
                }
            }
        }

        if (showGrid) {
            if (state.big3.benchKg != null || state.big3.squatKg != null || state.big3.deadliftKg != null) {
                item {
                    ObsidianCard(
                        highlightedBorder = GoldPr.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = GoldPr)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.gym_big3_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                state.big3.totalKg?.let { tot ->
                                    Text(
                                        text = "${tot.roundToInt()} ${stringResource(R.string.unit_kg)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = GoldPr,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Bench: ${state.big3.benchKg?.roundToInt() ?: "—"} ${stringResource(R.string.unit_kg)}", style = MaterialTheme.typography.bodyMedium)
                                Text("Squat: ${state.big3.squatKg?.roundToInt() ?: "—"} ${stringResource(R.string.unit_kg)}", style = MaterialTheme.typography.bodyMedium)
                                Text("Deadlift: ${state.big3.deadliftKg?.roundToInt() ?: "—"} ${stringResource(R.string.unit_kg)}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // 3x4 Circular Muscle Group Grid matching Jefit 110907
            val rows = MuscleCategoryGroup.entries.chunked(3)
            items(rows) { rowCats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowCats.forEach { cat ->
                        MuscleCategoryCircle(
                            category = cat,
                            selected = false,
                            onClick = { onSelectCategory(cat) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = stringResource(R.string.exercises_found_count, state.exercises.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(state.exercises, key = { it.exercise.id }) { item ->
                ObsidianCard(
                    onClick = { onOpenExercise(item.exercise.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MiniBodyMap(
                            muscleIntensity = muscleIntensityForExercise(item.exercise),
                            modifier = Modifier.size(50.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.exercise.localizedName(isUk),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${stringResource(item.exercise.primaryMuscle.labelRes())} • ${
                                    pluralStringResource(R.plurals.workouts_count, item.usageCount, item.usageCount)
                                }",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item.bestE1rmKg?.let { e1 ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${e1.roundToInt()} ${stringResource(R.string.unit_kg)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("e1RM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressTab(
    state: GymUiState,
    isUk: Boolean,
    onToggleExternal: (Boolean) -> Unit,
    onRefreshPending: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenRecovery: () -> Unit,
    onOpenWatchActivity: () -> Unit
) {
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    val kpi = state.weeklyKpis

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.isRefreshingPending) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }

        // 2-column Weekly KPI Cards (Jefit 110950)
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ObsidianCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.progress_volume_week), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${kpi.volumeKg.roundToInt()} ${stringResource(R.string.unit_kg)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            kpi.volumeDeltaPercent?.let { d ->
                                val txt = if (d >= 0) "↗ $d%" else "↘ ${-d}%"
                                Text(txt, color = if (d >= 0) EmeraldRest else CoralEnergy, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text(stringResource(R.string.progress_this_week), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ObsidianCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.progress_time_week), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${kpi.workoutMinutes} ${stringResource(R.string.unit_min)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            kpi.timeDeltaPercent?.let { d ->
                                val txt = if (d >= 0) "↗ $d%" else "↘ ${-d}%"
                                Text(txt, color = if (d >= 0) EmeraldRest else CoralEnergy, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text(stringResource(R.string.progress_this_week), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Muscle Recovery Banner Card
        state.recovery?.let { rec ->
            item {
                val badgeColor = when {
                    rec.overallReadinessPercent >= 85 -> EmeraldRest
                    rec.overallReadinessPercent >= 60 -> GoldPr
                    else -> CoralEnergy
                }
                val recovering = rec.items.filter { it.recoveryPercent < 100 }.sortedBy { it.recoveryPercent }
                ObsidianCard(onClick = onOpenRecovery, modifier = Modifier.fillMaxWidth()) {
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
                            modifier = Modifier.size(width = 88.dp, height = 78.dp)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(10.dp), color = badgeColor.copy(alpha = 0.16f)) {
                                    Text(
                                        text = "${rec.overallReadinessPercent}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val statusSummary = if (recovering.isEmpty()) {
                                stringResource(R.string.recovery_all_fresh)
                            } else {
                                recovering.take(2).map {
                                    "${stringResource(it.group.recGroupLabelRes())} ${it.recoveryPercent}%"
                                }.joinToString(" • ")
                            }
                            Text(
                                text = statusSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Watch Walks, Runs & Steps Shortcut Card (kept isolated from gym tonnage & 1RM)
        item {
            ObsidianCard(onClick = onOpenWatchActivity, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Watch, contentDescription = null, tint = ElectricBlue)
                    Text(
                        text = stringResource(R.string.progress_watch_activities),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Interactive Monthly Calendar Card (Jefit 110950)
        item {
            MonthlyWorkoutCalendarCard(
                yearMonth = displayedMonth,
                workoutsByDate = state.workoutsByDate,
                totalSessions = kpi.totalSessions,
                streakWeeks = kpi.currentWeekStreak,
                onPrevMonth = { displayedMonth = displayedMonth.minusMonths(1) },
                onNextMonth = { displayedMonth = displayedMonth.plusMonths(1) },
                onTapWorkoutDay = { workoutsOnDay ->
                    workoutsOnDay.firstOrNull()?.let { onOpenWorkout(it.workout.id) }
                }
            )
        }

        // Filter row + refresh
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = state.showExternalCardio,
                    onClick = { onToggleExternal(!state.showExternalCardio) },
                    label = { Text(stringResource(R.string.gym_include_external), maxLines = 1) }
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onRefreshPending,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_refresh), maxLines = 1)
                }
            }
        }

        items(
            items = state.historyItems,
            key = { item ->
                when (item) {
                    is HistoryListItem.MonthHeader -> "m_${item.yearMonth}"
                    is HistoryListItem.BreakSeparator -> "b_${item.days}_${item.hashCode()}"
                    is HistoryListItem.WorkoutRow -> "w_${item.workout.workout.id}"
                }
            }
        ) { item ->
            when (item) {
                is HistoryListItem.MonthHeader -> {
                    val fmt = DateTimeFormatter.ofPattern("LLLL yyyy")
                    Text(
                        text = item.yearMonth.format(fmt).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricBlue,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                is HistoryListItem.BreakSeparator -> {
                    ObsidianCard(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val monthsLabel = pluralStringResource(R.plurals.months_count, item.months, item.months)
                        Text(
                            text = stringResource(R.string.gym_break_badge, monthsLabel, item.days),
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldPr,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                is HistoryListItem.WorkoutRow -> {
                    val wf = item.workout
                    val w = wf.workout
                    ObsidianCard(
                        onClick = { onOpenWorkout(w.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = w.title ?: wf.exercises.firstOrNull()?.exercise?.localizedName(isUk)
                                    ?: stringResource(R.string.active_workout_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (w.excludedFromStats) stringResource(R.string.gym_external_tag)
                                    else stringResource(w.hcStatus.labelRes()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (w.excludedFromStats) MaterialTheme.colorScheme.onSurfaceVariant else EmeraldRest
                                )
                            }
                            Text(
                                text = formatInstantShort(w.startedAt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val m = wf.metrics
                            if (m != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(formatDurationMinSec(m.durationSec))
                                    Text("${m.volumeKg.roundToInt()} ${stringResource(R.string.unit_kg)}")
                                    Text(
                                        "${m.kcalSmartTotal.roundToInt()} ${stringResource(R.string.unit_kcal)}",
                                        color = CoralEnergy
                                    )
                                    Text(m.avgHr?.let { "♥ $it" } ?: "—")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyWorkoutCalendarCard(
    yearMonth: YearMonth,
    workoutsByDate: Map<LocalDate, List<WorkoutFull>>,
    totalSessions: Int,
    streakWeeks: Int,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTapWorkoutDay: (List<WorkoutFull>) -> Unit
) {
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    val monthTitle = yearMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        .replaceFirstChar { it.uppercase(locale) }

    val daysInMonth = yearMonth.lengthOfMonth()
    // Monday-first grid (1..7)
    val firstDayOffset = (yearMonth.atDay(1).dayOfWeek.value - 1)
    val cells = buildList<LocalDate?> {
        repeat(firstDayOffset) { add(null) }
        for (d in 1..daysInMonth) add(yearMonth.atDay(d))
        while (size % 7 != 0) add(null)
    }

    ObsidianCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(monthTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onPrevMonth, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                }
                IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }

            // Weekday headers Mon..Sun
            Row(modifier = Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { dow ->
                    Text(
                        text = dow.getDisplayName(TextStyle.NARROW, locale).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            cells.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    week.forEach { date ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (date != null) {
                                val dayWorkouts = workoutsByDate[date].orEmpty()
                                val hasWorkout = dayWorkouts.isNotEmpty()
                                val isToday = date == today
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (hasWorkout) ElectricBlue else CardElevated)
                                        .then(
                                            if (isToday && !hasWorkout) Modifier.border(1.5.dp, ElectricBlue, CircleShape)
                                            else Modifier
                                        )
                                        .clickable(enabled = hasWorkout) { onTapWorkoutDay(dayWorkouts) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        fontSize = 13.sp,
                                        fontWeight = if (hasWorkout || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (hasWorkout) Color.White else MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("$totalSessions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.progress_total_sessions), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = GoldPr)
                    Column {
                        Text(
                            text = stringResource(R.string.progress_streak_weeks, streakWeeks),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(stringResource(R.string.progress_current_streak), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
