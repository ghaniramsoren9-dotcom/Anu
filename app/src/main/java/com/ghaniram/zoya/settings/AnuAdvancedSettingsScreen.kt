package com.ghaniram.zoya.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ghaniram.zoya.AnuSettingsStore

/**
 * Advanced Settings Index Screen matching Page 21 of the specification.
 * Completely structured and branded for Anu.
 */
@Composable
fun AnuAdvancedSettingsScreen(
    store: AnuSettingsStore,
    onNavigate: (SettingsScreenDestination) -> Unit,
    onBack: () -> Unit
) {
    val colors = com.ghaniram.zoya.ui.theme.LocalAnuColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Advanced",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // LOOK AND FEEL
            item {
                SettingsSectionHeader("Look and Feel")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsItemCard(
                        icon = Icons.Outlined.Palette,
                        title = "Theme",
                        subtitle = "Colours, typeface, text size and corners",
                        onClick = { onNavigate(SettingsScreenDestination.THEME_CUSTOMIZATION) }
                    )
                    SettingsItemCard(
                        icon = Icons.Outlined.Brightness7,
                        title = "Appearance",
                        subtitle = "Orb style, colour and size",
                        onClick = { onNavigate(SettingsScreenDestination.APPEARANCE_ORB) }
                    )
                    SettingsItemCard(
                        icon = Icons.Outlined.SettingsInputComponent,
                        title = "Behaviour",
                        subtitle = "Floating orb, echo guard, start on boot",
                        onClick = { onNavigate(SettingsScreenDestination.BEHAVIOUR) }
                    )
                    SettingsItemCard(
                        icon = Icons.Outlined.Keyboard,
                        title = "Typing",
                        subtitle = "Human-paced typing in editors",
                        onClick = { onNavigate(SettingsScreenDestination.TYPING) }
                    )
                }
            }

            // SAFETY AND ACCESS
            item {
                SettingsSectionHeader("Safety and Access")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsItemCard(
                        icon = Icons.Outlined.Security,
                        title = "Voice Guardian",
                        subtitle = "Answer only your voice",
                        onClick = { onNavigate(SettingsScreenDestination.VOICE_GUARDIAN) }
                    )
                    SettingsItemCard(
                        icon = Icons.Outlined.WarningAmber,
                        title = "Emergency SOS",
                        subtitle = "Contacts, siren and what gets sent",
                        onClick = { onNavigate(SettingsScreenDestination.EMERGENCY_SOS) }
                    )
                    SettingsItemCard(
                        icon = Icons.Outlined.TouchApp,
                        title = "Touch Guard",
                        subtitle = "Watch the phone while you are away from it",
                        onClick = { onNavigate(SettingsScreenDestination.TOUCH_GUARD) }
                    )
                    SettingsItemCard(
                        icon = Icons.Outlined.Lock,
                        title = "Screen lock",
                        subtitle = "Waking the phone, and unlocking it for you",
                        onClick = { onNavigate(SettingsScreenDestination.SCREEN_LOCK) }
                    )
                    SettingsItemCard(
                        icon = Icons.Outlined.Checklist,
                        title = "Permissions",
                        subtitle = "Grant the access Anu's features need",
                        onClick = { onNavigate(SettingsScreenDestination.PERMISSIONS) }
                    )
                }
            }

            // SYSTEM
            item {
                SettingsSectionHeader("System")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsItemCard(
                        icon = Icons.Outlined.Reply,
                        title = "WhatsApp auto-reply",
                        subtitle = "Answer messages for you while you are away",
                        onClick = { onNavigate(SettingsScreenDestination.WHATSAPP_AUTO_REPLY) }
                    )
                    SettingsItemCard(
                        icon = Icons.Outlined.NotificationsActive,
                        title = "Event triggers",
                        subtitle = "What she announces on her own",
                        onClick = { onNavigate(SettingsScreenDestination.EVENT_TRIGGERS) }
                    )
                }
            }
        }
    }
}
