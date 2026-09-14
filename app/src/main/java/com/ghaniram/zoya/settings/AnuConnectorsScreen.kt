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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
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
    var usernameInput by remember { mutableStateOf("") }
    var defaultRepoInput by remember { mutableStateOf("") }

    val allConnectors = listOf(
        ConnectorItem("gdrive", "Google Drive", "Files you create or pick", "Files", "G", Color(0xFF4285F4)),
        ConnectorItem("github", "GitHub", "Repos, issues, commits and files", "Code", "G", Color(0xFF24292E)),
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
                val savedToken = store.getConnectorToken(item.id)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(1.dp, if (isConnected) colors.accentPrimary.copy(alpha = 0.6f) else colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(item.badgeColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.iconLetter,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 17.sp
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary
                                    )
                                    if (isConnected) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = "Connected",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            text = "Active",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                                Text(
                                    text = item.description,
                                    fontSize = 11.5.sp,
                                    color = colors.textSecondary
                                )
                                if (isConnected && item.id == "github" && store.githubUsername.isNotBlank()) {
                                    Text(
                                        text = "@${store.githubUsername}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.accentPrimary
                                    )
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            if (isConnected) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedForConnect = item
                                            tokenInput = savedToken
                                            usernameInput = if (item.id == "github") store.githubUsername else ""
                                            defaultRepoInput = if (item.id == "github") store.githubDefaultRepo else ""
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Config", fontSize = 11.sp, color = colors.textPrimary)
                                    }
                                    Button(
                                        onClick = {
                                            store.removeConnector(item.id)
                                            if (item.id == "github") {
                                                store.githubUsername = ""
                                                store.githubDefaultRepo = ""
                                            }
                                            activeConnectors = store.connectedAccounts
                                            Toast.makeText(context, "${item.name} disconnected & credentials removed", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.chipBackground),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Disconnect", fontSize = 11.sp, color = Color(0xFFEF4444))
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        selectedForConnect = item
                                        tokenInput = savedToken
                                        usernameInput = if (item.id == "github") store.githubUsername else ""
                                        defaultRepoInput = if (item.id == "github") store.githubDefaultRepo else ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Connect", fontSize = 11.5.sp, color = Color.White)
                                }
                            }
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
        val item = selectedForConnect!!
        val isGitHub = item.id == "github"

        AlertDialog(
            onDismissRequest = { selectedForConnect = null },
            title = {
                Column {
                    Text(
                        text = if (isGitHub) "Connect GitHub Account" else "Connect ${item.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = if (isGitHub) "Allows Anu to browse repos, create code files, and push commits"
                               else "Secure direct integration with ${item.name}",
                        fontSize = 11.5.sp,
                        color = colors.textSecondary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isGitHub) {
                        Text("Personal Access Token (PAT):", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                        BasicInputField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            placeholder = "ghp_... or github_pat_...",
                            isPassword = true
                        )

                        Text("GitHub Username:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                        BasicInputField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            placeholder = "e.g. your-github-handle"
                        )

                        Text("Default Repository (optional):", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                        BasicInputField(
                            value = defaultRepoInput,
                            onValueChange = { defaultRepoInput = it },
                            placeholder = "e.g. username/repo"
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, null, tint = colors.accentPrimary, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Requires 'repo' scope for repositories and code commits.",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                    } else {
                        Text("API Token / Access Key:", fontSize = 12.sp, color = colors.textSecondary)
                        BasicInputField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            placeholder = "Paste your ${item.name} token/key",
                            isPassword = true
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val token = tokenInput.trim()
                        if (token.isBlank()) {
                            Toast.makeText(context, "Please enter a valid token", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        store.setConnectorToken(item.id, token)
                        if (isGitHub) {
                            if (usernameInput.isNotBlank()) store.githubUsername = usernameInput.trim()
                            if (defaultRepoInput.isNotBlank()) store.githubDefaultRepo = defaultRepoInput.trim()
                        }
                        activeConnectors = store.connectedAccounts
                        selectedForConnect = null
                        tokenInput = ""
                        usernameInput = ""
                        defaultRepoInput = ""
                        Toast.makeText(context, "${item.name} connected successfully!", Toast.LENGTH_SHORT).show()
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
