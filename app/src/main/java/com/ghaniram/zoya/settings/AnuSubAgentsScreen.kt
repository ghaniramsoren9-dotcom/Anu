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
import org.json.JSONArray
import org.json.JSONObject

data class CustomProvider(
    val id: String,
    val name: String,
    val endpoint: String,
    val apiKey: String = "",
    val isActive: Boolean = true
)

data class ModelChipInfo(
    val label: String,
    val modelId: String,
    val category: String
)

/**
 * Sub-Agents Screen matching Page 15 of the specification.
 * Fully interactive with model priority selection, rich coding models,
 * and persistent custom provider management.
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

    fun parseProviders(json: String): List<CustomProvider> = try {
        val arr = JSONArray(json)
        val list = mutableListOf<CustomProvider>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                CustomProvider(
                    id = obj.optString("id", System.currentTimeMillis().toString()),
                    name = obj.optString("name", "Provider"),
                    endpoint = obj.optString("endpoint", ""),
                    apiKey = obj.optString("apiKey", ""),
                    isActive = obj.optBoolean("isActive", true)
                )
            )
        }
        list
    } catch (_: Exception) {
        emptyList()
    }

    fun serializeProviders(list: List<CustomProvider>): String {
        val arr = JSONArray()
        list.forEach { cp ->
            arr.put(
                JSONObject().apply {
                    put("id", cp.id)
                    put("name", cp.name)
                    put("endpoint", cp.endpoint)
                    put("apiKey", cp.apiKey)
                    put("isActive", cp.isActive)
                }
            )
        }
        return arr.toString()
    }

    var customProvidersList by remember {
        mutableStateOf(parseProviders(store.customProvidersJson))
    }

    var selectedModelCategory by remember { mutableStateOf("All") }
    var showAddProviderDialog by remember { mutableStateOf(false) }
    var newProviderName by remember { mutableStateOf("") }
    var newProviderUrl by remember { mutableStateOf("") }
    var newProviderKey by remember { mutableStateOf("") }

    val allCodingModels = listOf(
        ModelChipInfo("Claude 3.7 Sonnet", "claude-3-7-sonnet", "Coding"),
        ModelChipInfo("Claude 3.5 Sonnet", "claude-3-5-sonnet", "Coding"),
        ModelChipInfo("DeepSeek R1", "deepseek-r1", "Reasoning"),
        ModelChipInfo("DeepSeek V3", "deepseek-v3", "Coding"),
        ModelChipInfo("Qwen 2.5 Coder", "qwen-2.5-coder-32b", "Coding"),
        ModelChipInfo("GPT-4o", "gpt-4o", "Coding"),
        ModelChipInfo("o3-mini", "o3-mini", "Reasoning"),
        ModelChipInfo("Gemini 2.5 Pro", "gemini-2.5-pro", "Reasoning"),
        ModelChipInfo("Gemini 2.0 Thinking", "gemini-2.0-flash-thinking", "Reasoning"),
        ModelChipInfo("Gemini 3.6 Flash", "gemini-3.6-flash", "Fast"),
        ModelChipInfo("Gemini 2.5 Flash", "gemini-2.5-flash", "Fast"),
        ModelChipInfo("Gemini 3.1 Flash Lite", "gemini-3.1-flash-lite", "Fast")
    )

    val filteredChips = allCodingModels.filter {
        selectedModelCategory == "All" || it.category.equals(selectedModelCategory, ignoreCase = true)
    }

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
            // Coding Models Card
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Code, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Coding models", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Select models to add to fallback chain for code generation and tasks", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(10.dp))

                    // Model Categories
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(listOf("All", "Coding", "Reasoning", "Fast")) { cat ->
                            ChoiceChipPill(
                                label = cat,
                                isSelected = selectedModelCategory == cat,
                                onClick = { selectedModelCategory = cat }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Model Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filteredChips) { chip ->
                            val isAdded = modelsOrderState.contains(chip.modelId)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isAdded) colors.accentPrimary.copy(alpha = 0.2f) else colors.chipBackground,
                                border = if (isAdded) BorderStroke(1.dp, colors.accentPrimary) else null,
                                modifier = Modifier.clickable {
                                    if (!isAdded) {
                                        val updated = if (modelsOrderState.isBlank()) chip.modelId else "$modelsOrderState,${chip.modelId}"
                                        modelsOrderState = updated
                                        store.codingModelsOrder = updated
                                        Toast.makeText(context, "${chip.label} added to coding chain", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text(
                                    text = if (isAdded) "✓ ${chip.label}" else "+ ${chip.label}",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isAdded) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isAdded) colors.accentPrimary else colors.textPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text("Active Priority Order (comma separated):", fontSize = 11.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    BasicInputField(
                        value = modelsOrderState,
                        onValueChange = {
                            modelsOrderState = it
                            store.codingModelsOrder = it
                        },
                        placeholder = "gemini-2.5-pro,claude-3-7-sonnet,deepseek-r1..."
                    )

                    Spacer(Modifier.height(10.dp))

                    // Preset Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                val preset = "claude-3-7-sonnet,deepseek-r1,qwen-2.5-coder-32b,gemini-2.5-pro,gpt-4o"
                                modelsOrderState = preset
                                store.codingModelsOrder = preset
                                Toast.makeText(context, "Elite Coding preset applied", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Elite Coding", fontSize = 11.sp, color = colors.textPrimary)
                        }

                        OutlinedButton(
                            onClick = {
                                val preset = "deepseek-r1,o3-mini,gemini-2.0-flash-thinking,gemini-2.5-pro"
                                modelsOrderState = preset
                                store.codingModelsOrder = preset
                                Toast.makeText(context, "Deep Reasoning preset applied", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Deep Reasoning", fontSize = 11.sp, color = colors.textPrimary)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    SettingsTipBanner(
                        text = "Models are evaluated sequentially. If rate limit or error occurs, Anu seamlessly falls back to the next model in the list."
                    )
                }
            }

            // Sub-agent Brain Card
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

                    // User Custom Providers List
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
                                    if (cp.apiKey.isNotBlank()) {
                                        Text("Key: ••••••••••••", fontSize = 10.sp, color = colors.accentPrimary)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val updated = customProvidersList.filter { it.id != cp.id }
                                        customProvidersList = updated
                                        store.customProvidersJson = serializeProviders(updated)
                                        Toast.makeText(context, "${cp.name} removed", Toast.LENGTH_SHORT).show()
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
                        onClick = {
                            newProviderName = ""
                            newProviderUrl = ""
                            newProviderKey = ""
                            showAddProviderDialog = true
                        },
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
                    Text("Quick Presets:", fontSize = 11.5.sp, color = colors.textSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                newProviderName = "DeepSeek"
                                newProviderUrl = "https://api.deepseek.com/v1"
                            },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("DeepSeek", fontSize = 10.5.sp, color = colors.textPrimary)
                        }
                        OutlinedButton(
                            onClick = {
                                newProviderName = "OpenRouter"
                                newProviderUrl = "https://openrouter.ai/api/v1"
                            },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("OpenRouter", fontSize = 10.5.sp, color = colors.textPrimary)
                        }
                        OutlinedButton(
                            onClick = {
                                newProviderName = "Groq"
                                newProviderUrl = "https://api.groq.com/openai/v1"
                            },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Groq", fontSize = 10.5.sp, color = colors.textPrimary)
                        }
                    }

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
                        placeholder = "Paste API key (gsk_..., sk-..., etc.)",
                        isPassword = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProviderName.isNotBlank()) {
                            val newProvider = CustomProvider(
                                id = System.currentTimeMillis().toString(),
                                name = newProviderName.trim(),
                                endpoint = newProviderUrl.trim(),
                                apiKey = newProviderKey.trim(),
                                isActive = true
                            )
                            val updated = customProvidersList + newProvider
                            customProvidersList = updated
                            store.customProvidersJson = serializeProviders(updated)
                            newProviderName = ""
                            newProviderUrl = ""
                            newProviderKey = ""
                            showAddProviderDialog = false
                            Toast.makeText(context, "Provider added & saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please enter a provider name", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Add & Save", color = Color.White)
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
