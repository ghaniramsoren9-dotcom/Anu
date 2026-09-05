package com.ghaniram.zoya.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
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
 * Typing Settings Screen matching Page 25 of the specification.
 * Interactive human typing simulator controls with dynamic theme support.
 */
@Composable
fun AnuTypingScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val colors = LocalAnuColors.current
    var humanTypingState by remember { mutableStateOf(store.humanTypingInEditors) }
    var speedState by remember { mutableStateOf(store.typingSpeed) }
    var codingTypingState by remember { mutableStateOf(store.realisticTypingWhileCoding) }
    var appsState by remember { mutableStateOf(store.typingTargetApps) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Typing",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Realistic Typing
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Keyboard, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Realistic typing", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Type like a human in editors", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(12.dp))

                    SettingsToggleRow(
                        title = "Human typing in editors",
                        subtitle = "Notepad, Docs, code — types character by character with natural pacing",
                        checked = humanTypingState,
                        onCheckedChange = {
                            humanTypingState = it
                            store.humanTypingInEditors = it
                        }
                    )

                    Spacer(Modifier.height(14.dp))
                    Text("Typing speed", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Slow", "Normal", "Fast").forEach { spd ->
                            val isSelected = speedState.equals(spd, ignoreCase = true)
                            ChoiceChipPill(
                                label = spd,
                                isSelected = isSelected,
                                onClick = {
                                    speedState = spd
                                    store.typingSpeed = spd
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    SettingsToggleRow(
                        title = "Realistic typing while coding",
                        subtitle = "Applies to coding tasks too",
                        checked = codingTypingState,
                        onCheckedChange = {
                            codingTypingState = it
                            store.realisticTypingWhileCoding = it
                        }
                    )

                    Spacer(Modifier.height(14.dp))
                    Text("Apps where it's on", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicInputField(
                        value = appsState,
                        onValueChange = {
                            appsState = it
                            store.typingTargetApps = it
                        },
                        placeholder = "com.google.android.keep,com.termux..."
                    )

                    Spacer(Modifier.height(8.dp))
                    SettingsTipBanner(
                        text = "Comma-separated package names. Everywhere else stays instant."
                    )
                }
            }
        }
    }
}
