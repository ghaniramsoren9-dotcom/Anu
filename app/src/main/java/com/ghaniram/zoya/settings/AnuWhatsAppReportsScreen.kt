package com.ghaniram.zoya.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
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

/**
 * WhatsApp Groups & Reports Screen matching Pages 22 & 23 of the specification.
 * Interactive group manager and template editor with dynamic theme support.
 */
@Composable
fun AnuWhatsAppReportsScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    var groupInput by remember { mutableStateOf("") }
    var groupsList by remember { mutableStateOf(store.getWhatsAppGroups()) }
    var reportFormats by remember { mutableStateOf(store.getReportFormats()) }

    var showNewFormatDialog by remember { mutableStateOf(false) }
    var newFormatTitle by remember { mutableStateOf("") }
    var newFormatTemplate by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "WhatsApp groups & reports",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Your WhatsApp Groups
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Group, null, tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Your WhatsApp groups", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Chats Anu must treat as a group", fontSize = 11.5.sp, color = colors.textSecondary)

                    Spacer(Modifier.height(10.dp))
                    SettingsTipBanner(
                        text = "Anu never posts in a group without reading the exact message to you first and getting a clear yes. That can't be switched off — a message in a work group can't be unsent.",
                        isWarning = true
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            BasicInputField(
                                value = groupInput,
                                onValueChange = { groupInput = it },
                                placeholder = "Group name in WhatsApp"
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (groupInput.isNotBlank()) {
                                    store.addWhatsAppGroup(groupInput.trim())
                                    groupsList = store.getWhatsAppGroups()
                                    groupInput = ""
                                    Toast.makeText(context, "Group registered!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("Add", fontSize = 12.sp, color = Color.White)
                        }
                    }

                    if (groupsList.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            groupsList.forEach { group ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.inputBackground,
                                    border = BorderStroke(1.dp, colors.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.Forum, null, tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(group, color = colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                store.removeWhatsAppGroup(group)
                                                groupsList = store.getWhatsAppGroups()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Outlined.Close, "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Report Formats
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FormatListNumbered, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Report formats", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text(
                        "${reportFormats.size}/20 saved — Save the message your work group expects, with {curly braces} where values change. Then just say 'us group me report daal do' — Anu asks for the values and formats it.",
                        fontSize = 11.5.sp,
                        color = colors.textSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { showNewFormatDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.chipBackground, contentColor = colors.textPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("+ New format", fontSize = 12.sp)
                    }

                    if (reportFormats.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            reportFormats.forEachIndexed { index, format ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.inputBackground,
                                    border = BorderStroke(1.dp, colors.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = format,
                                            color = colors.textPrimary,
                                            fontSize = 12.5.sp,
                                            lineHeight = 17.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                store.removeReportFormat(index)
                                                reportFormats = store.getReportFormats()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Outlined.Delete, "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFormatDialog) {
        AlertDialog(
            onDismissRequest = { showNewFormatDialog = false },
            title = { Text("New Report Format", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Format Name", fontSize = 12.sp, color = colors.textSecondary)
                    BasicInputField(
                        value = newFormatTitle,
                        onValueChange = { newFormatTitle = it },
                        placeholder = "e.g. Daily Standup Report"
                    )

                    Text("Template Text (use {tags} for variables)", fontSize = 12.sp, color = colors.textSecondary)
                    BasicInputField(
                        value = newFormatTemplate,
                        onValueChange = { newFormatTemplate = it },
                        placeholder = "Today's tasks: {tasks}\nCompleted: {completed}\nBlockers: {blockers}"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFormatTemplate.isNotBlank()) {
                            val combined = if (newFormatTitle.isNotBlank()) "[$newFormatTitle]\n$newFormatTemplate" else newFormatTemplate
                            store.addReportFormat(combined)
                            reportFormats = store.getReportFormats()
                            newFormatTitle = ""
                            newFormatTemplate = ""
                            showNewFormatDialog = false
                            Toast.makeText(context, "Report format saved!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFormatDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.cardBackground
        )
    }
}
