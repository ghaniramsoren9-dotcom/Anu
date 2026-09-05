package com.ghaniram.zoya.settings

import android.speech.tts.TextToSpeech
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
import com.ghaniram.zoya.ui.theme.AnuPrimary
import java.util.Locale

data class AnuVoiceOption(
    val tone: String,
    val speaker: String,
    val category: String
)

/**
 * Voice Picker Screen matching Pages 11, 12, 13 of the specification.
 * Interactive previews using Android Text-to-Speech engine.
 */
@Composable
fun AnuVoicePickerScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    var selectedCategory by remember { mutableStateOf(store.selectedVoiceCategory) }
    var selectedSpeaker by remember { mutableStateOf(store.selectedVoiceSpeaker) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isPlayingTone by remember { mutableStateOf<String?>(null) }

    // Initialize TTS for realistic preview
    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.ENGLISH
            }
        }
        ttsEngine = tts
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
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
            Text("Tap to listen, then pick", fontSize = 12.sp, color = colors.textSecondary)
            Spacer(Modifier.height(14.dp))

            // Category Tabs
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
                            com.ghaniram.zoya.ZoyaSessionManager.onSettingsUpdated()
                            Toast.makeText(context, "Selected ${voice.tone} (${voice.speaker})", Toast.LENGTH_SHORT).show()
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

                        // Play Preview Button
                        IconButton(
                            onClick = {
                                isPlayingTone = voice.speaker
                                ttsEngine?.let { engine ->
                                    val phrase = "Hello! I am Anu. How can I help you today?"
                                    when (voice.tone) {
                                        "Gentle", "Soft" -> { engine.setPitch(0.9f); engine.setSpeechRate(0.9f) }
                                        "Upbeat", "Excitable" -> { engine.setPitch(1.2f); engine.setSpeechRate(1.15f) }
                                        "Firm", "Gravelly" -> { engine.setPitch(0.75f); engine.setSpeechRate(0.95f) }
                                        "Youthful", "Bright" -> { engine.setPitch(1.3f); engine.setSpeechRate(1.05f) }
                                        else -> { engine.setPitch(1.0f); engine.setSpeechRate(1.0f) }
                                    }
                                    engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "sample_${voice.speaker}")
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colors.chipBackground)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Preview voice",
                                tint = colors.accentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
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
                    text = "Applies the next time Anu starts."
                )
            }
        }
    }
}
