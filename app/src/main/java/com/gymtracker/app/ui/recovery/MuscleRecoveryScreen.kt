package com.gymtracker.app.ui.recovery

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.app.R
import com.gymtracker.app.domain.RecoverySleepImpact
import com.gymtracker.app.ui.common.formatDateShort
import com.gymtracker.app.ui.common.isUkrainianLocale
import com.gymtracker.app.ui.components.BodyMapCanvas
import com.gymtracker.app.ui.components.BodyMapMode
import com.gymtracker.app.ui.components.labelRes
import com.gymtracker.app.ui.theme.CardElevated
import com.gymtracker.app.ui.theme.CardSurface
import com.gymtracker.app.ui.theme.CoralEnergy
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.ObsidianCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MuscleRecoveryScreen(
    onBack: () -> Unit,
    viewModel: MuscleRecoveryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snap = state.snapshot
    val isUk = isUkrainianLocale()

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
                title = { Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    val badgeColor = when {
                        snap.overallReadinessPercent >= 85 -> EmeraldRest
                        snap.overallReadinessPercent >= 60 -> GoldPr
                        else -> CoralEnergy
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = badgeColor.copy(alpha = 0.16f),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "${snap.overallReadinessPercent}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
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
            // Optional OHealth Sleep recovery banner
            val sleepMin = state.sleepMinutesLastNight
            if (sleepMin != null && (snap.sleepImpact == RecoverySleepImpact.LOW_SLEEP || snap.sleepImpact == RecoverySleepImpact.GOOD_SLEEP)) {
                item {
                    val sleepStr = "${sleepMin / 60}h ${sleepMin % 60}m"
                    val isLow = snap.sleepImpact == RecoverySleepImpact.LOW_SLEEP
                    val accent = if (isLow) GoldPr else EmeraldRest
                    ObsidianCard(
                        highlightedBorder = accent.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Bedtime, contentDescription = null, tint = accent)
                            Text(
                                text = stringResource(
                                    if (isLow) R.string.recovery_sleep_low else R.string.recovery_sleep_good,
                                    sleepStr
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = accent
                            )
                        }
                    }
                }
            }

            // Full Front + Back Recovery Silhouette Card
            item {
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.recovery_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        BodyMapCanvas(
                            muscleIntensity = snap.muscleFatigueMap,
                            showBack = true,
                            mode = BodyMapMode.RECOVERY,
                            injuredMuscles = snap.injuredMuscleSet,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LegendDot(Color(0xFF6B7280), stringResource(R.string.recovery_legend_fresh))
                            LegendDot(Color(0xFF38BDF8), stringResource(R.string.recovery_legend_recovering))
                            LegendDot(CoralEnergy, stringResource(R.string.recovery_legend_fatigued))
                            LegendDot(Color(0xFFEF4444), stringResource(R.string.recovery_legend_injured))
                        }
                    }
                }
            }

            // 3-Column Table Header matching Jefit Muscle Recovery
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.recovery_col_muscle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        text = stringResource(R.string.recovery_col_rate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(0.9f)
                    )
                    Text(
                        text = stringResource(R.string.recovery_col_time),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.9f)
                    )
                }
            }

            itemsIndexed(snap.items, key = { _, item -> item.group.name }) { idx, item ->
                val rowBg = if (idx % 2 == 1) CardSurface else MaterialTheme.colorScheme.background
                val rateColor = when {
                    item.isInjured -> Color(0xFFEF4444)
                    item.recoveryPercent >= 95 -> MaterialTheme.colorScheme.onBackground
                    item.recoveryPercent >= 65 -> Color(0xFF38BDF8)
                    item.recoveryPercent >= 35 -> GoldPr
                    else -> CoralEnergy
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(rowBg)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(item.group.labelRes()),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        text = "${item.recoveryPercent}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = rateColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(0.9f)
                    )
                    Text(
                        text = formatRecoveryRemaining(item.remainingMinutes, isUk),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.9f)
                    )
                }
            }

            // Injured / Sore Muscles section matching Jefit
            item {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Healing, contentDescription = null, tint = Color(0xFFEF4444))
                    Column {
                        Text(
                            text = stringResource(R.string.recovery_injured_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.recovery_mark_injury),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        snap.items.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(item.group.labelRes()),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                item.injuredDate?.let { d ->
                                    Text(
                                        text = formatDateShort(d),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFFEF4444),
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                                Checkbox(
                                    checked = item.isInjured,
                                    onCheckedChange = { viewModel.toggleInjured(item.group) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFFEF4444),
                                        uncheckedColor = CardElevated
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun formatRecoveryRemaining(remainingMinutes: Int, isUk: Boolean): String {
    if (remainingMinutes <= 0) return if (isUk) "0 хв" else "0m"
    val hours = remainingMinutes / 60
    val mins = remainingMinutes % 60
    return if (hours >= 48) {
        val days = hours / 24
        val remH = hours % 24
        if (isUk) "${days}д ${remH}г" else "${days}d ${remH}h"
    } else if (hours > 0) {
        if (isUk) "${hours}г ${mins}хв" else "${hours}h ${mins}m"
    } else {
        if (isUk) "${mins}хв" else "${mins}m"
    }
}
