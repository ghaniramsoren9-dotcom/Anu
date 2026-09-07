package com.ghaniram.zoya.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.AnuSettingsStore

/**
 * Runtime-observable design tokens. Keeping these as Compose state means the
 * existing screens that reference AnuPrimary/AnuCardSurface/etc. update without
 * requiring an Activity recreation when a theme preset changes.
 */
var AnuPrimary by mutableStateOf(Color(0xFF6C38FF))
var AnuSecondary by mutableStateOf(Color(0xFF9D5CFF))
var AnuAccent by mutableStateOf(Color(0xFFC084FC))
var AnuSoft by mutableStateOf(Color(0xFFE9D5FF))
var AnuLavenderBg by mutableStateOf(Color(0xFFF3F0FF))
var AnuBackground by mutableStateOf(Color(0xFFF8F7FC))
var AnuCardSurface by mutableStateOf(Color(0xFFFFFFFF))
var AnuTextDark by mutableStateOf(Color(0xFF1A1230))
var AnuTextMuted by mutableStateOf(Color(0xFF8B82A0))
var AnuBorder by mutableStateOf(Color(0xFFEBE6FA))
var AnuStatusGreen by mutableStateOf(Color(0xFF22C55E))

// Dark/base palette aliases kept for source compatibility.
var AnuDarkPrimary by mutableStateOf(Color(0xFF9D5CFF))
var AnuDarkBackground by mutableStateOf(Color(0xFF0F172A))
var AnuDarkCardSurface by mutableStateOf(Color(0xFF1E293B))
var AnuDarkBorder by mutableStateOf(Color(0xFF334155))
var AnuDarkTextPrimary by mutableStateOf(Color(0xFFF8FAFC))
var AnuDarkTextMuted by mutableStateOf(Color(0xFF94A3B8))
var AnuDarkInputBg by mutableStateOf(Color(0xFF0F172A))

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

private data class RuntimePalette(
    val accent: Color,
    val secondary: Color,
    val background: Color,
    val card: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val soft: Color,
    val lavender: Color
)

private fun paletteFor(name: String, dark: Boolean): RuntimePalette {
    if (!dark) {
        val accent = when (name) {
            "Ember" -> Color(0xFFD97706)
            "Abyss" -> Color(0xFF0891B2)
            "Rosewood" -> Color(0xFFE11D48)
            "Obsidian" -> Color(0xFF52525B)
            "Nocturne" -> Color(0xFF6366F1)
            else -> Color(0xFF6C38FF)
        }
        return RuntimePalette(
            accent = accent,
            secondary = accent.copy(alpha = 0.82f),
            background = Color(0xFFF8FAFC),
            card = Color.White,
            border = Color(0xFFE2E8F0),
            text = Color(0xFF0F172A),
            muted = Color(0xFF64748B),
            soft = accent.copy(alpha = 0.12f),
            lavender = accent.copy(alpha = 0.08f)
        )
    }

    return when (name) {
        "Obsidian" -> RuntimePalette(Color(0xFF94A3B8), Color(0xFFCBD5E1), Color(0xFF18181B), Color(0xFF27272A), Color(0xFF3F3F46), Color(0xFFFAFAFA), Color(0xFFA1A1AA), Color(0xFF3F3F46), Color(0xFF27272A))
        "Nocturne" -> RuntimePalette(Color(0xFF818CF8), Color(0xFFA5B4FC), Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF4338CA), Color(0xFFEEF2FF), Color(0xFFA5B4FC), Color(0xFF312E81), Color(0xFF28235C))
        "Ember" -> RuntimePalette(Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFF1C1917), Color(0xFF292524), Color(0xFF44403C), Color(0xFFFFF7ED), Color(0xFFD6D3D1), Color(0xFF451A03), Color(0xFF292524))
        "Abyss" -> RuntimePalette(Color(0xFF06B6D4), Color(0xFF22D3EE), Color(0xFF042F2E), Color(0xFF134E4A), Color(0xFF115E59), Color(0xFFECFEFF), Color(0xFF99F6E4), Color(0xFF164E63), Color(0xFF134E4A))
        "Rosewood" -> RuntimePalette(Color(0xFFF43F5E), Color(0xFFFB7185), Color(0xFF3B0764), Color(0xFF581C87), Color(0xFF7E22CE), Color(0xFFFFF1F2), Color(0xFFFDA4AF), Color(0xFF701A75), Color(0xFF581C87))
        "Daylight" -> paletteFor("Daylight", false)
        else -> RuntimePalette(Color(0xFF9D5CFF), Color(0xFFC084FC), Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF334155), Color(0xFFF8FAFC), Color(0xFF94A3B8), Color(0xFF2B1D4C), Color(0xFF261D42))
    }
}

