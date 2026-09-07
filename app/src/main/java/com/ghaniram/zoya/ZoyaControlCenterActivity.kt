package com.ghaniram.zoya

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ghaniram.zoya.ui.theme.*

private val SettingsBg = AnuBackground
private val SettingsCard = AnuCardSurface
private val SettingsMuted = AnuTextMuted
private val SettingsText = AnuTextDark
private val SettingsBorder = AnuBorder

class ZoyaControlCenterActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshToken++ }
    private var refreshToken by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            ZoyaTheme {
                AnuSettingsScreen(
                    refreshToken = refreshToken,
                    onDiagnostics = { startActivity(Intent(this, CapabilityDiagnosticsActivity::class.java)) },
                    onAccessibility = { ZoyaCapabilityManager.openAccessibility(this) },
                    onNotifications = { ZoyaCapabilityManager.openNotificationAccess(this) },
                    onOverlay = { ZoyaCapabilityManager.openOverlay(this) },
                    onWriteSettings = { ZoyaCapabilityManager.openWriteSettings(this) },
                    onAlarms = { ZoyaCapabilityManager.openExactAlarms(this) },
                    onFiles = { ZoyaCapabilityManager.openAllFiles(this) },
                    onDnd = { ZoyaCapabilityManager.openNotificationPolicy(this) },
                    onDeviceAdmin = { ZoyaCapabilityManager.openDeviceAdmin(this) },
                    onLocation = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    onContacts = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS)) },
                    onBluetooth = { permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT)) }
                )
            }
        }
    }

    override fun onResume() { super.onResume(); refreshToken++ }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnuSettingsScreen(
    refreshToken: Int,
    onDiagnostics: () -> Unit,
    onAccessibility: () -> Unit,
    onNotifications: () -> Unit,
    onOverlay: () -> Unit,
    onWriteSettings: () -> Unit,
    onAlarms: () -> Unit,
    onFiles: () -> Unit,
    onDnd: () -> Unit,
    onDeviceAdmin: () -> Unit,
    onLocation: () -> Unit,
    onContacts: () -> Unit,
    onBluetooth: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var appearanceExpanded by remember { mutableStateOf(false) }
    var privacyExpanded by remember { mutableStateOf(false) }
    var controlExpanded by remember { mutableStateOf(false) }
    var permissionsExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(refreshToken) { CapabilityRegistry.snapshot(context) }

    Scaffold(containerColor = SettingsBg, topBar = {
        SmallTopAppBar(title = { Column {
            Text("Settings & Controls", color = SettingsText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text("Configure Anu safely and comfortably", color = SettingsMuted, fontSize = 11.sp)
        }}, colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = SettingsBg))
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AnuLavenderBg), border = androidx.compose.foundation.BorderStroke(1.dp, SettingsBorder), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(15.dp)) {
                        Text("Capability health", color = SettingsText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("See which Anu capabilities are actually ready on this phone — not just switched on in Settings.", color = SettingsMuted, fontSize = 11.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Open Capability Diagnostics", fontSize = 12.sp) }
                    }
                }
            }
            item { SectionLabel("PERSONALIZATION") }
            item { SettingsCard(Icons.Default.Brightness6, "Appearance", "Theme, motion and visual comfort", appearanceExpanded) { appearanceExpanded = !appearanceExpanded }; AnimatedVisibility(appearanceExpanded) { ExpandPanel { SettingHint("Theme", "New Anu violet & light clean system applied."); Divider(color = SettingsBorder); SettingHint("Motion", "Animations are kept short and responsive to avoid sluggish transitions.") } } }
            item { SettingsCard(Icons.Default.Tune, "Voice & Conversation", "Voice, language and conversation behaviour", false) { } }
            item { SectionLabel("PRIVACY & SECURITY") }
            item { SettingsCard(Icons.Default.Lock, "Privacy", "Memory and data controls", privacyExpanded) { privacyExpanded = !privacyExpanded }; AnimatedVisibility(privacyExpanded) { ExpandPanel { SettingHint("Memory", "Only information you explicitly approve should be retained."); Divider(color = SettingsBorder); SettingHint("Permissions", "Anu only requests access needed for an enabled feature.") } } }
            item { SectionLabel("PHONE CONTROL") }
            item { SettingsCard(Icons.Default.Security, "Accessibility & Device Control", "Phone actions and automation access", controlExpanded) { controlExpanded = !controlExpanded }; AnimatedVisibility(controlExpanded) { ExpandPanel { ActionRow(Icons.Default.AccessibilityNew, "Accessibility Service", capability(ZoyaCapabilityManager.hasAccessibility(context)), onAccessibility); ActionRow(Icons.Default.Visibility, "Display over other apps", capability(ZoyaCapabilityManager.hasOverlay(context)), onOverlay); ActionRow(Icons.Default.Settings, "Modify system settings", capability(ZoyaCapabilityManager.hasWriteSettings(context)), onWriteSettings); ActionRow(Icons.Default.Alarm, "Exact alarms & reminders", capability(ZoyaCapabilityManager.hasExactAlarms(context)), onAlarms); ActionRow(Icons.Default.FolderOpen, "All files access", capability(ZoyaCapabilityManager.hasAllFiles(context)), onFiles); ActionRow(Icons.Default.DoNotDisturbOn, "Do Not Disturb / Modes", capability(ZoyaCapabilityManager.hasNotificationPolicy(context)), onDnd); ActionRow(Icons.Default.Security, "Device Admin", capability(ZoyaCapabilityManager.hasDeviceAdmin(context)), onDeviceAdmin) } } }
            item { SectionLabel("NOTIFICATIONS & COMMUNICATION") }
            item { SettingsCard(Icons.Default.NotificationsActive, "Notifications", "Notification access and assistant alerts", permissionsExpanded) { permissionsExpanded = !permissionsExpanded }; AnimatedVisibility(permissionsExpanded) { ExpandPanel { ActionRow(Icons.Default.Notifications, "Notification access", capability(ZoyaCapabilityManager.hasNotificationAccess(context)), onNotifications); ActionRow(Icons.Default.Bluetooth, "Bluetooth event access", runtimePermission(context, Manifest.permission.BLUETOOTH_CONNECT), onBluetooth); ActionRow(Icons.Default.Place, "Location", runtimePermission(context, Manifest.permission.ACCESS_FINE_LOCATION), onLocation); ActionRow(Icons.Default.Contacts, "Contacts", runtimePermission(context, Manifest.permission.READ_CONTACTS), onContacts); ActionRow(Icons.Default.Call, "Phone calls", runtimePermission(context, Manifest.permission.CALL_PHONE), onContacts); ActionRow(Icons.Default.Sms, "SMS", runtimePermission(context, Manifest.permission.SEND_SMS), onContacts) } } }
            item { SectionLabel("ABOUT") }
            item { Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SettingsCard), border = androidx.compose.foundation.BorderStroke(1.dp, SettingsBorder), modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = AnuPrimary, modifier = Modifier.size(22.dp)); Spacer(Modifier.size(12.dp)); Column(Modifier.weight(1f)) { Text("Anu", color = SettingsText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text("ANU Design System • Violet & Light", color = SettingsMuted, fontSize = 10.sp) } } } }
        }
    }
}

