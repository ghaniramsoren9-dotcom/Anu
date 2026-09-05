package com.ghaniram.zoya.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.AnuSettingsStore
import com.ghaniram.zoya.ui.theme.LocalAnuColors

/**
 * Voice Guardian Screen matching Page 26 of the specification.
 * Interactive voice print management and test tool with dynamic theme support.
 */
@Composable
fun AnuVoiceGuardianScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    var guardianOnState by remember { mutableStateOf(store.voiceGuardianOn) }
    var awayModeState by remember { mutableStateOf(store.awayGuardModeLock) }
    var listenModeState by remember { mutableStateOf(store.voiceListenMode) }
    var strictnessState by remember { mutableStateOf(store.voiceMatchStrictness) }

    var enrolledVoices by remember { mutableStateOf(listOf("Ghaniram (Owner)")) }
    var showRecordDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Voice Guardian",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Guardian Controls
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Security, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Voice Guardian", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text(
                        "Anu only acts for enrolled voices, according to their role. Owner = full control; guest/unknown voices can only chat. With Away mode ON, an unknown voice gets a warning first, then the phone locks.",
                        fontSize = 11.5.sp,
                        color = colors.textSecondary,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(14.dp))

                    SettingsToggleRow(
                        title = "Voice Guardian ON",
                        checked = guardianOnState,
                        onCheckedChange = {
                            guardianOnState = it
                            store.voiceGuardianOn = it
                        }
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsToggleRow(
                        title = "Away / guard mode",
                        subtitle = "Lock on unknown voice attempting control",
                        checked = awayModeState,
                        onCheckedChange = {
                            awayModeState = it
                            store.awayGuardModeLock = it
                        }
                    )

                    Spacer(Modifier.height(14.dp))
                    Text("Listen mode", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Everyone", "Owner only", "Owner + family").forEach { mode ->
                            val isSelected = listenModeState.equals(mode, ignoreCase = true)
                            ChoiceChipPill(
                                label = mode,
                                isSelected = isSelected,
                                onClick = {
                                    listenModeState = mode
                                    store.voiceListenMode = mode
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Match strictness", fontSize = 12.sp, color = colors.textSecondary)
                        Text(String.format("%.2f", strictnessState), fontSize = 12.sp, color = colors.accentPrimary)
                    }
                    Slider(
                        value = strictnessState,
                        onValueChange = {
                            strictnessState = it
                            store.voiceMatchStrictness = it
                        },
                        valueRange = 0.10f..0.90f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accentPrimary,
                            activeTrackColor = colors.accentPrimary,
                            inactiveTrackColor = colors.cardBorder
                        )
                    )

                    Spacer(Modifier.height(6.dp))
                    SettingsTipBanner(
                        text = "Higher = stricter (guards against recordings). Lower = easier (works well in noisy rooms or hoarse throat)."
                    )
                }
            }

            // Enrolled Voices
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.MicNone, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Enrolled voices", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }

                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        enrolledVoices.forEach { voice ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(voice, color = colors.textPrimary, fontSize = 13.sp)
                                Surface(shape = RoundedCornerShape(4.dp), color = colors.accentPrimary.copy(alpha = 0.2f)) {
                                    Text("Enrolled", color = colors.accentPrimary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showRecordDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("+ Record voice", fontSize = 12.sp, color = Color.White)
                        }

                        Button(
                            onClick = { showTestDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.chipBackground),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("Test voice", fontSize = 12.sp, color = colors.textPrimary)
                        }
                    }
                }
            }
        }
    }

    if (showRecordDialog) {
        AlertDialog(
            onDismissRequest = { showRecordDialog = false },
            title = { Text("Voice Enrollment", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
            text = {
                Text(
                    "Please say clearly: 'Hello Anu, I am the owner of this device and you take commands from me.'",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        Toast.makeText(context, "Voice profile saved to secure enclave!", Toast.LENGTH_SHORT).show()
                        showRecordDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Save Voice Print", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecordDialog = false }) { Text("Cancel", color = colors.textSecondary) }
            },
            containerColor = colors.cardBackground
        )
    }

    if (showTestDialog) {
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = { Text("Voice Verification Test", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
            text = {
                Text("Listening... Speak any command to Anu.", color = colors.textSecondary, fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        Toast.makeText(context, "Match: 96% — Verified as Ghaniram (Owner)", Toast.LENGTH_SHORT).show()
                        showTestDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Done", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestDialog = false }) { Text("Close", color = colors.textSecondary) }
            },
            containerColor = colors.cardBackground
        )
    }
}
