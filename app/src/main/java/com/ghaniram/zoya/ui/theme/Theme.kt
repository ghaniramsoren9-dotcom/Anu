package com.ghaniram.zoya.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AnuPrimary = Color(0xFF6C38FF)
val AnuSecondary = Color(0xFF9D5CFF)
val AnuAccent = Color(0xFFC084FC)
val AnuSoft = Color(0xFFE9D5FF)
val AnuLavenderBg = Color(0xFFF3F0FF)
val AnuBackground = Color(0xFFF8F7FC)
val AnuCardSurface = Color(0xFFFFFFFF)
val AnuTextDark = Color(0xFF1A1230)
val AnuTextMuted = Color(0xFF8B82A0)
val AnuBorder = Color(0xFFEBE6FA)
val AnuStatusGreen = Color(0xFF22C55E)

// Dark Palette
val AnuDarkPrimary = Color(0xFF9D5CFF)
val AnuDarkBackground = Color(0xFF0F172A)
val AnuDarkCardSurface = Color(0xFF1E293B)
val AnuDarkBorder = Color(0xFF334155)
val AnuDarkTextPrimary = Color(0xFFF8FAFC)
val AnuDarkTextMuted = Color(0xFF94A3B8)
val AnuDarkInputBg = Color(0xFF0F172A)

data class AnuCustomColors(
    val isDark: Boolean,
    val background: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val inputBackground: Color,
    val chipBackground: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val topBarTint: Color
)

val LocalAnuColors = staticCompositionLocalOf {
    AnuCustomColors(
        isDark = false,
        background = AnuBackground,
        cardBackground = AnuCardSurface,
        cardBorder = AnuBorder,
        textPrimary = AnuTextDark,
        textSecondary = AnuTextMuted,
        inputBackground = AnuLavenderBg,
        chipBackground = AnuLavenderBg,
        accentPrimary = AnuPrimary,
        accentSecondary = AnuSecondary,
        topBarTint = AnuTextDark
    )
}

private val AnuLightColorScheme = lightColorScheme(
    primary = AnuPrimary,
    onPrimary = Color.White,
    primaryContainer = AnuSoft,
    onPrimaryContainer = AnuPrimary,
    secondary = AnuSecondary,
    onSecondary = Color.White,
    secondaryContainer = AnuLavenderBg,
    onSecondaryContainer = AnuTextDark,
    tertiary = AnuAccent,
    onTertiary = Color.White,
    background = AnuBackground,
    onBackground = AnuTextDark,
    surface = AnuCardSurface,
    onSurface = AnuTextDark,
    surfaceVariant = AnuLavenderBg,
    onSurfaceVariant = AnuTextMuted,
    outline = AnuBorder,
    outlineVariant = Color(0xFFF0ECFC),
    error = Color(0xFFEF4444)
)

private val AnuDarkColorScheme = darkColorScheme(
    primary = AnuDarkPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B1D4C),
    onPrimaryContainer = AnuAccent,
    secondary = AnuAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = AnuDarkTextPrimary,
    tertiary = AnuSoft,
    onTertiary = AnuDarkBackground,
    background = AnuDarkBackground,
    onBackground = AnuDarkTextPrimary,
    surface = AnuDarkCardSurface,
    onSurface = AnuDarkTextPrimary,
    surfaceVariant = Color(0xFF243048),
    onSurfaceVariant = AnuDarkTextMuted,
    outline = AnuDarkBorder,
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFEF4444)
)

private val AnuTypography = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = AnuTextDark,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = AnuTextDark,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = AnuTextDark
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = AnuTextDark
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = AnuTextDark
    ),
    bodySmall = androidx.compose.ui.text.TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = AnuTextMuted
    ),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    ),
    labelMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = AnuTextMuted
    )
)

@Composable
fun ZoyaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AnuDarkColorScheme else AnuLightColorScheme
    val customColors = if (darkTheme) {
        AnuCustomColors(
            isDark = true,
            background = AnuDarkBackground,
            cardBackground = AnuDarkCardSurface,
            cardBorder = AnuDarkBorder,
            textPrimary = AnuDarkTextPrimary,
            textSecondary = AnuDarkTextMuted,
            inputBackground = Color(0xFF0F172A),
            chipBackground = Color(0xFF261D42),
            accentPrimary = AnuDarkPrimary,
            accentSecondary = AnuAccent,
            topBarTint = Color.White
        )
    } else {
        AnuCustomColors(
            isDark = false,
            background = AnuBackground,
            cardBackground = AnuCardSurface,
            cardBorder = AnuBorder,
            textPrimary = AnuTextDark,
            textSecondary = AnuTextMuted,
            inputBackground = AnuLavenderBg,
            chipBackground = AnuLavenderBg,
            accentPrimary = AnuPrimary,
            accentSecondary = AnuSecondary,
            topBarTint = AnuTextDark
        )
    }

    CompositionLocalProvider(LocalAnuColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AnuTypography,
            content = content
        )
    }
}