private fun capability(value: Boolean) = if (value) "Enabled" else "Needs permission"
private fun runtimePermission(context: android.content.Context, permission: String): String = if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) "Enabled" else "Needs permission"
@Composable private fun SectionLabel(text: String) { Text(text, color = AnuPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
@Composable private fun SettingsCard(icon: ImageVector, title: String, subtitle: String, expanded: Boolean, onClick: () -> Unit) { Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SettingsCard), border = androidx.compose.foundation.BorderStroke(1.dp, SettingsBorder), modifier = Modifier.fillMaxWidth().animateContentSize()) { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(12.dp), color = AnuLavenderBg, modifier = Modifier.size(42.dp)) { Icon(icon, null, tint = AnuPrimary, modifier = Modifier.padding(10.dp)) }; Spacer(Modifier.size(12.dp)); Column(Modifier.weight(1f)) { Text(title, color = SettingsText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, color = SettingsMuted, fontSize = 10.sp) }; Icon(Icons.Default.ChevronRight, null, tint = SettingsMuted, modifier = Modifier.size(20.dp)) } } }
@Composable private fun ExpandPanel(content: @Composable ColumnScope.() -> Unit) { Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AnuLavenderBg), border = androidx.compose.foundation.BorderStroke(1.dp, SettingsBorder), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), content = content) } }
@Composable private fun SettingHint(title: String, description: String) { Column(Modifier.padding(vertical = 10.dp)) { Text(title, color = SettingsText, fontSize = 12.sp, fontWeight = FontWeight.Medium); Text(description, color = SettingsMuted, fontSize = 10.sp) } }
@Composable private fun ActionRow(icon: ImageVector, title: String, status: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = SettingsMuted, modifier = Modifier.size(20.dp)); Spacer(Modifier.size(10.dp)); Column(Modifier.weight(1f)) { Text(title, color = SettingsText, fontSize = 12.sp); Text(status, color = SettingsMuted, fontSize = 9.sp) }; Button(onClick = onClick, modifier = Modifier.height(34.dp), shape = RoundedCornerShape(17.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AnuPrimary)) { Text(if (status == "Enabled") "Manage" else "Enable", fontSize = 9.sp) } } }
