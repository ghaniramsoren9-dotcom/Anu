package com.ghaniram.zoya.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.AnuSettingsStore
import com.ghaniram.zoya.ui.theme.LocalAnuColors

/**
 * Main Settings Index Screen matching Page 7 of the specification.
 * Completely branded with Anu and supporting full Light, Dark, and System Default themes.
 */
@Composable
fun AnuSettingsHomeScreen(
    store: AnuSettingsStore,
    onNavigate: (SettingsScreenDestination) -> Unit
) {
    val colors = LocalAnuColors.current
    val settingsVersion by store.stateVersion.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Personalize how Anu works for you",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // GEMINI API KEY STATUS BANNER
            item {
                val hasKey = store.customGeminiKey.isNotBlank()
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (hasKey) colors.cardBackground else colors.accentPrimary.copy(alpha = 0.08f),
                    border = BorderStroke(
                        1.dp,
                        if (hasKey) Color(0xFF10B981).copy(alpha = 0.4f) else colors.accentPrimary.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(SettingsScreenDestination.PERSONAL) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (hasKey) Color(0xFF10B981).copy(alpha = 0.15f) else colors.accentPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.VpnKey,
                                    contentDescription = null,
                                    tint = if (hasKey) Color(0xFF10B981) else colors.accentPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (hasKey) "Gemini API Key Active" else "Add Gemini API Key",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                                if (hasKey) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "Ready",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (hasKey)
                                    "Tap to test, view, or update your API key"
                                else
                                    "Required for live AI voice, vision & conversations",
                                fontSize = 11.5.sp,
                                color = colors.textSecondary
                            )
                        }

                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // THEME / APPEARANCE SWITCHER
            item {
                SettingsSectionHeader("Appearance & Theme")
                val currentThemeMode = store.themeMode
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    shadowElevation = if (colors.isDark) 0.dp else 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Palette,
                                    contentDescription = null,
                                    tint = colors.accentPrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Theme Mode",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                            }
                            Text(
                                text = currentThemeMode,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.accentPrimary
                            )
                        }

                        Spacer(Modifier.height(12.dp))

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
                                        .clickable { store.themeMode = modeKey }
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
                                            modifier = Modifier.size(15.dp)
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

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(SettingsScreenDestination.THEME_CUSTOMIZATION) }
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Customize presets, fonts & surfaces",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ACCOUNT
            item {
                SettingsSectionHeader("Account")
                SettingsItemCard(
                    icon = Icons.Outlined.Person,
                    title = "Personal",
                    subtitle = "Your name, music, Gemini & YouTube keys",
                    onClick = { onNavigate(SettingsScreenDestination.PERSONAL) }
                )
            }

            // ASSISTANT
            item {
                SettingsSectionHeader("Assistant")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsItemCard(
                        icon = Icons.Outlined.Headphones,
                        title = "Anu",
                        subtitle = "Persona, girlfriend mode, voice, language",
                        onClick = { onNavigate(SettingsScreenDestination.ASSISTANT_ANU) }
                    )
                    SettingsItemCard(
                        icon = Icons.Outlined.Bolt,
                        title = "Skills",
                        subtitle = "Installed playbooks and the online skill store",
                        onClick = { onNavigate(SettingsScreenDestination.SKILLS) }
                    )
                    SettingsItemCard(
                        icon = Icons.Outlined.SmartToy,
                        title = "Sub-agents",
                        subtitle = "Coding models and background agents",
                        onClick = { onNavigate(SettingsScreenDestination.SUB_AGENTS) }
                    )
                }
            }

            // WORK & MESSAGES
            item {
                SettingsSectionHeader("Work & Messages")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsItemCard(
                        icon = Icons.Outlined.Email,
                        title = "Email",
                        subtitle = "Let Anu send mail from your address",
                        onClick = { onNavigate(SettingsScreenDestination.EMAIL) }
                    )
                    SettingsItemCard(
                        icon = Icons.Outlined.Forum,
                        title = "WhatsApp groups & reports",
                        subtitle = "Your groups, and the report formats she fills in",
                        onClick = { onNavigate(SettingsScreenDestination.WHATSAPP_REPORTS) }
                    )
                    SettingsItemCard(
                        icon = Icons.Outlined.Share,
                        title = "Social media",
                        subtitle = "Handle, caption voice, daily story, scheduled posts",
                        onClick = { onNavigate(SettingsScreenDestination.SOCIAL_MEDIA) }
                    )
                }
            }

            // CONNECTED ACCOUNTS
            item {
                SettingsSectionHeader("Connected Accounts")
                SettingsItemCard(
                    icon = Icons.Outlined.Hub,
                    title = "Connectors",
                    subtitle = "GitHub, Notion, Telegram and more",
                    onClick = { onNavigate(SettingsScreenDestination.CONNECTORS) }
                )
            }

            // MEMORY & DATA
            item {
                SettingsSectionHeader("Memory & Data")
                SettingsItemCard(
                    icon = Icons.Outlined.CloudUpload,
                    title = "Backup",
                    subtitle = "Export & restore your memories and chats",
                    onClick = { onNavigate(SettingsScreenDestination.BACKUP_RESTORE) }
                )
            }

            // SYSTEM
            item {
                SettingsSectionHeader("System")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsItemCard(
                        icon = Icons.Outlined.Tune,
                        title = "Advanced",
                        subtitle = "Behaviour, safety, permissions",
                        onClick = { onNavigate(SettingsScreenDestination.ADVANCED) }
                    )
                    SettingsItemCard(
                        icon = Icons.Outlined.Extension,
                        title = "Optional",
                        subtitle = "Extra integrations — Maps / Places",
                        onClick = { onNavigate(SettingsScreenDestination.OPTIONAL_PLACES) }
                    )
                }
            }

            // Footer
            item {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Anu v4.0.0",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}
