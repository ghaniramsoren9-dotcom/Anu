package com.ghaniram.zoya.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Psychology
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

data class CustomProvider(
    val id: String,
    val name: String,
    val endpoint: String,
    val isActive: Boolean = true
)

/**
 * Sub-Agents Screen matching Page 15 of the specification.
 * Fully interactive with model priority selection and custom provider dialog with dynamic theme support.
 */
@Composable
fun AnuSubAgentsScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    var modelsOrderState by remember { mutableStateOf(store.codingModelsOrder) }
    var customProvidersState by remember { mutableStateOf(store.customProvidersEnabled) }
    var geminiProviderActive by remember { mutableStateOf(store.defaultGeminiProviderActive) }

    var customProvidersList by remember {
        mutableStateOf(listOf<CustomProvider>())
    }

    var showAddProviderDialog by remember { mutableStateOf(false) }
    var newProviderName by remember { mutableStateOf("") }
    var newProviderUrl by remember { mutableStateOf("") }
    var newProviderKey by remember { mutableStateOf("") }

    val availableChips = listOf(
        "3.6 Flash", "3.1 Flash Lite", "2.5 Flash", "3.5 Flash", "2 Flash", "2.5 Flash Lite"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Sub-agents",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Coding Models
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Code, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Coding models", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Tried in order for code generation", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(10.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableChips) { chip ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = colors.chipBackground,
                                modifier = Modifier.clickable {
                                    val formatted = "gemini-${chip.lowercase().replace(" ", "-")}"
                                    if (!modelsOrderState.contains(formatted)) {
                                        modelsOrderState = if (modelsOrderState.isBlank()) formatted else "$modelsOrderState,$formatted"
                                        store.codingModelsOrder = modelsOrderState
                                    }
                                }
                            ) {
                                Text(
                                    text = chip,
                                    fontSize = 11.5.sp,
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    BasicInputField(
                        value = modelsOrderState,
                        onValueChange = {
                            modelsOrderState = it
                            store.codingModelsOrder = it
                        },
                        placeholder = "gemini-3.6-flash,gemini-3.1-flash-lite,gemini-2.5-flash"
                    )

                    Spacer(Modifier.height(8.dp))
                    SettingsTipBanner(
                        text = "Models are evaluated sequentially. If rate limit or error occurs, Anu seamlessly falls back to the next model in the list."
                    )
                }
            }

            // Sub-agent Brain
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Psychology, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sub-agent brain", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    SettingsToggleRow(
                        title = "Custom providers",
                        subtitle = "Applies on next session start",
                        checked = customProvidersState,
                        onCheckedChange = {
                            customProvidersState = it
                            store.customProvidersEnabled = it
                        }
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsTipBanner(
                        text = "Tried top-down; auto-switches to the next on rate-limit/error. Stack several free keys for reliability."
                    )

                    Spacer(Modifier.height(12.dp))

                    // Gemini Default Provider
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.inputBackground,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Gemini", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                                Text("Primary Google AI Studio provider", fontSize = 11.sp, color = colors.textSecondary)
                            }
                            Switch(
                                checked = geminiProviderActive,
                                onCheckedChange = {
                                    geminiProviderActive = it
                                    store.defaultGeminiProviderActive = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colors.accentPrimary,
                                    uncheckedThumbColor = colors.textSecondary,
                                    uncheckedTrackColor = colors.chipBackground
                                )
                            )
                        }
                    }

                    // User Custom Providers
                    customProvidersList.forEach { cp ->
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.inputBackground,
                            border = BorderStroke(1.dp, colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cp.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                                    Text(cp.endpoint, fontSize = 11.sp, color = colors.textSecondary)
                                }
                                IconButton(
                                    onClick = {
                                        customProvidersList = customProvidersList.filter { it.id != cp.id }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Outlined.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { showAddProviderDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.chipBackground, contentColor = colors.textPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("+ Add provider", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showAddProviderDialog) {
        AlertDialog(
            onDismissRequest = { showAddProviderDialog = false },
            title = { Text("Add Custom Provider", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Provider Name", fontSize = 12.sp, color = colors.textSecondary)
                    BasicInputField(
                        value = newProviderName,
                        onValueChange = { newProviderName = it },
                        placeholder = "e.g. DeepSeek / Groq / OpenAI"
                    )

                    Text("Base URL / Endpoint", fontSize = 12.sp, color = colors.textSecondary)
                    BasicInputField(
                        value = newProviderUrl,
                        onValueChange = { newProviderUrl = it },
                        placeholder = "https://api.groq.com/openai/v1"
                    )

                    Text("API Key", fontSize = 12.sp, color = colors.textSecondary)
                    BasicInputField(
                        value = newProviderKey,
                        onValueChange = { newProviderKey = it },
                        placeholder = "gsk_...",
                        isPassword = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProviderName.isNotBlank()) {
                            customProvidersList = customProvidersList + CustomProvider(
                                id = System.currentTimeMillis().toString(),
                                name = newProviderName.trim(),
                                endpoint = newProviderUrl.trim()
                            )
                            newProviderName = ""
                            newProviderUrl = ""
                            newProviderKey = ""
                            showAddProviderDialog = false
                            Toast.makeText(context, "Provider added!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Add", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddProviderDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.cardBackground
        )
    }
}
