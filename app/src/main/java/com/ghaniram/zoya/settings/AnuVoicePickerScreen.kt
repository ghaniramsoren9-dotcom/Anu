package com.ghaniram.zoya.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.AnuSettingsStore
import com.ghaniram.zoya.GeminiVoicePreview
import com.ghaniram.zoya.ZoyaSessionManager
import com.ghaniram.zoya.ui.theme.AnuPrimary
import kotlinx.coroutines.launch

data class AnuVoiceOption(
    val tone: String,
    val speaker: String,
    val category: String
)

/**
 * Voice Picker — previews use real Gemini TTS voices (Aoede, Kore, …),
 * not the robotic Android system TTS engine.
 */
@Composable
fun AnuVoicePickerScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf(store.selectedVoiceCategory) }
    var selectedSpeaker by remember { mutableStateOf(store.selectedVoiceSpeaker) }
    var isPlayingTone by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { GeminiVoicePreview.stop() }
    }

    val anuVoices = listOf(
        AnuVoiceOption("Breezy", "Aoede", "Anu"),
        AnuVoiceOption("Firm", "Kore", "Anu"),
        AnuVoiceOption("Youthful", "Leda", "Anu"),
        AnuVoiceOption("Bright", "Zephyr", "Anu"),
        AnuVoiceOption("Upbeat", "Laomedeia", "Anu"),
        AnuVoiceOption("Smooth", "Despina", "Anu"),
        AnuVoiceOption("Clear", "Erinome", "Anu"),
        AnuVoiceOption("Easy-going", "Callirrhoe", "Anu"),
        AnuVoiceOption("Bright", "Autonoe", "Anu"),
        AnuVoiceOption("Mature", "Gacrux", "Anu"),
        AnuVoiceOption("Forward", "Pulcherrima", "Anu"),
        AnuVoiceOption("Warm", "Sulafat", "Anu"),
        AnuVoiceOption("Gentle", "Vindemiatrix", "Anu")
    )

    val fridayVoices = listOf(
        AnuVoiceOption("Firm", "Kore", "Friday"),
        AnuVoiceOption("Breezy", "Aoede", "Friday"),
        AnuVoiceOption("Youthful", "Leda", "Friday"),
        AnuVoiceOption("Bright", "Zephyr", "Friday"),
        AnuVoiceOption("Upbeat", "Laomedeia", "Friday"),
        AnuVoiceOption("Smooth", "Despina", "Friday"),
        AnuVoiceOption("Clear", "Erinome", "Friday"),
        AnuVoiceOption("Easy-going", "Callirrhoe", "Friday"),
        AnuVoiceOption("Mature", "Gacrux", "Friday"),
        AnuVoiceOption("Warm", "Sulafat", "Friday")
    )

    val venomVoices = listOf(
        AnuVoiceOption("Gravelly", "Algenib", "Venom"),
        AnuVoiceOption("Informative", "Charon", "Venom"),
        AnuVoiceOption("Excitable", "Fenrir", "Venom"),
        AnuVoiceOption("Upbeat", "Puck", "Venom"),
        AnuVoiceOption("Firm", "Orus", "Venom"),
        AnuVoiceOption("Breathy", "Enceladus", "Venom"),
        AnuVoiceOption("Clear", "Iapetus", "Venom"),
        AnuVoiceOption("Easy-going", "Umbriel", "Venom"),
        AnuVoiceOption("Smooth", "Algieba", "Venom"),
        AnuVoiceOption("Informative", "Rasalgethi", "Venom"),
        AnuVoiceOption("Even", "Schedar", "Venom"),
        AnuVoiceOption("Casual", "Zubenelgenubi", "Venom"),
        AnuVoiceOption("Lively", "Sadachbia", "Venom"),
        AnuVoiceOption("Soft", "Achernar", "Venom")
    )

    val currentList = when (selectedCategory) {
        "Friday" -> fridayVoices
        "Venom" -> venomVoices
        else -> anuVoices
    }

    fun playGeminiPreview(voice: AnuVoiceOption) {
        val apiKey = store.customGeminiKey.trim()
        if (apiKey.isBlank()) {
            Toast.makeText(context, "Add Gemini API key in Settings → Personal first", Toast.LENGTH_LONG).show()
            return
        }
        isPlayingTone = voice.speaker
        previewError = null
        scope.launch {
            val err = GeminiVoicePreview.playPreview(
                apiKey = apiKey,
                voiceName = voice.speaker,
                phrase = "Hello! I am Anu. How can I help you today?"
            )
            isPlayingTone = null
            if (err != null) {
                previewError = err
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnuDarkBackground)
    ) {
        SettingsTopBar(
            title = "Voice",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Tap ▶ to hear the real Gemini voice, then pick",
                fontSize = 12.sp,
                color = colors.textSecondary
            )
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.cardBackground)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Anu", "Friday", "Venom").forEach { cat ->
                    val isCatSelected = selectedCategory.equals(cat, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCatSelected) colors.accentPrimary else Color.Transparent)
                            .clickable { selectedCategory = cat }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat,
                            fontSize = 12.5.sp,
                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCatSelected) Color.White else colors.textSecondary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(currentList, key = { "${it.category}_${it.speaker}_${it.tone}" }) { voice ->
                val isSelected = selectedSpeaker == voice.speaker
                val isLoading = isPlayingTone == voice.speaker

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) colors.accentPrimary else colors.cardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedSpeaker = voice.speaker
                            store.selectedVoiceCategory = voice.category
                            store.selectedVoiceTone = voice.tone
                            store.selectedVoiceSpeaker = voice.speaker
                            ZoyaSessionManager.reconnectForCriticalSettings()
                            Toast.makeText(
                                context,
                                "Selected ${voice.tone} (${voice.speaker})",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = voice.tone,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = voice.speaker,
                                fontSize = 11.5.sp,
                                color = colors.textSecondary
                            )
                        }

                        IconButton(
                            onClick = { playGeminiPreview(voice) },
                            enabled = isPlayingTone == null,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colors.chipBackground)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.accentPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Preview Gemini voice",
                                    tint = colors.accentPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (isSelected) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingsTipBanner(
                    text = "Preview uses real Gemini voices (needs internet + API key). Selection applies on next Anu session start."
                )
            }
        }
    }
}
