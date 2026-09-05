package com.ghaniram.zoya.settings

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.AnuSettingsStore
import com.ghaniram.zoya.ui.theme.AnuPrimary

data class OrbColorChoice(val name: String, val color: Color)

/**
 * Appearance / Orb Screen matching Page 23 of the specification.
 * Interactive animated orb preview with style and size controls.
 */
@Composable
fun AnuAppearanceScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    var orbStyleState by remember { mutableStateOf(store.orbStyle) }
    var orbColorState by remember { mutableStateOf(store.orbColorName) }
    var orbSizeState by remember { mutableStateOf(store.orbSizeDp) }
    var useOrbOnHomeState by remember { mutableStateOf(store.useOrbOnHome) }

    val colorOptions = listOf(
        OrbColorChoice("Persona", Color(0xFF3B82F6)),
        OrbColorChoice("Jarvis Orange", Color(0xFFF97316)),
        OrbColorChoice("Ultron Cyan", Color(0xFF06B6D4)),
        OrbColorChoice("Neon Pink", Color(0xFFEC4899)),
        OrbColorChoice("Toxic Green", Color(0xFF10B981))
    )

    val activeColor = colorOptions.find { it.name == orbColorState }?.color ?: Color(0xFF3B82F6)

    // Animated pulse for orb preview
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Appearance",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp)
        ) {
            // Live Orb Preview Canvas
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0B1120),
                    border = BorderStroke(1.dp, activeColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(130.dp)) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(activeColor.copy(alpha = 0.8f), activeColor.copy(alpha = 0.1f), Color.Transparent)
                                ),
                                radius = size.minDimension / 2f * pulseScale
                            )
                            drawCircle(
                                color = activeColor,
                                radius = size.minDimension / 3.5f,
                                style = Stroke(width = 3.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White,
                                radius = size.minDimension / 6f
                            )
                        }
                    }
                }
            }

            // Orb Style Header
            item {
                Text("The orb", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Text("How Anu looks on screen", fontSize = 11.5.sp, color = colors.textSecondary)
            }

            // Orb Style Options
            listOf(
                Triple("Anu 2047", "Her signature — dynamic multi-ring energy field", "Anu 2047"),
                Triple("Anu Nova", "Concentric pulsing holographic rings", "Anu Nova"),
                Triple("J.A.R.V.I.S.", "Geometric high-tech tactical sphere wireframe", "J.A.R.V.I.S.")
            ).forEach { (title, desc, key) ->
                item {
                    val isSelected = orbStyleState == key
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.cardBackground,
                        border = BorderStroke(1.dp, if (isSelected) activeColor else colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                orbStyleState = key
                                store.orbStyle = key
                                Toast.makeText(context, "$key style chosen!", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                                Text(desc, fontSize = 11.sp, color = colors.textSecondary)
                            }
                            if (isSelected) {
                                Icon(Icons.Filled.Check, null, tint = activeColor, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Orb Colour Selector
            item {
                SettingsCardContainer {
                    Text("Colour", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        colorOptions.forEach { opt ->
                            val isSelected = orbColorState == opt.name
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(opt.color)
                                    .clickable {
                                        orbColorState = opt.name
                                        store.orbColorName = opt.name
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Floating Orb Size Slider
            item {
                SettingsCardContainer {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Floating orb size", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        Text("${orbSizeState.toInt()} dp", fontSize = 12.sp, color = colors.accentPrimary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = orbSizeState,
                        onValueChange = {
                            orbSizeState = it
                            store.orbSizeDp = it
                        },
                        valueRange = 120f..240f,
                        colors = SliderDefaults.colors(
                            thumbColor = activeColor,
                            activeTrackColor = activeColor,
                            inactiveTrackColor = colors.cardBorder
                        )
                    )
                }
            }

            // Home Character Replacement
            item {
                SettingsCardContainer {
                    SettingsToggleRow(
                        title = "Use the orb on Home",
                        subtitle = "Replace the character with the orb in your chosen style",
                        checked = useOrbOnHomeState,
                        onCheckedChange = {
                            useOrbOnHomeState = it
                            store.useOrbOnHome = it
                        }
                    )
                }
            }
        }
    }
}
