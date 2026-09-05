package com.ghaniram.zoya.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.AnuSettingsStore
import com.ghaniram.zoya.ui.theme.LocalAnuColors

/**
 * WhatsApp Auto-reply Screen matching Pages 20 & 21 of the specification.
 * Interactive trigger rules, templates, and filter lists with dynamic theme support.
 */
@Composable
fun AnuWhatsAppAutoReplyScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val colors = LocalAnuColors.current
    var autoReplyEnabled by remember { mutableStateOf(store.whatsAppAutoReplyEnabled) }
    var onlyWhileAsleep by remember { mutableStateOf(store.replyOnlyWhileAsleep) }
    var includeGroups by remember { mutableStateOf(store.replyIncludeGroups) }

    var instructionsState by remember { mutableStateOf(store.replyInstructions) }
    var fallbackNoInternetState by remember { mutableStateOf(store.replyFallbackNoInternet) }
    var signatureNoteState by remember { mutableStateOf(store.replySignatureNote) }

    var onlyChatsState by remember { mutableStateOf(store.replyOnlyChats) }
    var neverChatsState by remember { mutableStateOf(store.replyNeverChats) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "WhatsApp auto-reply",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Explanatory Banner
            item {
                SettingsTipBanner(
                    text = "When this is on, Anu answers incoming WhatsApp messages for you — while she is asleep and while the phone is locked. The reply is written by a helper model following your instructions below and sent through WhatsApp's own notification reply. Notification access is required."
                )
            }

            // Master Switches
            item {
                SettingsCardContainer {
                    SettingsToggleRow(
                        title = "Reply to WhatsApp messages",
                        checked = autoReplyEnabled,
                        onCheckedChange = {
                            autoReplyEnabled = it
                            store.whatsAppAutoReplyEnabled = it
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Only while Anu is asleep",
                        checked = onlyWhileAsleep,
                        onCheckedChange = {
                            onlyWhileAsleep = it
                            store.replyOnlyWhileAsleep = it
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Include groups",
                        subtitle = "Also formulate responses to tagged group chats",
                        checked = includeGroups,
                        onCheckedChange = {
                            includeGroups = it
                            store.replyIncludeGroups = it
                        }
                    )
                }
            }

            // How She Replies
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Chat, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("How she replies", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(12.dp))

                    Text("Instructions", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicInputField(
                        value = instructionsState,
                        onValueChange = {
                            instructionsState = it
                            store.replyInstructions = it
                        },
                        placeholder = "Politely say I am busy..."
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("If there is no internet", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicInputField(
                        value = fallbackNoInternetState,
                        onValueChange = {
                            fallbackNoInternetState = it
                            store.replyFallbackNoInternet = it
                        },
                        placeholder = "Abhi busy hoon, thodi der me reply karta hoon."
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("Auto-reply note", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicInputField(
                        value = signatureNoteState,
                        onValueChange = {
                            signatureNoteState = it
                            store.replySignatureNote = it
                        },
                        placeholder = "- Anu (auto-reply)"
                    )
                }
            }

            // Which Chats
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FilterList, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Which chats", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(12.dp))

                    Text("Only these chats (leave empty for every chat)", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicInputField(
                        value = onlyChatsState,
                        onValueChange = {
                            onlyChatsState = it
                            store.replyOnlyChats = it
                        },
                        placeholder = "Leave empty for every chat"
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("Never these chats", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicInputField(
                        value = neverChatsState,
                        onValueChange = {
                            neverChatsState = it
                            store.replyNeverChats = it
                        },
                        placeholder = "e.g. Mummy, Boss"
                    )
                }
            }

            // What She Said (Activity Log)
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("What she said", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "0 replies sent today. Up to 12 per chat, 60 in a day limit enforced for safety.",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsTipBanner(
                        text = "Anu never answers any message that looks like an OTP, a bank alert, or any one-time security code."
                    )
                }
            }
        }
    }
}
