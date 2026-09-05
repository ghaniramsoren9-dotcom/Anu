package com.ghaniram.zoya.settings

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.AnuSettingsStore
import com.ghaniram.zoya.ZoyaSessionManager
import com.ghaniram.zoya.ui.theme.LocalAnuColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Personal Settings Screen matching Page 8 of the specification.
 * Full Theme adaptivity, manual Gemini API Key management, clipboard paste, and live test.
 */
@Composable
fun AnuPersonalSettingsScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    val scope = rememberCoroutineScope()

    var nameState by remember { mutableStateOf(store.userName) }
    var genderState by remember { mutableStateOf(store.userGender) }
    var phoneState by remember { mutableStateOf(store.userPhone) }
    var musicAppState by remember { mutableStateOf(store.musicApp) }
    var favoriteSongState by remember { mutableStateOf(store.favoriteSong) }
    var geminiKeyState by remember { mutableStateOf(store.customGeminiKey) }
    var ytChannelState by remember { mutableStateOf(store.youtubeChannel) }
    var ytKeyState by remember { mutableStateOf(store.youtubeApiKey) }
    var showGeminiKey by remember { mutableStateOf(false) }

    var isTestingKey by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Personal",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // GEMINI API KEY - Placed prominently at the top
            item {
                SettingsCardContainer {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.VpnKey, null, tint = colors.accentPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Gemini API Key", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        }
                        if (store.customGeminiKey.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "Saved",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Powers Anu's real-time voice, vision camera, and conversational intelligence. Stored securely on your device.",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsTipBanner(
                        text = "Get a free key from Google AI Studio: Tap the link below, click 'Create API key', and paste it here."
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Open Google AI Studio to get a free key →",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accentPrimary
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.inputBackground)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (geminiKeyState.isEmpty()) {
                                Text(
                                    "Paste AIzaSy... key here",
                                    color = colors.textSecondary.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = geminiKeyState,
                                onValueChange = {
                                    geminiKeyState = it
                                    testResult = null
                                },
                                visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
                                cursorBrush = SolidColor(colors.accentPrimary),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Paste Button
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                if (!clip.isNullOrBlank()) {
                                    geminiKeyState = clip
                                    testResult = null
                                    Toast.makeText(context, "API Key pasted from clipboard!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.ContentPaste,
                                contentDescription = "Paste from clipboard",
                                tint = colors.accentPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Show / Hide Key
                        IconButton(
                            onClick = { showGeminiKey = !showGeminiKey },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (showGeminiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Toggle key visibility",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Test key feedback banner
                    testResult?.let { (success, message) ->
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (success) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, if (success) Color(0xFF10B981).copy(alpha = 0.3f) else Color(0xFFEF4444).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (success) Color(0xFF10B981) else Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = message,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (success) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Save Button
                        Button(
                            onClick = {
                                val trimmed = geminiKeyState.trim()
                                store.customGeminiKey = trimmed
                                ZoyaSessionManager.onApiKeyUpdated(trimmed)
                                Toast.makeText(context, "Gemini API key saved securely!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("Save Key", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }

                        // Test Key Button
                        OutlinedButton(
                            onClick = {
                                val keyToTest = geminiKeyState.trim()
                                if (keyToTest.isBlank()) {
                                    testResult = Pair(false, "Please enter an API key first")
                                    return@OutlinedButton
                                }
                                isTestingKey = true
                                testResult = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val client = OkHttpClient.Builder()
                                                .connectTimeout(8, TimeUnit.SECONDS)
                                                .readTimeout(8, TimeUnit.SECONDS)
                                                .build()
                                            val request = Request.Builder()
                                                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$keyToTest")
                                                .build()
                                            client.newCall(request).execute().use { response ->
                                                if (response.isSuccessful) {
                                                    Pair(true, "API key is valid and working!")
                                                } else {
                                                    Pair(false, "API Error: ${response.code} ${response.message}")
                                                }
                                            }
                                        }.getOrElse {
                                            Pair(false, "Connection error: ${it.message ?: "Network error"}")
                                        }
                                    }
                                    isTestingKey = false
                                    testResult = result
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.cardBorder),
                            modifier = Modifier.height(40.dp)
                        ) {
                            if (isTestingKey) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.accentPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Testing...", fontSize = 12.sp, color = colors.textPrimary)
                            } else {
                                Icon(Icons.Outlined.Refresh, null, tint = colors.accentPrimary, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Test Key", fontSize = 12.sp, color = colors.textPrimary)
                            }
                        }
                    }
                }
            }

            // Your Name
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Person, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Your name", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("How Anu addresses you", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    BasicInputField(
                        value = nameState,
                        onValueChange = {
                            nameState = it
                            store.userName = it
                        },
                        placeholder = "Ghaniram"
                    )
                }
            }

            // You Are (Gender)
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Person, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("You are", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("So Anu uses the right words and verb conjugations for you", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Male", "Female", "Prefer not to say").forEach { option ->
                            val isSelected = genderState.equals(option, ignoreCase = true)
                            ChoiceChipPill(
                                label = option,
                                isSelected = isSelected,
                                onClick = {
                                    genderState = option
                                    store.userGender = option
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    SettingsTipBanner(
                        text = "Hindi and Odia change their verbs with gender, so Anu needs this to speak naturally with proper respect."
                    )
                }
            }

            // Phone Number
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Phone, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Phone number", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Optional — used for emergency contacts and account profile", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    BasicInputField(
                        value = phoneState,
                        onValueChange = {
                            phoneState = it
                            store.userPhone = it
                        },
                        placeholder = "+91 9876543210"
                    )
                }
            }

            // Music
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.MusicNote, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Music", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Default app and a song she'll reach for", fontSize = 11.5.sp, color = colors.textSecondary)

                    Spacer(Modifier.height(12.dp))
                    Text("Play with", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("YT Music", "Spotify", "YouTube", "Gaana").forEach { app ->
                            val isSelected = musicAppState.equals(app, ignoreCase = true)
                            ChoiceChipPill(
                                label = app,
                                isSelected = isSelected,
                                onClick = {
                                    musicAppState = app
                                    store.musicApp = app
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Favorite song", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicInputField(
                        value = favoriteSongState,
                        onValueChange = {
                            favoriteSongState = it
                            store.favoriteSong = it
                        },
                        placeholder = "e.g. Believer, Rangabati"
                    )
                }
            }

            // YouTube
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Subscriptions, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("YouTube", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Your channel — add your own API key only if you hit the daily limit", fontSize = 11.5.sp, color = colors.textSecondary)

                    Spacer(Modifier.height(12.dp))
                    Text("Channel", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicInputField(
                        value = ytChannelState,
                        onValueChange = {
                            ytChannelState = it
                            store.youtubeChannel = it
                        },
                        placeholder = "@yourchannel"
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("Data API key", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicInputField(
                        value = ytKeyState,
                        onValueChange = {
                            ytKeyState = it
                        },
                        placeholder = "Optional YouTube Data API key",
                        isPassword = true
                    )

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            store.youtubeApiKey = ytKeyState.trim()
                            store.youtubeChannel = ytChannelState.trim()
                            Toast.makeText(context, "YouTube settings saved!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("Save YouTube", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun BasicInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalAnuColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.inputBackground,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = colors.textSecondary.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(colors.accentPrimary),
                singleLine = true,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