private fun applyRuntimePalette(p: RuntimePalette, dark: Boolean) {
    AnuPrimary = p.accent
    AnuSecondary = p.secondary
    AnuAccent = p.secondary
    AnuSoft = p.soft
    AnuLavenderBg = p.lavender
    AnuBackground = p.background
    AnuCardSurface = p.card
    AnuTextDark = p.text
    AnuTextMuted = p.muted
    AnuBorder = p.border
    AnuDarkPrimary = p.accent
    AnuDarkBackground = p.background
    AnuDarkCardSurface = p.card
    AnuDarkBorder = p.border
    AnuDarkTextPrimary = p.text
    AnuDarkTextMuted = p.muted
    AnuDarkInputBg = p.background
    AnuStatusGreen = Color(0xFF22C55E)
}

private fun buildLightScheme(p: RuntimePalette) = lightColorScheme(
    primary = p.accent,
    onPrimary = Color.White,
    primaryContainer = p.soft,
    onPrimaryContainer = p.accent,
    secondary = p.secondary,
    onSecondary = Color.White,
    secondaryContainer = p.lavender,
    onSecondaryContainer = p.text,
    tertiary = p.secondary,
    onTertiary = Color.White,
    background = p.background,
    onBackground = p.text,
    surface = p.card,
    onSurface = p.text,
    surfaceVariant = p.lavender,
    onSurfaceVariant = p.muted,
    outline = p.border,
    outlineVariant = p.border,
    error = Color(0xFFEF4444)
)

private fun buildDarkScheme(p: RuntimePalette) = darkColorScheme(
    primary = p.accent,
    onPrimary = Color.White,
    primaryContainer = p.soft,
    onPrimaryContainer = p.secondary,
    secondary = p.secondary,
    onSecondary = Color.White,
    secondaryContainer = p.card,
    onSecondaryContainer = p.text,
    tertiary = p.secondary,
    onTertiary = p.background,
    background = p.background,
    onBackground = p.text,
    surface = p.card,
    onSurface = p.text,
    surfaceVariant = p.card,
    onSurfaceVariant = p.muted,
    outline = p.border,
    outlineVariant = p.border,
    error = Color(0xFFEF4444)
)

private val AnuTypography = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AnuTextDark, letterSpacing = (-0.5).sp),
    titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AnuTextDark, letterSpacing = (-0.2).sp),
    titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AnuTextDark),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = AnuTextDark),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = AnuTextDark),
    bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = AnuTextMuted),
    labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = AnuTextMuted)
)

@Composable
fun ZoyaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val store = rememberThemeStore(context)
    val settingsVersion by store.stateVersion.collectAsState()
    val mode = store.themeMode
    val effectiveDark = when (mode) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }
    val preset = store.themePreset
    val palette = paletteFor(preset, effectiveDark)

    // Publish the selected preset into the legacy color tokens used throughout
    // the existing UI. This is what makes preset switches immediate everywhere.
    SideEffect(key1 = "$preset|$effectiveDark|$settingsVersion") {
        applyRuntimePalette(palette, effectiveDark)
    }

    val colorScheme = if (effectiveDark) buildDarkScheme(palette) else buildLightScheme(palette)
    val customColors = AnuCustomColors(
        isDark = effectiveDark,
        background = palette.background,
        cardBackground = palette.card,
        cardBorder = palette.border,
        textPrimary = palette.text,
        textSecondary = palette.muted,
        inputBackground = palette.background,
        chipBackground = palette.lavender,
        accentPrimary = palette.accent,
        accentSecondary = palette.secondary,
        topBarTint = palette.text
    )

    CompositionLocalProvider(LocalAnuColors provides customColors) {
        MaterialTheme(colorScheme = colorScheme, typography = AnuTypography, content = content)
    }
}

@Composable
private fun rememberThemeStore(context: android.content.Context): AnuSettingsStore =
    androidx.compose.runtime.remember(context.applicationContext) {
        AnuSettingsStore.getInstance(context.applicationContext)
    }
