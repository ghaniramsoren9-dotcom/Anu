package com.ghaniram.zoya.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import com.ghaniram.zoya.ui.theme.LocalAnuColors

/**
 * Screen Lock Settings Screen matching Pages 30 & 31 of the specification.
 * Interactive Pattern visualizer, PIN configuration, and calibration sliders with dynamic theme support.
 */
@Composable
fun AnuScreenLockScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    var wakeScreenState by remember { mutableStateOf(store.wakeScreenWhenNeeded) }
    var unlockForMeState by remember { mutableStateOf(store.unlockForMe) }
    var pinState by remember { mutableStateOf(store.savedPin) }
    var selectedPatternDots by remember { mutableStateOf(setOf<Int>()) }

    var verticalPosState by remember { mutableStateOf(store.lockVerticalPosition) }
    var gridSizeState by remember { mutableStateOf(store.lockGridSize) }
    var drawingSpeedState by remember { mutableStateOf(store.lockDrawingSpeedMs) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Screen lock",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Waking the screen
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.StayCurrentPortrait, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Waking the screen", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    SettingsToggleRow(
                        title = "Wake the screen when she needs it",
                        subtitle = "Voice, calls and messages already work locked. This is for actions that must touch the screen — she raises Android's own unlock prompt.",
                        checked = wakeScreenState,
                        onCheckedChange = {
                            wakeScreenState = it
                            store.wakeScreenWhenNeeded = it
                        }
                    )
                }
            }

            // Unlock with pattern or PIN
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LockOpen, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock with your pattern or PIN", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    SettingsToggleRow(
                        title = "Unlock for me",
                        subtitle = "Let Anu unlock the phone autonomously for authorized requests",
                        checked = unlockForMeState,
                        onCheckedChange = {
                            unlockForMeState = it
                            store.unlockForMe = it
                        }
                    )

                    Spacer(Modifier.height(14.dp))
                    Text("Pattern Grid Calibration (Tap dots in sequence)", fontSize = 12.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(8.dp))

                    // 3x3 Pattern Matrix Visualizer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.inputBackground)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            for (row in 0..2) {
                                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                    for (col in 0..2) {
                                        val index = row * 3 + col
                                        val isDotSelected = selectedPatternDots.contains(index)
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(if (isDotSelected) colors.accentPrimary else colors.chipBackground)
                                                .clickable {
                                                    val next = selectedPatternDots.toMutableSet()
                                                    if (next.contains(index)) next.remove(index) else next.add(index)
                                                    selectedPatternDots = next
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isDotSelected) {
                                                Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Or enter numeric PIN", fontSize = 12.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicInputField(
                        value = pinState,
                        onValueChange = {
                            pinState = it
                            store.savedPin = it
                        },
                        placeholder = "••••",
                        isPassword = true
                    )

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            Toast.makeText(context, "Testing automated unlock sequence...", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.chipBackground, contentColor = colors.textPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Text("Lock and try to unlock", fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Fine-tuning calibration", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Vertical position", fontSize = 11.5.sp, color = colors.textSecondary)
                        Text("${(verticalPosState * 100).toInt()}%", fontSize = 11.5.sp, color = colors.accentPrimary)
                    }
                    Slider(
                        value = verticalPosState,
                        onValueChange = {
                            verticalPosState = it
                            store.lockVerticalPosition = it
                        },
                        colors = SliderDefaults.colors(thumbColor = colors.accentPrimary, activeTrackColor = colors.accentPrimary, inactiveTrackColor = colors.cardBorder)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Size scale", fontSize = 11.5.sp, color = colors.textSecondary)
                        Text("${(gridSizeState * 100).toInt()}%", fontSize = 11.5.sp, color = colors.accentPrimary)
                    }
                    Slider(
                        value = gridSizeState,
                        onValueChange = {
                            gridSizeState = it
                            store.lockGridSize = it
                        },
                        colors = SliderDefaults.colors(thumbColor = colors.accentPrimary, activeTrackColor = colors.accentPrimary, inactiveTrackColor = colors.cardBorder)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Drawing speed", fontSize = 11.5.sp, color = colors.textSecondary)
                        Text("${drawingSpeedState.toInt()} ms", fontSize = 11.5.sp, color = colors.accentPrimary)
                    }
                    Slider(
                        value = drawingSpeedState,
                        onValueChange = {
                            drawingSpeedState = it
                            store.lockDrawingSpeedMs = it
                        },
                        valueRange = 50f..300f,
                        colors = SliderDefaults.colors(thumbColor = colors.accentPrimary, activeTrackColor = colors.accentPrimary, inactiveTrackColor = colors.cardBorder)
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsTipBanner(
                        text = "Stored encrypted on this phone and never synced or backed up. Used purely by Anu's local accessibility gesture engine."
                    )
                }
            }
        }
    }
}
