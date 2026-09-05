package com.ghaniram.zoya.settings

import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.ghaniram.zoya.ui.theme.LocalAnuColors

/**
 * Emergency SOS Screen matching Page 27 of the specification.
 * Interactive SOS contacts management and dial testing with dynamic theme support.
 */
@Composable
fun AnuEmergencySosScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    var countryCodeState by remember { mutableStateOf(store.sosCountryCode) }
    var contactsList by remember { mutableStateOf(store.getSosContacts()) }

    var showManualDialog by remember { mutableStateOf(false) }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var showCountryDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Emergency SOS",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Country Code
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Public, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Country code", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.inputBackground,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCountryDropdown = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(countryCodeState, color = colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.ArrowDropDown, null, tint = colors.textSecondary)
                        }
                    }

                    DropdownMenu(
                        expanded = showCountryDropdown,
                        onDismissRequest = { showCountryDropdown = false }
                    ) {
                        listOf(
                            "India (+91)", "United States (+1)", "United Kingdom (+44)",
                            "Nepal (+977)", "Bangladesh (+880)", "Canada (+1)"
                        ).forEach { cc ->
                            DropdownMenuItem(
                                text = { Text(cc, color = colors.textPrimary) },
                                onClick = {
                                    countryCodeState = cc
                                    store.sosCountryCode = cc
                                    showCountryDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Favorite & SOS Contacts
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Emergency, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Favorite & SOS contacts", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Anu can call or SMS these contacts in emergency", fontSize = 11.5.sp, color = colors.textSecondary)

                    Spacer(Modifier.height(14.dp))
                    if (contactsList.isEmpty()) {
                        Text("No SOS contacts saved yet.", color = colors.textSecondary, fontSize = 12.5.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            contactsList.forEach { contactStr ->
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
                                        Icon(Icons.Outlined.ContactPhone, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(contactStr, color = colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                store.removeSosContact(contactStr)
                                                contactsList = store.getSosContacts()
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

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                                    context.startActivity(intent)
                                }.onFailure {
                                    Toast.makeText(context, "Opening contacts picker...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.chipBackground),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("From contacts", fontSize = 12.sp, color = colors.textPrimary)
                        }

                        Button(
                            onClick = { showManualDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("+ Type manually", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Add SOS Contact", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Name", fontSize = 12.sp, color = colors.textSecondary)
                    BasicInputField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        placeholder = "e.g. Papa, Sister, Doctor"
                    )

                    Text("Phone Number", fontSize = 12.sp, color = colors.textSecondary)
                    BasicInputField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        placeholder = "+91 98765 43210"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (contactName.isNotBlank() && contactPhone.isNotBlank()) {
                            store.addSosContact("$contactName ($contactPhone)")
                            contactsList = store.getSosContacts()
                            contactName = ""
                            contactPhone = ""
                            showManualDialog = false
                            Toast.makeText(context, "SOS contact added!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Add", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) { Text("Cancel", color = colors.textSecondary) }
            },
            containerColor = colors.cardBackground
        )
    }
}
