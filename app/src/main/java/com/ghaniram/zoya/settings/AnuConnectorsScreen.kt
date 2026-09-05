package com.ghaniram.zoya.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ghaniram.zoya.ui.theme.LocalAnuColors

data class ConnectorItem(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val iconLetter: String,
    val badgeColor: Color
)

/**
 * Connectors Screen matching Page 19 of the specification.
 * Interactive account connector state management with dynamic theme support.
 */
@Composable
fun AnuConnectorsScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var activeConnectors by remember { mutableStateOf(store.connectedAccounts) }

    var selectedForConnect by remember { mutableStateOf<ConnectorItem?>(null) }
    var tokenInput by remember { mutableStateOf("") }

    val allConnectors = listOf(
        ConnectorItem("gdrive", "Google Drive", "Files you create or pick", "Files", "G", Color(0xFF4285F4)),
        ConnectorItem("github", "GitHub", "Repos, issues and files", "Code", "G", Color(0xFF24292E)),
        ConnectorItem("notion", "Notion", "Pages, notes and databases", "Notes & tasks", "N", Color(0xFF000000)),
        ConnectorItem("telegram", "Telegram", "Send yourself messages and files", "Messages", "T", Color(0xFF0088CC)),
        ConnectorItem("todoist", "Todoist", "Your real task list", "Notes & tasks", "T", Color(0xFFE44332)),
        ConnectorItem("gitlab", "GitLab", "Projects, issues and files", "Code", "G", Color(0xFFFC6D26)),
        ConnectorItem("linear", "Linear", "Issues and cycles", "Notes & tasks", "L", Color(0xFF5E6AD2))
    )

    val filteredList = allConnectors.filter { item ->
        val matchesCategory = (selectedCategory == "All") || item.category.equals(selectedCategory, ignoreCase = true)
        val matchesQuery = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true) || item.description.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Connectors",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp)
        ) {
            // Search Bar
            item {
                BasicInputField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search connectors"
                )
            }

            // Category Filter Pills
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(listOf("All", "Files", "Code", "Notes & tasks", "Messages")) { cat ->
                        ChoiceChipPill(
                            label = cat,
                            isSelected = selectedCategory == cat,
                            onClick = { selectedCategory = cat }
                        )
                    }
                }
            }

            // Connectors List
            items(filteredList, key = { it.id }) { item ->
                val isConnected = activeConnectors.contains(item.id)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(1.dp, if (isConnected) colors.accentPrimary.copy(alpha = 0.5f) else colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(item.badgeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.iconLetter,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = item.description,
                                fontSize = 11.5.sp,
                                color = colors.textSecondary
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (isConnected) {
                                    store.toggleConnector(item.id)
                                    activeConnectors = store.connectedAccounts
                                    Toast.makeText(context, "${item.name} disconnected", Toast.LENGTH_SHORT).show()
                                } else {
                                    selectedForConnect = item
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnected) colors.chipBackground else colors.accentPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = if (isConnected) "Disconnect" else "Connect",
                                fontSize = 11.5.sp,
                                color = if (isConnected) colors.textPrimary else Color.White
                            )
                        }
                    }
                }
            }

            // Security Note
            item {
                Spacer(Modifier.height(8.dp))
                SettingsTipBanner(
                    text = "Connector credentials stay on this phone, encrypted. Anu never sends them anywhere, and disconnecting deletes them."
                )
            }
        }
    }

    if (selectedForConnect != null) {
        AlertDialog(
            onDismissRequest = { selectedForConnect = null },
            title = { Text("Connect ${selectedForConnect?.name}", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter API token or Personal Access Token:", fontSize = 12.sp, color = colors.textSecondary)
                    BasicInputField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        placeholder = "e.g. ghp_...",
                        isPassword = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedForConnect?.let { item ->
                            store.toggleConnector(item.id)
                            activeConnectors = store.connectedAccounts
                            Toast.makeText(context, "${item.name} connected successfully!", Toast.LENGTH_SHORT).show()
                        }
                        tokenInput = ""
                        selectedForConnect = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Save & Connect", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedForConnect = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.cardBackground
        )
    }
}
