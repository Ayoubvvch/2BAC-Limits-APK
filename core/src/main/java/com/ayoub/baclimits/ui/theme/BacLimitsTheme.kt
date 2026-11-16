package com.ayoub.baclimits.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object OledColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0A0A0E)
    val SurfaceElevated = Color(0xFF121218)
    val SurfaceHighlight = Color(0xFF1A1A24)
    val NavBar = Color(0xFF06060A)
    val Border = Color(0xFF22222C)
    val BorderStrong = Color(0xFF323240)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF9CA3AF)
    val TextMuted = Color(0xFF6B7280)

    val Primary = Color(0xFF3B82F6)
    val PrimaryDark = Color(0xFF1D4ED8)
    val PrimarySoft = Color(0xFF0E1D3B)

    val Teal = Color(0xFF2DD4BF)
    val TealSoft = Color(0xFF0A2926)

    val Green = Color(0xFF4ADE80)
    val GreenSoft = Color(0xFF0A2917)

    val Amber = Color(0xFFFBBF24)
    val AmberSoft = Color(0xFF2B1F09)

    val Red = Color(0xFFF87171)
    val RedSoft = Color(0xFF2D1014)
}

private val BacDarkScheme = darkColorScheme(
    primary = OledColors.Primary,
    onPrimary = Color.White,
    primaryContainer = OledColors.PrimarySoft,
    onPrimaryContainer = Color(0xFF93C5FD),
    secondary = OledColors.Teal,
    onSecondary = Color.Black,
    secondaryContainer = OledColors.TealSoft,
    onSecondaryContainer = OledColors.Teal,
    tertiary = OledColors.Amber,
    background = OledColors.Background,
    onBackground = OledColors.TextPrimary,
    surface = OledColors.Surface,
    onSurface = OledColors.TextPrimary,
    surfaceVariant = OledColors.SurfaceElevated,
    onSurfaceVariant = OledColors.TextSecondary,
    outline = OledColors.BorderStrong,
    outlineVariant = OledColors.Border,
    error = OledColors.Red,
    errorContainer = OledColors.RedSoft,
    onError = Color.Black
)

private val BacTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 30.sp,
        color = OledColors.TextPrimary
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 26.sp,
        color = OledColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 22.sp,
        color = OledColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 21.sp,
        color = OledColors.TextPrimary
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
        color = OledColors.TextSecondary
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp,
        color = OledColors.TextSecondary
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.SansSerif,
        color = OledColors.TextMuted
    )
)

@Composable
fun BacLimitsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BacDarkScheme,
        typography = BacTypography,
        content = content
    )
}
