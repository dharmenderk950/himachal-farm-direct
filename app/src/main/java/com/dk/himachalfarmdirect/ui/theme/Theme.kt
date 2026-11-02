package com.dk.himachalfarmdirect.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Clean green & white agricultural palette (spec B). */
object FarmColors {
    val Leaf = Color(0xFF2E7D32)
    val Forest = Color(0xFF1B5E20)
    val Sprout = Color(0xFFA5D6A7)
    val Mint = Color(0xFFE8F5E9)
    val Harvest = Color(0xFFF9A825)   // accent for offers / prices
    val Soil = Color(0xFF5D4037)
    val Ink = Color(0xFF1C2B1E)
    val Muted = Color(0xFF5F6F61)
    val Line = Color(0xFFDDE7DE)
    val Danger = Color(0xFFC62828)
}

private val scheme = lightColorScheme(
    primary = FarmColors.Leaf,
    onPrimary = Color.White,
    primaryContainer = FarmColors.Mint,
    onPrimaryContainer = FarmColors.Forest,
    secondary = FarmColors.Harvest,
    onSecondary = FarmColors.Ink,
    background = Color.White,
    onBackground = FarmColors.Ink,
    surface = Color.White,
    onSurface = FarmColors.Ink,
    surfaceVariant = Color(0xFFF4F8F4),
    onSurfaceVariant = FarmColors.Muted,
    outline = FarmColors.Line,
    error = FarmColors.Danger,
)

/** Slightly larger base sizes — easier for first-time smartphone users to read. */
private val type = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun FarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
