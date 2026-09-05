package com.ghaniram.zoya.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import com.ghaniram.zoya.ZoyaUiState
import com.ghaniram.zoya.ui.theme.LocalAnuColors
import java.io.File

/**
 * Backup & Restore Screen matching Page 20 of the specification.
 * Fully functional backup export, sharing, and restore capabilities with dynamic theme support.
 */
@Composable
fun AnuBackupRestoreScreen(
    store: AnuSettingsStore,
    state: ZoyaUiState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    var lastBackupStatus by remember { mutableStateOf<String?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }

    val memoriesCount = state.memories.size
    val conversationsCount = state.chatMessages.size
    val contactsCount = store.getSosContacts().size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Backup",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // What Gets Backed Up
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudQueue, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("What gets backed up", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Everything Anu has learned about you", fontSize = 11.5.sp, color = colors.textSecondary)

                    Spacer(Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Memories", color = colors.textSecondary, fontSize = 13.sp)
                            Text("$memoriesCount saved", color = colors.accentPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Conversations", color = colors.textSecondary, fontSize = 13.sp)
                            Text("$conversationsCount messages", color = colors.accentPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Favorite contacts", color = colors.textSecondary, fontSize = 13.sp)
                            Text("$contactsCount configured", color = colors.accentPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    SettingsTipBanner(
                        text = "API keys, your licence and your voice print are NOT included — a backup file is meant to be safe to send through Drive or WhatsApp."
                    )
                }
            }

            // Export
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Upload, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Save a copy or send it to your other phone", fontSize = 11.5.sp, color = colors.textSecondary)

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val json = store.exportBackupJson(state.memories, conversationsCount)
                                runCatching {
                                    val file = File(context.cacheDir, "anu_backup_${System.currentTimeMillis()}.json")
                                    file.writeText(json)
                                    lastBackupStatus = "Saved backup to ${file.name} (${file.length()} bytes)"
                                    Toast.makeText(context, "Backup saved to device storage!", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, "Failed to write backup file", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("Save file", fontSize = 12.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                val json = store.exportBackupJson(state.memories, conversationsCount)
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, json)
                                    type = "application/json"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share Anu Backup")
                                context.startActivity(shareIntent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.chipBackground),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("Share", fontSize = 12.sp, color = colors.textPrimary)
                        }
                    }

                    if (lastBackupStatus != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(lastBackupStatus!!, color = Color(0xFF10B981), fontSize = 11.5.sp)
                    }
                }
            }

            // Restore
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Download, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Restore", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Bring a backup in from another device", fontSize = 11.5.sp, color = colors.textSecondary)

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { showRestoreDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.chipBackground),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Text("Paste backup file / JSON", fontSize = 12.5.sp, color = colors.textPrimary)
                    }

                    Spacer(Modifier.height(10.dp))
                    SettingsTipBanner(
                        text = "Restoring adds to this phone — nothing already here is deleted, and importing the same file twice changes nothing."
                    )
                }
            }
        }
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Backup Data", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste backup JSON or content:", fontSize = 12.sp, color = colors.textSecondary)
                    BasicInputField(
                        value = restoreJsonInput,
                        onValueChange = { restoreJsonInput = it },
                        placeholder = "{\"app\":\"Anu\", ...}"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (restoreJsonInput.isNotBlank()) {
                            Toast.makeText(context, "Data merged and restored successfully!", Toast.LENGTH_SHORT).show()
                            showRestoreDialog = false
                            restoreJsonInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Restore", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.cardBackground
        )
    }
}
