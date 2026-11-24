package com.gymtracker.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.gymtracker.app.R
import com.gymtracker.app.data.db.ExerciseEntity
import com.gymtracker.app.data.db.HcStatus
import com.gymtracker.app.data.db.Muscle
import com.gymtracker.app.data.db.PrType
import com.gymtracker.app.data.db.SetType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun isUkrainianLocale(): Boolean {
    val locales = LocalConfiguration.current.locales
    val primary = if (locales.isEmpty) Locale.getDefault() else locales[0]
    return primary.language == "uk"
}

fun ExerciseEntity.localizedName(isUk: Boolean): String = if (isUk) nameUk else nameEn

@StringRes
fun Muscle.labelRes(): Int = when (this) {
    Muscle.CHEST -> R.string.muscle_chest
    Muscle.BACK -> R.string.muscle_back
    Muscle.LATS -> R.string.muscle_lats
    Muscle.TRAPS -> R.string.muscle_traps
    Muscle.LOWER_BACK -> R.string.muscle_lower_back
    Muscle.FRONT_DELTS -> R.string.muscle_front_delts
    Muscle.SIDE_DELTS -> R.string.muscle_side_delts
    Muscle.REAR_DELTS -> R.string.muscle_rear_delts
    Muscle.BICEPS -> R.string.muscle_biceps
    Muscle.TRICEPS -> R.string.muscle_triceps
    Muscle.FOREARMS -> R.string.muscle_forearms
    Muscle.ABS -> R.string.muscle_abs
    Muscle.HIP_FLEXORS -> R.string.muscle_hip_flexors
    Muscle.QUADS -> R.string.muscle_quads
    Muscle.HAMSTRINGS -> R.string.muscle_hamstrings
    Muscle.GLUTES -> R.string.muscle_glutes
    Muscle.CALVES -> R.string.muscle_calves
    Muscle.CARDIO -> R.string.muscle_cardio
}

@StringRes
fun HcStatus.labelRes(): Int = when (this) {
    HcStatus.MATCHED -> R.string.hc_status_matched
    HcStatus.PENDING -> R.string.hc_status_pending
    HcStatus.HR_ONLY -> R.string.hc_status_hr_only
    HcStatus.NO_DATA -> R.string.hc_status_no_data
    HcStatus.NOT_ATTEMPTED -> R.string.hc_status_not_attempted
    HcStatus.ERROR -> R.string.hc_status_error
}

@StringRes
fun PrType.labelRes(): Int = when (this) {
    PrType.BEST_E1RM -> R.string.pr_best_e1rm
    PrType.MAX_WEIGHT -> R.string.pr_max_weight
    PrType.MAX_REPS_AT_WEIGHT -> R.string.pr_max_reps_weight
    PrType.MAX_SET_VOLUME -> R.string.pr_max_set_vol
    PrType.MAX_SESSION_VOLUME -> R.string.pr_max_session_vol
}

@StringRes
fun SetType.labelRes(): Int = when (this) {
    SetType.WORK -> R.string.set_type_work
    SetType.WARMUP -> R.string.set_type_warmup
    SetType.DROP -> R.string.set_type_drop
    SetType.FAILURE -> R.string.set_type_failure
}

fun formatInstantShort(instant: Instant): String {
    val dt = instant.atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").format(dt)
}

fun formatDateShort(date: LocalDate): String {
    return DateTimeFormatter.ofPattern("dd MMM yy").format(date)
}

fun formatDurationMinSec(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val rem = s % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, rem)
    else String.format("%02d:%02d", m, rem)
}
