package com.gymtracker.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ObsidianBg = Color(0xFF0B0C0E)
val NavBarBg = Color(0xFF111317)
val CardSurface = Color(0xFF16181D)
val CardElevated = Color(0xFF1E222A)
val HairlineBorder = Color(0xFF252932)

val ElectricBlue = Color(0xFF2F7CF6)
val DeepMuscleBlue = Color(0xFF234B8C)
val MuscleInactive = Color(0xFF2C313A)
val MuscleOutline = Color(0xFF121418)

val EmeraldRest = Color(0xFF33B870)
val GoldPr = Color(0xFFF5B301)
val CoralEnergy = Color(0xFFFF5A5F)
val TealAccent = Color(0xFF2DD4BF)
val BlueWatch = Color(0xFF38BDF8)

val HrZoneColors = listOf(
    Color(0xFF64748B), // Z1 Warm-up
    Color(0xFF38BDF8), // Z2 Light
    Color(0xFF33B870), // Z3 Aerobic
    Color(0xFFF5B301), // Z4 Threshold
    Color(0xFFFF5A5F)  // Z5 Max
)

private val DarkColors = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF143568),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF061A38),
    secondaryContainer = Color(0xFF17325C),
    onSecondaryContainer = Color(0xFFD6E6FF),
    tertiary = EmeraldRest,
    onTertiary = Color.White,
    background = ObsidianBg,
    onBackground = Color(0xFFF3F4F6),
    surface = CardSurface,
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = CardElevated,
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = HairlineBorder,
    error = Color(0xFFFF5A5F)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D64D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF0B254F),
    secondary = Color(0xFF1D64D8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF0B254F),
    tertiary = Color(0xFF15803D),
    onTertiary = Color.White,
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB),
    error = Color(0xFFDC2626)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun GymTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}

@Composable
fun ObsidianCard(
    modifier: Modifier = Modifier,
    highlightedBorder: Color? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = BorderStroke(
        width = if (highlightedBorder != null) 1.5.dp else 1.dp,
        color = highlightedBorder ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    )
    val shape = RoundedCornerShape(14.dp)
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            border = border,
            colors = colors,
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            border = border,
            colors = colors,
            content = content
        )
    }
}
