package com.ghaniram.zoya.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.AnuSettingsStore
import com.ghaniram.zoya.ui.theme.AnuPrimary
import com.ghaniram.zoya.ui.theme.LocalAnuColors

data class ThemePresetItem(
    val name: String,
    val description: String,
    val accentColor: Color,
    val bgColor: Color
)

/**
 * Theme Customization Screen matching Page 22 of the specification.
 * Interactive live preview card, Light/Dark/System mode selection, presets, typography, and surface shape selector.
 */
@Composable
fun AnuThemeSettingsScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    val settingsVersion by store.stateVersion.collectAsState()

    var selectedTheme by remember { mutableStateOf(store.themePreset) }
    var selectedTypeface by remember { mutableStateOf(store.typeface) }
    var selectedTextSize by remember { mutableStateOf(store.textSize) }
    var selectedSurfaceStyle by remember { mutableStateOf(store.surfaceStyle) }
    var selectedCorners by remember { mutableStateOf(store.surfaceCorners) }

    val presets = listOf(
        ThemePresetItem("Midnight", "The original. Calm near-black blue, one clear accent.", Color(0xFF6C38FF), Color(0xFF0F172A)),
        ThemePresetItem("Obsidian", "Neutral greys, one cold accent. The most restrained option.", Color(0xFF94A3B8), Color(0xFF18181B)),
        ThemePresetItem("Nocturne", "Deep indigo with a violet accent. Warmer, still quiet.", Color(0xFF818CF8), Color(0xFF1E1B4B)),
        ThemePresetItem("Ember", "Warm carbon and amber. High contrast, reads well at night.", Color(0xFFF59E0B), Color(0xFF1C1917)),
        ThemePresetItem("Abyss", "Deep teal and cyan. Cool, clinical, very dark.", Color(0xFF06B6D4), Color(0xFF042F2E)),
        ThemePresetItem("Rosewood", "Warm plum and rose. The softest of the dark themes.", Color(0xFFF43F5E), Color(0xFF3B0764)),
        ThemePresetItem("Daylight", "Light background. Clean and open high-contrast mode.", Color(0xFF6C38FF), Color(0xFFF8FAFC))
    )

    val currentPreset = presets.find { it.name == selectedTheme } ?: presets.first()
    val currentThemeMode = store.themeMode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Theme",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp)
        ) {
            // Theme Mode Selector (Light / Dark / System Default)
            item {
                Text(
                    text = "App Theme Mode",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    shadowElevation = if (colors.isDark) 0.dp else 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple("Light", "Light", Icons.Outlined.LightMode),
                                Triple("Dark", "Dark", Icons.Outlined.DarkMode),
                                Triple("System Default", "System", Icons.Outlined.SettingsBrightness)
                            ).forEach { (modeKey, modeLabel, icon) ->
                                val isSelected = currentThemeMode == modeKey
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) colors.accentPrimary else colors.chipBackground,
                                    border = BorderStroke(1.dp, if (isSelected) colors.accentPrimary else colors.cardBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            store.themeMode = modeKey
                                            Toast.makeText(context, "Theme set to $modeLabel", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else colors.textPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = modeLabel,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Live Preview Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = currentPreset.bgColor,
                    border = BorderStroke(1.dp, currentPreset.accentColor.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Good evening, ${store.userName}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text("Thursday, 5 September", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(currentPreset.accentColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("A", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = currentPreset.accentColor),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Primary Action", fontSize = 11.5.sp)
                            }

                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Secondary", fontSize = 11.5.sp, color = Color(0xFFCBD5E1))
                            }
                        }
                    }
                }
            }

            // Theme Presets Header
            item {
                Text("Theme Presets", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            }

            items(presets, key = { it.name }) { preset ->
                val isSelected = selectedTheme == preset.name
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(1.dp, if (isSelected) preset.accentColor else colors.cardBorder),
                    shadowElevation = if (colors.isDark) 0.dp else 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedTheme = preset.name
                            store.themePreset = preset.name
                            Toast.makeText(context, "${preset.name} theme applied!", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(preset.accentColor)
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                            Text(preset.description, fontSize = 11.sp, color = colors.textSecondary)
                        }

                        if (isSelected) {
                            Icon(Icons.Filled.Check, null, tint = preset.accentColor, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Text Customization
            item {
                SettingsCardContainer {
                    Text("Typeface", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Inter", "System", "Serif", "Monospace", "Handwritten")) { font ->
                            ChoiceChipPill(
                                label = font,
                                isSelected = selectedTypeface == font,
                                onClick = {
                                    selectedTypeface = font
                                    store.typeface = font
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Text Size", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Compact", "Default", "Large", "Larger")) { sz ->
                            ChoiceChipPill(
                                label = sz,
                                isSelected = selectedTextSize == sz,
                                onClick = {
                                    selectedTextSize = sz
                                    store.textSize = sz
                                }
                            )
                        }
                    }
                }
            }

            // Surfaces
            item {
                SettingsCardContainer {
                    Text("Surface Style", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Flat", "Glass", "Soft", "Clay")) { sty ->
                            ChoiceChipPill(
                                label = sty,
                                isSelected = selectedSurfaceStyle == sty,
                                onClick = {
                                    selectedSurfaceStyle = sty
                                    store.surfaceStyle = sty
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Corners", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Sharp", "Soft", "Rounded", "Pillowy")) { cor ->
                            ChoiceChipPill(
                                label = cor,
                                isSelected = selectedCorners == cor,
                                onClick = {
                                    selectedCorners = cor
                                    store.surfaceCorners = cor
                                }
                            )
                        }
                    }
                }
            }

            // Tip
            item {
                SettingsTipBanner(
                    text = "The orb has its own colours under Appearance. A theme here repaints the app chrome; the orb keeps whatever palette its style was designed around unless you override it there."
                )
            }
        }
    }
}
