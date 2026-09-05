package com.ghaniram.zoya.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
 * Social Media Settings Screen matching Page 18 of the specification.
 * Fully interactive with handles, platforms, daily story, and auto-post switches with dynamic theme support.
 */
@Composable
fun AnuSocialMediaScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val colors = LocalAnuColors.current
    var handleState by remember { mutableStateOf(store.socialHandle) }
    var platformState by remember { mutableStateOf(store.socialPlatform) }
    var captionVoiceState by remember { mutableStateOf(store.socialCaptionVoice) }
    var dailyStoryState by remember { mutableStateOf(store.dailyStoryEnabled) }
    var autoPostState by remember { mutableStateOf(store.autoPostWithoutAsking) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Social media",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Your Handle
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AlternateEmail, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Your handle", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Printed small at the bottom of posters she makes", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    BasicInputField(
                        value = handleState,
                        onValueChange = {
                            handleState = it
                            store.socialHandle = it
                        },
                        placeholder = "@yourname"
                    )

                    Spacer(Modifier.height(8.dp))
                    SettingsTipBanner(
                        text = "Leave this empty and posters carry no watermark at all — better than a placeholder nobody meant to post."
                    )
                }
            }

            // Where Posts Go
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Share, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Where posts go", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Used when you don't name a platform", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Instagram", "Facebook", "X", "WhatsApp Status").forEach { plat ->
                            val isSelected = platformState.equals(plat, ignoreCase = true)
                            ChoiceChipPill(
                                label = plat,
                                isSelected = isSelected,
                                onClick = {
                                    platformState = plat
                                    store.socialPlatform = plat
                                }
                            )
                        }
                    }
                }
            }

            // Caption Voice
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Caption voice", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("How her captions should sound", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    BasicInputField(
                        value = captionVoiceState,
                        onValueChange = {
                            captionVoiceState = it
                            store.socialCaptionVoice = it
                        },
                        placeholder = "e.g. funny, seedha simple, motivational"
                    )
                }
            }

            // Daily Story
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CameraAlt, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Daily story", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("A fresh quote poster every morning", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(10.dp))

                    SettingsTipBanner(
                        text = "Each morning Anu writes a new line about something you actually care about — picked from what she remembers of your interests — and renders a 9:16 poster ready for story."
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsToggleRow(
                        title = "Daily story",
                        checked = dailyStoryState,
                        onCheckedChange = {
                            dailyStoryState = it
                            store.dailyStoryEnabled = it
                        }
                    )
                }
            }

            // Post It For Me
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Post it for me", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Publish without asking — daily story and scheduled posts", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(10.dp))

                    SettingsTipBanner(
                        text = "With this ON, Anu publishes on her own at the scheduled time. With this OFF, she renders the poster, sends you a notification with a preview, and waits for a tap before posting."
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsToggleRow(
                        title = "Auto-post",
                        checked = autoPostState,
                        onCheckedChange = {
                            autoPostState = it
                            store.autoPostWithoutAsking = it
                        }
                    )
                }
            }

            // Scheduled Posts
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scheduled posts", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Waiting to be prepared", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Nothing scheduled. Ask Anu — 'kal subah 9 baje ek story laga dena' — and it shows up here.",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            // What She Uses
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lightbulb, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("What she uses", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Where story ideas come from", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(10.dp))

                    SettingsTipBanner(
                        text = "Story ideas come from what Anu remembers about your INTERESTS — the gym, coding, music. Names, family, money, health, schedules and anything private are filtered out before an idea reaches the design step."
                    )
                }
            }
        }
    }
}
