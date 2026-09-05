package com.ghaniram.zoya.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.AnuSettingsStore
import com.ghaniram.zoya.ZoyaOverlayService
import com.ghaniram.zoya.ui.theme.AnuPrimary

/**
 * Behaviour Settings Screen matching Page 24 of the specification.
 * Fully interactive with live overlay controls, audio switches, and boot settings.
 */
@Composable
fun AnuBehaviourScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    var floatingOrbState by remember { mutableStateOf(store.floatingOrbEnabled) }
    var edgeGlowState by remember { mutableStateOf(store.edgeGlowEnabled) }
    var echoGuardState by remember { mutableStateOf(store.echoGuardEnabled) }
    var screenRecordingState by remember { mutableStateOf(store.screenRecordingMode) }
    var startOnBootState by remember { mutableStateOf(store.startOnBoot) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Behaviour",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // On Screen
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Layers, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("On screen", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    SettingsToggleRow(
                        title = "Floating orb",
                        subtitle = "Show over other apps",
                        checked = floatingOrbState,
                        onCheckedChange = { checked ->
                            if (checked && !Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                                Toast.makeText(context, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show()
                            } else {
                                floatingOrbState = checked
                                store.floatingOrbEnabled = checked
                                if (checked) {
                                    ZoyaOverlayService.start(context)
                                } else {
                                    ZoyaOverlayService.stop(context)
                                }
                            }
                        }
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsToggleRow(
                        title = "Edge glow",
                        subtitle = "Animated frame around the screen while Anu is live",
                        checked = edgeGlowState,
                        onCheckedChange = {
                            edgeGlowState = it
                            store.edgeGlowEnabled = it
                        }
                    )
                }
            }

            // Audio
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.VolumeUp, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Audio", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    SettingsToggleRow(
                        title = "Echo guard",
                        subtitle = "Mute the mic while Anu speaks so she doesn't reply to herself",
                        checked = echoGuardState,
                        onCheckedChange = {
                            echoGuardState = it
                            store.echoGuardEnabled = it
                        }
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsToggleRow(
                        title = "Screen-recording mode",
                        subtitle = "Play Anu on the media stream so screen recorders capture her voice. Use earphones — on the loud speaker a light echo can appear. Applies on next start.",
                        checked = screenRecordingState,
                        onCheckedChange = {
                            screenRecordingState = it
                            store.screenRecordingMode = it
                        }
                    )
                }
            }

            // Startup
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PowerSettingsNew, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Startup", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    SettingsToggleRow(
                        title = "Start on boot",
                        subtitle = "Ready as soon as the phone turns on",
                        checked = startOnBootState,
                        onCheckedChange = {
                            startOnBootState = it
                            store.startOnBoot = it
                            Toast.makeText(context, if (it) "Anu will start on device boot" else "Boot startup disabled", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}
