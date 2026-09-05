package com.ghaniram.zoya.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.ghaniram.zoya.ZoyaLanguage
import com.ghaniram.zoya.ZoyaSessionManager
import com.ghaniram.zoya.ui.theme.LocalAnuColors

/**
 * Anu Assistant Settings Screen matching Pages 9 & 10 of the specification.
 * Full Theme adaptivity and live persistence with session sync.
 */
@Composable
fun AnuAssistantSettingsScreen(
    store: AnuSettingsStore,
    currentLanguage: ZoyaLanguage,
    onSelectLanguage: (ZoyaLanguage) -> Unit,
    onOpenVoicePicker: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current

    var assistantNameState by remember { mutableStateOf(store.assistantName) }
    var personaState by remember { mutableStateOf(store.persona) }
    var showPersonaDropdown by remember { mutableStateOf(false) }
    var showLanguageDropdown by remember { mutableStateOf(false) }

    var girlfriendModeState by remember { mutableStateOf(store.girlfriendMode) }
    var rememberOnHerOwnState by remember { mutableStateOf(store.rememberOnHerOwn) }
    var incognitoState by remember { mutableStateOf(store.incognitoMemory) }
    var wakeWordBackState by remember { mutableStateOf(store.bringWakeWordBack) }
    var proactiveAnuState by remember { mutableStateOf(store.proactiveAnu) }
    var announceCallsState by remember { mutableStateOf(store.announceIncomingCalls) }
    var keepRingtoneState by remember { mutableStateOf(store.keepRingtonePlaying) }
    var drivingModeState by remember { mutableStateOf(store.drivingModeRejectCalls) }
    var drivingReplyTextState by remember { mutableStateOf(store.drivingAutoReplyText) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Anu Assistant",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Assistant Name
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Psychology, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Assistant name", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("What you call her", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    BasicInputField(
                        value = assistantNameState,
                        onValueChange = {
                            assistantNameState = it
                            store.assistantName = it
                            ZoyaSessionManager.onSettingsUpdated()
                        },
                        placeholder = "Anu"
                    )
                }
            }

            // Persona
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Face, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Persona", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Her overall vibe and conversational style", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.inputBackground,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPersonaDropdown = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(personaState, color = colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.ArrowDropDown, null, tint = colors.textSecondary)
                        }
                    }

                    DropdownMenu(
                        expanded = showPersonaDropdown,
                        onDismissRequest = { showPersonaDropdown = false }
                    ) {
                        listOf("Anu", "Friendly", "Professional", "Chill", "Empathetic", "Inquisitive").forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p, color = colors.textPrimary) },
                                onClick = {
                                    personaState = p
                                    store.persona = p
                                    ZoyaSessionManager.onSettingsUpdated()
                                    showPersonaDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Girlfriend Mode
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Favorite, null, tint = Color(0xFFEC4899), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Girlfriend mode", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Anu's affectionate companion mode", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(12.dp))

                    SettingsToggleRow(
                        title = "Enable girlfriend mode",
                        subtitle = "Anu speaks like a loving, caring companion — warm, sweet, and supportive.",
                        checked = girlfriendModeState,
                        onCheckedChange = {
                            girlfriendModeState = it
                            store.girlfriendMode = it
                            ZoyaSessionManager.onSettingsUpdated()
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    SettingsTipBanner(
                        text = "Takes effect immediately for all conversations."
                    )
                }
            }

            // Memory
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lightbulb, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Memory", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("What Anu is allowed to remember", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(12.dp))

                    SettingsToggleRow(
                        title = "Let Anu remember on her own",
                        subtitle = "She saves durable things she picks up — people, preferences, routine.",
                        checked = rememberOnHerOwnState,
                        onCheckedChange = {
                            rememberOnHerOwnState = it
                            store.rememberOnHerOwn = it
                        }
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsToggleRow(
                        title = "Incognito",
                        subtitle = "Turn on to pause new memories without deleting existing ones.",
                        checked = incognitoState,
                        onCheckedChange = {
                            incognitoState = it
                            store.incognitoMemory = it
                        }
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsTipBanner(
                        text = "Passwords, OTPs, PINs and card numbers are never stored. Your memories live safely on this phone."
                    )
                }
            }

            // Voice Selector Card
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.RecordVoiceOver, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Voice", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Tap to listen, preview, and select", fontSize = 11.5.sp, color = colors.textSecondary)

                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.inputBackground,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenVoicePicker)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${store.selectedVoiceTone} (${store.selectedVoiceSpeaker})",
                                    color = colors.textPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Category: ${store.selectedVoiceCategory}",
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = "Change →",
                                color = colors.accentPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Language
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Translate, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Language", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("The primary language she speaks", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.inputBackground,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLanguageDropdown = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentLanguage.label} (${currentLanguage.name.lowercase().replaceFirstChar { it.uppercase() }})",
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Outlined.ArrowDropDown, null, tint = colors.textSecondary)
                        }
                    }

                    DropdownMenu(
                        expanded = showLanguageDropdown,
                        onDismissRequest = { showLanguageDropdown = false }
                    ) {
                        ZoyaLanguage.values().forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.label, color = colors.textPrimary) },
                                onClick = {
                                    onSelectLanguage(lang)
                                    showLanguageDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Auto Start
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Mic, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Auto start", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("What the wake word does after you stop Anu", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(12.dp))

                    SettingsToggleRow(
                        title = "Bring the wake word back after a stop",
                        subtitle = "The mic button switches Anu off completely — nothing listens until you press it again.",
                        checked = wakeWordBackState,
                        onCheckedChange = {
                            wakeWordBackState = it
                            store.bringWakeWordBack = it
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    SettingsTipBanner(
                        text = "The notification's 'Band karo' always switches everything off cleanly."
                    )
                }
            }

            // Proactive Anu
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Proactive Anu", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Let her start conversations on her own", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(12.dp))

                    SettingsToggleRow(
                        title = "Start conversations on her own",
                        checked = proactiveAnuState,
                        onCheckedChange = {
                            proactiveAnuState = it
                            store.proactiveAnu = it
                        }
                    )
                }
            }

            // Call Announcement
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Call, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Call announcement", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Anu announces incoming calls and lets you answer hands-free", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(12.dp))

                    SettingsToggleRow(
                        title = "Announce incoming calls",
                        checked = announceCallsState,
                        onCheckedChange = {
                            announceCallsState = it
                            store.announceIncomingCalls = it
                        }
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsToggleRow(
                        title = "Keep my ringtone playing",
                        subtitle = "Anu turns ringtone down while announcing. Switch this on to keep hearing it underneath her.",
                        checked = keepRingtoneState,
                        onCheckedChange = {
                            keepRingtoneState = it
                            store.keepRingtonePlaying = it
                        }
                    )
                }
            }

            // Driving Mode
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.DirectionsCar, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Driving mode", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Calls are handled safely, with optional auto-reply SMS", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(12.dp))

                    SettingsToggleRow(
                        title = "Reject calls while driving",
                        checked = drivingModeState,
                        onCheckedChange = {
                            drivingModeState = it
                            store.drivingModeRejectCalls = it
                        }
                    )

                    Spacer(Modifier.height(10.dp))
                    Text("Auto-reply template sent to caller", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicInputField(
                        value = drivingReplyTextState,
                        onValueChange = { drivingReplyTextState = it },
                        placeholder = "I am currently driving. Will call back soon."
                    )

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            store.drivingAutoReplyText = drivingReplyTextState.trim()
                            Toast.makeText(context, "Driving auto-reply template saved!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("Save Template", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }
}
