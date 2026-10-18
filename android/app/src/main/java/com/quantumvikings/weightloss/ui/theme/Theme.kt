package com.quantumvikings.weightloss.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Brand: a fresh green with warm accents, in the spirit of Yazio.
val Green = Color(0xFF22B573)
val Teal = Color(0xFF14A3A0)
val DeepTeal = Color(0xFF0E7C86)
val Orange = Color(0xFFFF8A3D)

// Macros keep the same colour everywhere: ring, bars, portion sheet, charts.
val CarbsColor = Color(0xFFFFB020)
val ProteinColor = Color(0xFF4C7CF3)
val FatColor = Color(0xFFF2545B)
val WaterColor = Color(0xFF3BA7F5)

/** One colour and emoji per meal, used for the meal cards and the add screen. */
data class MealStyle(val label: String, val emoji: String, val color: Color, val share: Double)

val MEAL_STYLES = mapOf(
    "BREAKFAST" to MealStyle("Ontbijt", "☕", Color(0xFFFFB020), 0.25),
    "LUNCH" to MealStyle("Lunch", "🥗", Color(0xFF22B573), 0.35),
    "DINNER" to MealStyle("Avondeten", "🍝", Color(0xFF7B61FF), 0.30),
    "SNACK" to MealStyle("Tussendoor", "🍎", Color(0xFFFF6FA8), 0.10),
)

val HeaderGradient = Brush.linearGradient(listOf(Green, Teal, DeepTeal))

private val Light = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F5E6),
    onPrimaryContainer = Color(0xFF00391F),
    secondary = Orange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3D1),
    onSecondaryContainer = Color(0xFF4A1F00),
    tertiary = Color(0xFF7B61FF),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF1B1D21),
    surface = Color.White,
    onSurface = Color(0xFF1B1D21),
    surfaceVariant = Color(0xFFEDEFF4),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFC),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF1F3F7),
    surfaceContainerHighest = Color(0xFFE8EBF0),
    outline = Color(0xFFD5D9E0),
    outlineVariant = Color(0xFFE6E9EE),
    error = Color(0xFFE5484D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF4FD597),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF0D5234),
    onPrimaryContainer = Color(0xFFD5F5E6),
    secondary = Color(0xFFFFA266),
    onSecondary = Color(0xFF4A1F00),
    secondaryContainer = Color(0xFF6B3410),
    onSecondaryContainer = Color(0xFFFFE3D1),
    tertiary = Color(0xFFA594FF),
    background = Color(0xFF111316),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF1B1E23),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF262A30),
    onSurfaceVariant = Color(0xFF9AA1AC),
    surfaceContainerLowest = Color(0xFF15171B),
    surfaceContainerLow = Color(0xFF181B1F),
    surfaceContainer = Color(0xFF1B1E23),
    surfaceContainerHigh = Color(0xFF23272D),
    surfaceContainerHighest = Color(0xFF2C3037),
    outline = Color(0xFF3A3F47),
    outlineVariant = Color(0xFF2C3037),
    error = Color(0xFFFF6B6F),
)

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun WeightLossTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
