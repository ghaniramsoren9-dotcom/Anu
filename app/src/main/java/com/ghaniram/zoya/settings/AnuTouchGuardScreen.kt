package com.ghaniram.zoya.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.AnuSettingsStore
import com.ghaniram.zoya.ui.theme.LocalAnuColors

/**
 * Touch Guard Screen matching Pages 28 & 29 of the specification.
 * Security tripwire, sensor triggers, and intrusion logs with dynamic theme support.
 */
@Composable
fun AnuTouchGuardScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    var touchGuardEnabled by remember { mutableStateOf(store.touchGuardEnabled) }
    var armGuard by remember { mutableStateOf(store.touchGuardArmed) }
    var godMode by remember { mutableStateOf(store.godModeEnabled) }
    var letAnyoneDisarm by remember { mutableStateOf(store.letAnyoneDisarmByVoice) }

    var warnFirstSiren by remember { mutableStateOf(store.warnFirstSirenSecond) }
    var lockImmediately by remember { mutableStateOf(store.lockScreenImmediatelyOnTouch) }
    var blinkTorch by remember { mutableStateOf(store.blinkTorchWithSiren) }
    var textSosAfter3 by remember { mutableStateOf(store.textSosAfter3Touches) }

    var sensitivityState by remember { mutableStateOf(store.touchMovementSensitivity) }
    var chargerTripState by remember { mutableStateOf(store.tripWhenChargerPulled) }
    var stealthState by remember { mutableStateOf(store.stealthRecordOnlyNoSound) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Touch Guard",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Master Guard Card
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Shield, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Touch Guard", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text(
                        "Watch the phone while you are away from it. When armed, anyone who wakes the screen, picks the phone up or pulls the charger out gets photographed with the front camera. Anu then announces the intrusion and triggers defensive measures.",
                        fontSize = 11.5.sp,
                        color = colors.textSecondary,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(14.dp))

                    SettingsToggleRow(
                        title = "Enable Touch Guard",
                        subtitle = "Off by default. Nothing below takes effect until this is on.",
                        checked = touchGuardEnabled,
                        onCheckedChange = {
                            touchGuardEnabled = it
                            store.touchGuardEnabled = it
                        }
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsToggleRow(
                        title = "Arm the guard",
                        subtitle = "Say 'touch guard on karo' to arm it without opening this screen. You get 12 seconds to put the phone down before it starts watching.",
                        checked = armGuard,
                        onCheckedChange = {
                            armGuard = it
                            store.touchGuardArmed = it
                            Toast.makeText(context, if (it) "Armed! You have 12 seconds to put the phone down." else "Touch Guard disarmed.", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsToggleRow(
                        title = "God Mode",
                        subtitle = "For a passcode someone else already knows. Your voice becomes the only key to unlock.",
                        checked = godMode,
                        onCheckedChange = {
                            godMode = it
                            store.godModeEnabled = it
                        }
                    )
                }
            }

            // What happens on a touch
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationImportant, null, tint = AnuWarningOrange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("What happens on a touch", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    SettingsToggleRow(
                        title = "Warn first, siren on second touch",
                        checked = warnFirstSiren,
                        onCheckedChange = {
                            warnFirstSiren = it
                            store.warnFirstSirenSecond = it
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Lock the screen immediately",
                        checked = lockImmediately,
                        onCheckedChange = {
                            lockImmediately = it
                            store.lockScreenImmediatelyOnTouch = it
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Blink the torch with the siren",
                        checked = blinkTorch,
                        onCheckedChange = {
                            blinkTorch = it
                            store.blinkTorchWithSiren = it
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Text my SOS contacts after 3 touches",
                        checked = textSosAfter3,
                        onCheckedChange = {
                            textSosAfter3 = it
                            store.textSosAfter3Touches = it
                        }
                    )
                }
            }

            // What counts as a touch
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Sensors, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("What counts as a touch", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(12.dp))

                    Text("Movement sensitivity", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Low", "Medium", "High").forEach { sens ->
                            val isSelected = sensitivityState.equals(sens, ignoreCase = true)
                            ChoiceChipPill(
                                label = sens,
                                isSelected = isSelected,
                                onClick = {
                                    sensitivityState = sens
                                    store.touchMovementSensitivity = sens
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    SettingsToggleRow(
                        title = "Trip when the charger is pulled out",
                        checked = chargerTripState,
                        onCheckedChange = {
                            chargerTripState = it
                            store.tripWhenChargerPulled = it
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Stealth — record only, no sound",
                        subtitle = "Silent photography and telemetry without alert sound",
                        checked = stealthState,
                        onCheckedChange = {
                            stealthState = it
                            store.stealthRecordOnlyNoSound = it
                        }
                    )
                }
            }

            // Who Touched It (Logs)
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Who touched it", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Nothing recorded yet. Every trip is recorded here with the time, what set it off, where the phone was, and the photos taken. Ask Anu 'koi mera phone chhua?' and she reads this out.",
                        fontSize = 11.5.sp,
                        color = colors.textSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
