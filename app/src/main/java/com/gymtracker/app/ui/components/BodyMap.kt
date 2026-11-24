package com.gymtracker.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gymtracker.app.R
import com.gymtracker.app.data.db.ExerciseEntity
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.Muscle
import com.gymtracker.app.data.db.RoutineDayExerciseWithExercise
import com.gymtracker.app.data.db.SetType
import com.gymtracker.app.data.db.WorkoutExerciseWithSets
import com.gymtracker.app.domain.RecoveryMuscleGroup
import com.gymtracker.app.domain.WorkoutMuscleStimulusItem
import com.gymtracker.app.ui.common.labelRes
import com.gymtracker.app.ui.theme.CardElevated
import com.gymtracker.app.ui.theme.CoralEnergy
import com.gymtracker.app.ui.theme.DeepMuscleBlue
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.theme.EmeraldRest
import com.gymtracker.app.ui.theme.GoldPr
import com.gymtracker.app.ui.theme.MuscleOutline
import kotlin.math.roundToInt

enum class BodyMapMode {
    FOCUS,
    RECOVERY
}

enum class MuscleCategoryGroup(
    @StringRes val labelRes: Int,
    val muscles: Set<Muscle>,
    val focusView: FocusView
) {
    TRICEPS(R.string.cat_triceps, setOf(Muscle.TRICEPS), FocusView.BACK_TRICEPS),
    CHEST(R.string.cat_chest, setOf(Muscle.CHEST), FocusView.FRONT_CHEST),
    SHOULDERS(R.string.cat_shoulders, setOf(Muscle.FRONT_DELTS, Muscle.SIDE_DELTS, Muscle.REAR_DELTS), FocusView.FRONT_SHOULDERS),
    BICEPS(R.string.cat_biceps, setOf(Muscle.BICEPS), FocusView.FRONT_BICEPS),
    ABS(R.string.cat_abs, setOf(Muscle.ABS, Muscle.HIP_FLEXORS), FocusView.FRONT_CORE),
    BACK(R.string.cat_back, setOf(Muscle.BACK, Muscle.LATS, Muscle.TRAPS, Muscle.LOWER_BACK), FocusView.BACK_UPPER),
    FOREARMS(R.string.cat_forearms, setOf(Muscle.FOREARMS), FocusView.FRONT_FOREARMS),
    UPPER_LEG(R.string.cat_upper_leg, setOf(Muscle.QUADS, Muscle.HAMSTRINGS), FocusView.FRONT_LEGS),
    GLUTES(R.string.cat_glutes, setOf(Muscle.GLUTES), FocusView.BACK_GLUTES),
    CARDIO(R.string.cat_cardio, setOf(Muscle.CARDIO), FocusView.CARDIO_BADGE),
    LOWER_LEG(R.string.cat_lower_leg, setOf(Muscle.CALVES), FocusView.BACK_CALVES),
    ALL(R.string.cat_all, Muscle.entries.toSet(), FocusView.FULL);

    enum class FocusView {
        FULL,
        FRONT_SHOULDERS,
        FRONT_CHEST,
        FRONT_BICEPS,
        FRONT_CORE,
        FRONT_FOREARMS,
        FRONT_LEGS,
        BACK_TRICEPS,
        BACK_UPPER,
        BACK_GLUTES,
        BACK_CALVES,
        CARDIO_BADGE
    }
}

data class MuscleBreakdownItem(
    val muscle: Muscle,
    val sets: Int,
    val volumeKg: Double,
    val sharePercent: Int
)

fun RecoveryMuscleGroup.labelRes(): Int = when (this) {
    RecoveryMuscleGroup.ABS -> R.string.rec_group_abs
    RecoveryMuscleGroup.BACK -> R.string.rec_group_back
    RecoveryMuscleGroup.BICEPS -> R.string.rec_group_biceps
    RecoveryMuscleGroup.CHEST -> R.string.rec_group_chest
    RecoveryMuscleGroup.FOREARMS -> R.string.rec_group_forearms
    RecoveryMuscleGroup.GLUTES -> R.string.rec_group_glutes
    RecoveryMuscleGroup.SHOULDERS -> R.string.rec_group_shoulders
    RecoveryMuscleGroup.TRICEPS -> R.string.rec_group_triceps
    RecoveryMuscleGroup.THIGHS -> R.string.rec_group_thighs
    RecoveryMuscleGroup.CALVES -> R.string.rec_group_calves
}

fun muscleIntensityForExercise(exercise: ExerciseEntity): Map<Muscle, Float> {
    val map = mutableMapOf<Muscle, Float>()
    map[exercise.primaryMuscle] = 1.0f
    exercise.secondaryMuscles.forEach { m ->
        if (m !in map) map[m] = 0.45f
    }
    return map
}

fun computeWorkoutMuscleBreakdown(
    exercises: List<WorkoutExerciseWithSets>
): Pair<Map<Muscle, Float>, List<MuscleBreakdownItem>> {
    val weights = mutableMapOf<Muscle, Double>()
    val setCounts = mutableMapOf<Muscle, Int>()
    val volCounts = mutableMapOf<Muscle, Double>()

    for (item in exercises) {
        if (item.exercise.type == ExerciseType.CARDIO || item.exercise.primaryMuscle == Muscle.CARDIO) continue
        val workSets = item.sets.filter { it.setType != SetType.WARMUP }
        val count = workSets.size
        if (count == 0) continue

        val vol = workSets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
        val pm = item.exercise.primaryMuscle
        weights[pm] = (weights[pm] ?: 0.0) + count.toDouble()
        setCounts[pm] = (setCounts[pm] ?: 0) + count
        volCounts[pm] = (volCounts[pm] ?: 0.0) + vol

        for (sm in item.exercise.secondaryMuscles.filter { it != pm && it != Muscle.CARDIO }.distinct()) {
            weights[sm] = (weights[sm] ?: 0.0) + count * 0.45
            setCounts[sm] = (setCounts[sm] ?: 0) + count
            volCounts[sm] = (volCounts[sm] ?: 0.0) + vol * 0.45
        }
    }

    if (weights.isEmpty()) return emptyMap<Muscle, Float>() to emptyList()
    val totalScore = weights.values.sum().coerceAtLeast(1.0)
    val maxScore = (weights.values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val intensity = weights.mapValues { (_, score) -> (score / maxScore).toFloat().coerceIn(0.25f, 1f) }
    val breakdown = weights.entries
        .sortedByDescending { it.value }
        .map { (m, score) ->
            MuscleBreakdownItem(
                muscle = m,
                sets = setCounts[m] ?: 0,
                volumeKg = volCounts[m] ?: 0.0,
                sharePercent = ((score / totalScore) * 100.0).roundToInt().coerceAtLeast(1)
            )
        }
    return intensity to breakdown
}

fun computeRoutineDayMuscleBreakdown(
    exercises: List<RoutineDayExerciseWithExercise>
): Pair<Map<Muscle, Float>, List<MuscleBreakdownItem>> {
    val weights = mutableMapOf<Muscle, Double>()
    val setCounts = mutableMapOf<Muscle, Int>()

    for (item in exercises) {
        if (item.exercise.type == ExerciseType.CARDIO || item.exercise.primaryMuscle == Muscle.CARDIO) continue
        val count = item.dayExercise.targetSets.coerceAtLeast(1)
        val pm = item.exercise.primaryMuscle
        weights[pm] = (weights[pm] ?: 0.0) + count.toDouble()
        setCounts[pm] = (setCounts[pm] ?: 0) + count
        for (sm in item.exercise.secondaryMuscles.filter { it != pm && it != Muscle.CARDIO }.distinct()) {
            weights[sm] = (weights[sm] ?: 0.0) + count * 0.45
            setCounts[sm] = (setCounts[sm] ?: 0) + count
        }
    }

    if (weights.isEmpty()) return emptyMap<Muscle, Float>() to emptyList()
    val totalScore = weights.values.sum().coerceAtLeast(1.0)
    val maxScore = (weights.values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val intensity = weights.mapValues { (_, score) -> (score / maxScore).toFloat().coerceIn(0.25f, 1f) }
    val breakdown = weights.entries
        .sortedByDescending { it.value }
        .map { (m, score) ->
            MuscleBreakdownItem(
                muscle = m,
                sets = setCounts[m] ?: 0,
                volumeKg = 0.0,
                sharePercent = ((score / totalScore) * 100.0).roundToInt().coerceAtLeast(1)
            )
        }
    return intensity to breakdown
}

private val FocusInactiveFill = Color(0xFF343A45)
private val RecoveryFreshFill = Color(0xFF6B7280)
private val RecoveryLightBlue = Color(0xFF38BDF8)
private val RecoveryAmber = Color(0xFFF59E0B)
private val RecoveryInjuredRed = Color(0xFFEF4444)
private val BodySilhouetteFill = Color(0xFF1E222A)

private fun resolveMuscleColor(
    muscles: Array<out Muscle>,
    muscleIntensity: Map<Muscle, Float>,
    mode: BodyMapMode,
    injuredMuscles: Set<Muscle>
): Color {
    if (mode == BodyMapMode.RECOVERY) {
        if (muscles.any { it in injuredMuscles }) return RecoveryInjuredRed
        val maxFatigue = muscles.maxOfOrNull { muscleIntensity[it] ?: 0f } ?: 0f
        return when {
            maxFatigue < 0.06f -> RecoveryFreshFill
            maxFatigue < 0.35f -> RecoveryLightBlue
            maxFatigue < 0.65f -> RecoveryAmber
            else -> CoralEnergy
        }
    }
    val maxI = muscles.maxOfOrNull { muscleIntensity[it] ?: 0f } ?: 0f
    return when {
        maxI >= 0.65f -> ElectricBlue
        maxI >= 0.15f -> DeepMuscleBlue
        else -> FocusInactiveFill
    }
}

@Composable
fun BodyMapCanvas(
    muscleIntensity: Map<Muscle, Float>,
    showBack: Boolean = true,
    mode: BodyMapMode = BodyMapMode.FOCUS,
    injuredMuscles: Set<Muscle> = emptySet(),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val virtualW = if (showBack) 215f else 100f
        val virtualH = 190f
        val scale = minOf(size.width / virtualW, size.height / virtualH)
        val xOffset = (size.width - virtualW * scale) / 2f
        val yOffset = (size.height - virtualH * scale) / 2f

        fun colorFor(vararg muscles: Muscle): Color =
            resolveMuscleColor(muscles, muscleIntensity, mode, injuredMuscles)

        drawFrontSilhouette(xOffset, yOffset, scale, ::colorFor)
        if (showBack) {
            drawBackSilhouette(xOffset + 115f * scale, yOffset, scale, ::colorFor)
        }
    }
}

@Composable
fun MiniBodyMap(
    muscleIntensity: Map<Muscle, Float>,
    modifier: Modifier = Modifier
) {
    val dominantBack = listOf(
        Muscle.BACK, Muscle.LATS, Muscle.LOWER_BACK, Muscle.REAR_DELTS, Muscle.TRICEPS, Muscle.GLUTES, Muscle.HAMSTRINGS
    ).any { (muscleIntensity[it] ?: 0f) >= 0.65f }
    val hasFrontPrimary = listOf(
        Muscle.CHEST, Muscle.FRONT_DELTS, Muscle.SIDE_DELTS, Muscle.BICEPS, Muscle.ABS, Muscle.QUADS
    ).any { (muscleIntensity[it] ?: 0f) >= 0.65f }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CardElevated)
            .padding(4.dp)
    ) {
        if (dominantBack && !hasFrontPrimary) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scale = minOf(size.width / 100f, size.height / 190f)
                val xo = (size.width - 100f * scale) / 2f
                val yo = (size.height - 190f * scale) / 2f
                drawBackSilhouette(xo, yo, scale) { ms ->
                    resolveMuscleColor(ms, muscleIntensity, BodyMapMode.FOCUS, emptySet())
                }
            }
        } else {
            BodyMapCanvas(
                muscleIntensity = muscleIntensity,
                showBack = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun MuscleCategoryCircle(
    category: MuscleCategoryGroup,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val intensity = category.muscles.associateWith { 1.0f }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(if (selected) ElectricBlue.copy(alpha = 0.22f) else CardElevated),
            contentAlignment = Alignment.Center
        ) {
            if (category == MuscleCategoryGroup.CARDIO) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val s = minOf(size.width, size.height) / 100f
                    val xo = (size.width - 100f * s) / 2f
                    val yo = (size.height - 100f * s) / 2f
                    val cx = xo + 50f * s
                    val cy = yo + 52f * s

                    // Stylized subtle upper torso backdrop inside circle
                    val torso = Path().apply {
                        moveTo(xo + 14f * s, yo + 98f * s)
                        cubicTo(xo + 15f * s, yo + 55f * s, xo + 19f * s, yo + 34f * s, xo + 37f * s, yo + 26f * s)
                        lineTo(xo + 41f * s, yo + 10f * s)
                        lineTo(xo + 59f * s, yo + 10f * s)
                        lineTo(xo + 63f * s, yo + 26f * s)
                        cubicTo(xo + 81f * s, yo + 34f * s, xo + 85f * s, yo + 55f * s, xo + 86f * s, yo + 98f * s)
                        close()
                    }
                    drawPath(torso, BodySilhouetteFill)

                    val leftLung = Path().apply {
                        moveTo(cx - 4f * s, cy - 19f * s)
                        cubicTo(cx - 23f * s, cy - 16f * s, cx - 32f * s, cy + 12f * s, cx - 25f * s, cy + 25f * s)
                        cubicTo(cx - 14f * s, cy + 28f * s, cx - 4f * s, cy + 17f * s, cx - 4f * s, cy - 19f * s)
                        close()
                    }
                    val rightLung = Path().apply {
                        moveTo(cx + 4f * s, cy - 19f * s)
                        cubicTo(cx + 23f * s, cy - 16f * s, cx + 32f * s, cy + 12f * s, cx + 25f * s, cy + 25f * s)
                        cubicTo(cx + 14f * s, cy + 28f * s, cx + 4f * s, cy + 17f * s, cx + 4f * s, cy - 19f * s)
                        close()
                    }
                    drawPath(leftLung, ElectricBlue)
                    drawPath(rightLung, ElectricBlue)
                    drawPath(leftLung, MuscleOutline, style = Stroke(width = 1.4f * s))
                    drawPath(rightLung, MuscleOutline, style = Stroke(width = 1.4f * s))
                    drawLine(
                        color = ElectricBlue,
                        start = Offset(cx, cy - 30f * s),
                        end = Offset(cx, cy - 4f * s),
                        strokeWidth = 3.2f * s,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = MuscleOutline,
                        start = Offset(cx - 8f * s, cy - 4f * s),
                        end = Offset(cx, cy - 10f * s),
                        strokeWidth = 1.8f * s
                    )
                    drawLine(
                        color = MuscleOutline,
                        start = Offset(cx + 8f * s, cy - 4f * s),
                        end = Offset(cx, cy - 10f * s),
                        strokeWidth = 1.8f * s
                    )
                }
            } else {
                val showBoth = category == MuscleCategoryGroup.ALL
                Canvas(modifier = Modifier.fillMaxSize()) {
                    fun c(vararg ms: Muscle): Color {
                        val hit = ms.any { it in intensity }
                        return if (hit) ElectricBlue else FocusInactiveFill
                    }
                    if (showBoth) {
                        val s = minOf(size.width * 0.88f / 215f, size.height * 0.88f / 190f)
                        val xo = (size.width - 215f * s) / 2f
                        val yo = (size.height - 190f * s) / 2f
                        drawFrontSilhouette(xo, yo, s, ::c)
                        drawBackSilhouette(xo + 115f * s, yo, s, ::c)
                    } else {
                        // Zoomed close-up matching Jefit's 3x4 muscle circle framing
                        val (virtualSpanW, focusY, isBack) = when (category.focusView) {
                            MuscleCategoryGroup.FocusView.BACK_TRICEPS -> Triple(74f, 56f, true)
                            MuscleCategoryGroup.FocusView.FRONT_CHEST -> Triple(66f, 43f, false)
                            MuscleCategoryGroup.FocusView.FRONT_SHOULDERS -> Triple(72f, 41f, false)
                            MuscleCategoryGroup.FocusView.FRONT_BICEPS -> Triple(74f, 56f, false)
                            MuscleCategoryGroup.FocusView.FRONT_CORE -> Triple(62f, 65f, false)
                            MuscleCategoryGroup.FocusView.BACK_UPPER -> Triple(68f, 51f, true)
                            MuscleCategoryGroup.FocusView.FRONT_FOREARMS -> Triple(82f, 75f, false)
                            MuscleCategoryGroup.FocusView.FRONT_LEGS -> Triple(60f, 106f, false)
                            MuscleCategoryGroup.FocusView.BACK_GLUTES -> Triple(58f, 92f, true)
                            MuscleCategoryGroup.FocusView.BACK_CALVES -> Triple(58f, 151f, true)
                            else -> Triple(92f, 95f, false)
                        }
                        val s = size.width / virtualSpanW
                        val xo = (size.width - 100f * s) / 2f
                        val yo = (size.height / 2f) - focusY * s
                        if (isBack) drawBackSilhouette(xo, yo, s, ::c)
                        else drawFrontSilhouette(xo, yo, s, ::c)
                    }
                }
            }
        }
        Text(
            text = stringResource(category.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) ElectricBlue else MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMapModalSheet(
    title: String,
    muscleIntensity: Map<Muscle, Float>,
    breakdown: List<MuscleBreakdownItem>,
    stimulusItems: List<WorkoutMuscleStimulusItem> = emptyList(),
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.bodymap_title), style = MaterialTheme.typography.titleLarge)
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            BodyMapCanvas(
                muscleIntensity = muscleIntensity,
                showBack = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(225.dp)
            )

            if (stimulusItems.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.summary_muscle_stimulus_title),
                    style = MaterialTheme.typography.titleMedium
                )
                stimulusItems.forEach { item ->
                    val stimColor = when {
                        item.stimulusPercent >= 85 -> EmeraldRest
                        item.stimulusPercent >= 50 -> ElectricBlue
                        else -> GoldPr
                    }
                    val levelRes = when {
                        item.stimulusPercent >= 100 -> R.string.stimulus_level_max
                        item.stimulusPercent >= 75 -> R.string.stimulus_level_optimal
                        item.stimulusPercent >= 45 -> R.string.stimulus_level_moderate
                        else -> R.string.stimulus_level_light
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(item.group.labelRes()),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = stimColor.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "${item.stimulusPercent}% ${stringResource(levelRes)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = stimColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${stringResource(R.string.stimulus_focus_share, item.focusSharePercent)} • ${pluralStringResource(R.plurals.sets_count, item.workSets, item.workSets)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (item.stimulusPercent / 100f).coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = stimColor,
                            trackColor = CardElevated,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                    }
                }
            } else {
                breakdown.take(8).forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(item.muscle.labelRes()), fontWeight = FontWeight.SemiBold)
                            val rightText = buildString {
                                append(pluralStringResource(R.plurals.sets_count, item.sets, item.sets))
                                if (item.volumeKg > 0.0) {
                                    append(" • ${item.volumeKg.roundToInt()} ${stringResource(R.string.unit_kg)}")
                                }
                                append(" (${item.sharePercent}%)")
                            }
                            Text(rightText, style = MaterialTheme.typography.bodyMedium, color = ElectricBlue)
                        }
                        LinearProgressIndicator(
                            progress = { (item.sharePercent / 100f).coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = ElectricBlue,
                            trackColor = CardElevated,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private class PathPts(val xo: Float, val yo: Float, val s: Float) {
    fun x(v: Float) = xo + v * s
    fun y(v: Float) = yo + v * s
}

private fun DrawScope.drawCurvedMuscle(
    xo: Float,
    yo: Float,
    s: Float,
    fill: Color,
    mirrorX: Boolean = false,
    outlineWidthPx: Float = (1.15f * s).coerceAtLeast(1f),
    build: Path.(mx: (Float) -> Float, y: (Float) -> Float) -> Unit
) {
    val mx: (Float) -> Float = { vx -> xo + (if (mirrorX) 100f - vx else vx) * s }
    val fy: (Float) -> Float = { vy -> yo + vy * s }
    val path = Path().apply {
        build(mx, fy)
        close()
    }
    drawPath(path, color = fill)
    drawPath(path, color = MuscleOutline, style = Stroke(width = outlineWidthPx))
}

private fun DrawScope.drawSymmetricMuscle(
    xo: Float,
    yo: Float,
    s: Float,
    fill: Color,
    buildLeft: Path.(x: (Float) -> Float, y: (Float) -> Float) -> Unit
) {
    drawCurvedMuscle(xo, yo, s, fill, mirrorX = false, build = buildLeft)
    drawCurvedMuscle(xo, yo, s, fill, mirrorX = true, build = buildLeft)
}

private fun DrawScope.drawHumanBaseSilhouette(
    xo: Float,
    yo: Float,
    s: Float
) {
    val p = PathPts(xo, yo, s)
    // Head + jaw
    drawOval(BodySilhouetteFill, topLeft = Offset(p.x(41.5f), p.y(4f)), size = Size(17f * s, 20f * s))

    // Smooth torso, arms, hands, legs & feet silhouette
    fun drawHalf(mirrorX: Boolean) {
        val mx: (Float) -> Float = { vx -> xo + (if (mirrorX) 100f - vx else vx) * s }
        val fy: (Float) -> Float = { vy -> yo + vy * s }
        val half = Path().apply {
            moveTo(mx(50f), fy(21f))
            lineTo(mx(44f), fy(21f))
            cubicTo(mx(43.5f), fy(26f), mx(42f), fy(28f), mx(33f), fy(31f))
            // Delt sweep into upper arm & forearm & hand
            cubicTo(mx(22f), fy(33f), mx(18f), fy(40f), mx(19f), fy(51f))
            cubicTo(mx(17.5f), fy(59f), mx(16.5f), fy(64f), mx(15f), fy(74f))
            cubicTo(mx(13f), fy(83f), mx(11f), fy(91f), mx(10.5f), fy(97f))
            cubicTo(mx(9f), fy(103f), mx(13f), fy(107f), mx(16.5f), fy(102f))
            cubicTo(mx(18f), fy(96f), mx(19f), fy(91f), mx(22f), fy(83f))
            cubicTo(mx(26f), fy(73f), mx(27f), fy(64f), mx(31f), fy(52f))
            // Ribcage & waist curve
            cubicTo(mx(33f), fy(60f), mx(35f), fy(68f), mx(34.5f), fy(75f))
            // Pelvis & outer thigh sweep
            cubicTo(mx(30.5f), fy(85f), mx(30f), fy(101f), mx(34f), fy(119f))
            // Outer knee & gastrocnemius flare to ankle
            cubicTo(mx(35f), fy(128f), mx(32.5f), fy(141f), mx(34.5f), fy(155f))
            lineTo(mx(36.5f), fy(172f))
            // Foot
            cubicTo(mx(35f), fy(177f), mx(31f), fy(183f), mx(36f), fy(185f))
            cubicTo(mx(41f), fy(185f), mx(45f), fy(181f), mx(44f), fy(172f))
            // Inner ankle, calf, thigh up to pelvis midline
            lineTo(mx(44.5f), fy(155f))
            cubicTo(mx(47f), fy(142f), mx(46f), fy(130f), mx(45.5f), fy(121f))
            cubicTo(mx(47.5f), fy(108f), mx(49f), fy(97f), mx(49.5f), fy(89f))
            lineTo(mx(50f), fy(89f))
            close()
        }
        drawPath(half, BodySilhouetteFill)
    }
    drawHalf(false)
    drawHalf(true)
}

private fun DrawScope.drawFrontSilhouette(
    xo: Float,
    yo: Float,
    s: Float,
    colorFor: (Array<out Muscle>) -> Color
) {
    drawHumanBaseSilhouette(xo, yo, s)

    // Neck traps flare
    val traps = colorFor(arrayOf(Muscle.TRAPS))
    drawSymmetricMuscle(xo, yo, s, traps) { x, y ->
        moveTo(x(45f), y(24f))
        cubicTo(x(43f), y(29f), x(38f), y(31f), x(32f), y(32.5f))
        cubicTo(x(37f), y(34f), x(42f), y(33.5f), x(45.5f), y(30.5f))
    }

    // Deltoids (curved shoulder caps)
    val delts = colorFor(arrayOf(Muscle.FRONT_DELTS, Muscle.SIDE_DELTS))
    drawSymmetricMuscle(xo, yo, s, delts) { x, y ->
        moveTo(x(32f), y(32.5f))
        cubicTo(x(23.5f), y(33.5f), x(19.5f), y(39.5f), x(20.5f), y(48.5f))
        cubicTo(x(24.5f), y(50f), x(28.5f), y(48.5f), x(31f), y(42f))
        cubicTo(x(31.5f), y(38f), x(32f), y(35f), x(32f), y(32.5f))
    }

    // Pectorals (sculpted left/right chest with rounded axillary tie-in)
    val chest = colorFor(arrayOf(Muscle.CHEST))
    drawSymmetricMuscle(xo, yo, s, chest) { x, y ->
        moveTo(x(32.5f), y(34f))
        cubicTo(x(39f), y(33f), x(46.5f), y(34f), x(49f), y(37f))
        cubicTo(x(49.2f), y(42f), x(49f), y(47f), x(47.5f), y(49.5f))
        cubicTo(x(41f), y(50.5f), x(35f), y(49f), x(31.5f), y(45.5f))
        cubicTo(x(30.5f), y(41f), x(31f), y(37f), x(32.5f), y(34f))
    }

    // Biceps (rounded fusiform arm belly)
    val biceps = colorFor(arrayOf(Muscle.BICEPS))
    drawSymmetricMuscle(xo, yo, s, biceps) { x, y ->
        moveTo(x(20.5f), y(49f))
        cubicTo(x(24.5f), y(50.5f), x(28f), y(49.5f), x(29.5f), y(51.5f))
        cubicTo(x(28.5f), y(60f), x(27f), y(65.5f), x(24.5f), y(67f))
        cubicTo(x(20.5f), y(66f), x(18.5f), y(58f), x(20.5f), y(49f))
    }

    // Forearms (curved brachioradialis & flexors)
    val forearms = colorFor(arrayOf(Muscle.FOREARMS))
    drawSymmetricMuscle(xo, yo, s, forearms) { x, y ->
        moveTo(x(18.5f), y(67.5f))
        cubicTo(x(22f), y(67f), x(25f), y(68f), x(25.5f), y(71.5f))
        cubicTo(x(24f), y(80f), x(21f), y(88f), x(18f), y(91f))
        cubicTo(x(15f), y(91f), x(13.5f), y(86f), x(14.5f), y(78f))
        cubicTo(x(15.5f), y(72f), x(17f), y(69f), x(18.5f), y(67.5f))
    }

    // Rectus Abdominis (curved 6-pack + lower abs paired symmetrically around linea alba)
    val abs = colorFor(arrayOf(Muscle.ABS))
    val abBands = listOf(51f to 58.5f, 59.5f to 67f, 68f to 75.5f, 76.5f to 84.5f)
    for ((idx, band) in abBands.withIndex()) {
        val wOuter = when (idx) {
            0 -> 41.5f
            1 -> 42f
            2 -> 42.8f
            else -> 44.5f
        }
        drawSymmetricMuscle(xo, yo, s, abs) { x, y ->
            moveTo(x(49.2f), y(band.first))
            cubicTo(x(46f), y(band.first), x(wOuter), y(band.first + 1.2f), x(wOuter), y((band.first + band.second) / 2f))
            cubicTo(x(wOuter), y(band.second - 1.2f), x(46f), y(band.second), x(49.2f), y(band.second))
        }
    }

    // Serratus & Obliques / Hip Flexors
    val hips = colorFor(arrayOf(Muscle.HIP_FLEXORS, Muscle.ABS))
    drawSymmetricMuscle(xo, yo, s, hips) { x, y ->
        moveTo(x(33.5f), y(50.5f))
        cubicTo(x(37f), y(51.5f), x(39.5f), y(54f), x(40.5f), y(61f))
        cubicTo(x(41.5f), y(68f), x(42.5f), y(77f), x(44f), y(83.5f))
        cubicTo(x(39.5f), y(81f), x(36f), y(77f), x(35f), y(71f))
        cubicTo(x(34.5f), y(63f), x(33f), y(56f), x(33.5f), y(50.5f))
    }

    // Quads: 3 curved heads per leg (Vastus Lateralis, Rectus Femoris teardrop, Vastus Medialis VMO)
    val quads = colorFor(arrayOf(Muscle.QUADS))
    // Outer sweep (Vastus Lateralis)
    drawSymmetricMuscle(xo, yo, s, quads) { x, y ->
        moveTo(x(33.5f), y(80f))
        cubicTo(x(37f), y(84f), x(38.5f), y(98f), x(38f), y(118f))
        cubicTo(x(36f), y(123f), x(34f), y(121f), x(33f), y(114f))
        cubicTo(x(31f), y(101f), x(31f), y(89f), x(33.5f), y(80f))
    }
    // Center teardrop (Rectus Femoris)
    drawSymmetricMuscle(xo, yo, s, quads) { x, y ->
        moveTo(x(36f), y(82f))
        cubicTo(x(40.5f), y(82.5f), x(43.5f), y(87f), x(43.5f), y(101f))
        cubicTo(x(43f), y(114f), x(41.5f), y(122f), x(39.5f), y(123.5f))
        cubicTo(x(37.5f), y(118f), x(37f), y(103f), x(36f), y(82f))
    }
    // Inner VMO & Adductor curve
    drawSymmetricMuscle(xo, yo, s, quads) { x, y ->
        moveTo(x(44f), y(87f))
        cubicTo(x(47f), y(90f), x(47.5f), y(104f), x(46f), y(121f))
        cubicTo(x(44f), y(124f), x(42f), y(123f), x(42.5f), y(114f))
        cubicTo(x(43.5f), y(103f), x(43.5f), y(94f), x(44f), y(87f))
    }

    // Front Calves (Gastrocnemius medial/lateral curve + Tibialis)
    val calves = colorFor(arrayOf(Muscle.CALVES))
    drawSymmetricMuscle(xo, yo, s, calves) { x, y ->
        moveTo(x(35.5f), y(133f))
        cubicTo(x(39f), y(132f), x(43.5f), y(133f), x(45.5f), y(138f))
        cubicTo(x(46.5f), y(147f), x(44f), y(158f), x(42f), y(169f))
        cubicTo(x(40f), y(170f), x(38f), y(169f), x(37.5f), y(162f))
        cubicTo(x(35f), y(151f), x(33.5f), y(142f), x(35.5f), y(133f))
    }
}

private fun DrawScope.drawBackSilhouette(
    xo: Float,
    yo: Float,
    s: Float,
    colorFor: (Array<out Muscle>) -> Color
) {
    drawHumanBaseSilhouette(xo, yo, s)

    // Trapezium diamond
    val traps = colorFor(arrayOf(Muscle.TRAPS))
    drawCurvedMuscle(xo, yo, s, traps) { x, y ->
        moveTo(x(46.5f), y(21.5f))
        lineTo(x(53.5f), y(21.5f))
        cubicTo(x(55.5f), y(28f), x(61f), y(31f), x(68f), y(32.5f))
        cubicTo(x(61f), y(37f), x(55f), y(43f), x(50f), y(53f))
        cubicTo(x(45f), y(43f), x(39f), y(37f), x(32f), y(32.5f))
        cubicTo(x(39f), y(31f), x(44.5f), y(28f), x(46.5f), y(21.5f))
    }

    // Rear Delts
    val rearDelts = colorFor(arrayOf(Muscle.REAR_DELTS, Muscle.SIDE_DELTS))
    drawSymmetricMuscle(xo, yo, s, rearDelts) { x, y ->
        moveTo(x(32f), y(32.5f))
        cubicTo(x(23.5f), y(34f), x(19.5f), y(40f), x(20.5f), y(48.5f))
        cubicTo(x(25f), y(49.5f), x(30f), y(47f), x(33.5f), y(41f))
        cubicTo(x(33.5f), y(36.5f), x(33f), y(34f), x(32f), y(32.5f))
    }

    // Upper / Mid Back (Rhomboids / Infraspinatus / Teres)
    val back = colorFor(arrayOf(Muscle.BACK))
    drawSymmetricMuscle(xo, yo, s, back) { x, y ->
        moveTo(x(34.5f), y(36f))
        cubicTo(x(39.5f), y(38f), x(45f), y(44f), x(49f), y(53.5f))
        cubicTo(x(43f), y(54f), x(37f), y(52f), x(32.5f), y(47.5f))
        cubicTo(x(33f), y(42f), x(33.5f), y(38.5f), x(34.5f), y(36f))
    }

    // Lats (Sweeping dorsal wings)
    val lats = colorFor(arrayOf(Muscle.LATS))
    drawSymmetricMuscle(xo, yo, s, lats) { x, y ->
        moveTo(x(32f), y(48f))
        cubicTo(x(38f), y(52f), x(44f), y(54f), x(49f), y(55f))
        cubicTo(x(48.5f), y(63f), x(46.5f), y(70f), x(44.5f), y(74f))
        cubicTo(x(39f), y(70f), x(35f), y(64f), x(32.5f), y(56f))
        cubicTo(x(31.5f), y(52f), x(31.5f), y(49.5f), x(32f), y(48f))
    }

    // Lower Back (Erector Spinae lumbar pillars)
    val lowerBack = colorFor(arrayOf(Muscle.LOWER_BACK))
    drawSymmetricMuscle(xo, yo, s, lowerBack) { x, y ->
        moveTo(x(45f), y(59f))
        cubicTo(x(47f), y(57f), x(49.2f), y(57f), x(49.2f), y(60f))
        lineTo(x(49.2f), y(79.5f))
        cubicTo(x(46f), y(80f), x(43f), y(78.5f), x(42f), y(74.5f))
        cubicTo(x(42.5f), y(67f), x(43.5f), y(62f), x(45f), y(59f))
    }

    // Triceps (Horseshoe curve on back upper arm)
    val triceps = colorFor(arrayOf(Muscle.TRICEPS))
    drawSymmetricMuscle(xo, yo, s, triceps) { x, y ->
        moveTo(x(20.5f), y(49f))
        cubicTo(x(24.5f), y(49f), x(28.5f), y(47f), x(30f), y(50.5f))
        cubicTo(x(29.5f), y(58f), x(27f), y(65.5f), x(23.5f), y(67.5f))
        cubicTo(x(20f), y(66f), x(18.5f), y(57.5f), x(20.5f), y(49f))
    }

    // Back Forearms
    val forearms = colorFor(arrayOf(Muscle.FOREARMS))
    drawSymmetricMuscle(xo, yo, s, forearms) { x, y ->
        moveTo(x(18.5f), y(67.5f))
        cubicTo(x(22f), y(67f), x(25f), y(68f), x(25.5f), y(71.5f))
        cubicTo(x(24f), y(80f), x(21f), y(88f), x(18f), y(91f))
        cubicTo(x(15f), y(91f), x(13.5f), y(86f), x(14.5f), y(78f))
        cubicTo(x(15.5f), y(72f), x(17f), y(69f), x(18.5f), y(67.5f))
    }

    // Glutes (Rounded athletic curve)
    val glutes = colorFor(arrayOf(Muscle.GLUTES))
    drawSymmetricMuscle(xo, yo, s, glutes) { x, y ->
        moveTo(x(34.5f), y(78.5f))
        cubicTo(x(40f), y(78f), x(46f), y(80f), x(49f), y(82.5f))
        cubicTo(x(49.2f), y(90f), x(48.5f), y(97.5f), x(45.5f), y(100.5f))
        cubicTo(x(39.5f), y(102f), x(33.5f), y(98.5f), x(32.5f), y(91f))
        cubicTo(x(32f), y(85f), x(33f), y(80.5f), x(34.5f), y(78.5f))
    }

    // Hamstrings (Split lateral & medial hamstring bellies)
    val hams = colorFor(arrayOf(Muscle.HAMSTRINGS))
    drawSymmetricMuscle(xo, yo, s, hams) { x, y ->
        moveTo(x(33.5f), y(98.5f))
        cubicTo(x(36.5f), y(101f), x(39f), y(102f), x(40f), y(106f))
        cubicTo(x(39.5f), y(116f), x(38f), y(124f), x(36f), y(126.5f))
        cubicTo(x(34f), y(123f), x(32.5f), y(111f), x(33.5f), y(98.5f))
    }
    drawSymmetricMuscle(xo, yo, s, hams) { x, y ->
        moveTo(x(41f), y(102f))
        cubicTo(x(44.5f), y(101.5f), x(47.5f), y(99.5f), x(48f), y(104f))
        cubicTo(x(47f), y(115f), x(45.5f), y(124f), x(43.5f), y(126.5f))
        cubicTo(x(41f), y(125f), x(40.5f), y(113f), x(41f), y(102f))
    }

    // Back Calves (Twin Gastrocnemius diamond heads)
    val calves = colorFor(arrayOf(Muscle.CALVES))
    drawSymmetricMuscle(xo, yo, s, calves) { x, y ->
        moveTo(x(35f), y(133f))
        cubicTo(x(37.5f), y(131f), x(40f), y(132f), x(40.5f), y(139f))
        cubicTo(x(40f), y(148f), x(39f), y(154f), x(37f), y(157f))
        cubicTo(x(34.5f), y(152f), x(33.5f), y(141f), x(35f), y(133f))
    }
    drawSymmetricMuscle(xo, yo, s, calves) { x, y ->
        moveTo(x(41f), y(134f))
        cubicTo(x(43.5f), y(131.5f), x(46f), y(133.5f), x(46.5f), y(141f))
        cubicTo(x(46f), y(149f), x(44f), y(156f), x(42f), y(158f))
        cubicTo(x(40.5f), y(153f), x(40.5f), y(143f), x(41f), y(134f))
    }
}
