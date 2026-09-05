package com.ghaniram.zoya.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ghaniram.zoya.ui.theme.LocalAnuColors

data class PermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val systemPermission: String? = null,
    val isSpecialIntent: Boolean = false
)

/**
 * Permissions Screen matching Page 32 of the specification.
 * Live permission status checkers and seamless Android intent launchers with dynamic theme support.
 */
@Composable
fun AnuPermissionsSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    var refreshTrigger by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshTrigger++
    }

    val permissionsList = listOf(
        PermissionItem("assistant", "Default assistant", "Let Anu be your primary voice assistant", isSpecialIntent = true),
        PermissionItem("mic", "Microphone", "Voice commands and live speech conversations", Manifest.permission.RECORD_AUDIO),
        PermissionItem("camera", "Camera", "Visual perception, scanning, and multimodal AI", Manifest.permission.CAMERA),
        PermissionItem("calls", "Phone calls", "Make autonomous and requested phone calls", Manifest.permission.CALL_PHONE),
        PermissionItem("location", "Location", "Local weather, commute, places, and geo context", Manifest.permission.ACCESS_FINE_LOCATION),
        PermissionItem("contacts", "Contacts", "Name resolution for calls, messages, and SOS", Manifest.permission.READ_CONTACTS),
        PermissionItem("sms", "SMS", "Send emergency SOS and driving auto-replies", Manifest.permission.SEND_SMS),
        PermissionItem("phone_state", "Answer & manage calls", "Call caller announcements and driving reject", Manifest.permission.READ_PHONE_STATE),
        PermissionItem("bluetooth", "Bluetooth", "Headset detection and event announcements", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null),
        PermissionItem("notif", "App notifications", "Task reminders, status, and alerts", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null),
        PermissionItem("notif_listener", "Notification access", "WhatsApp auto-reply and event reading", isSpecialIntent = true),
        PermissionItem("accessibility", "Accessibility service", "Screen reading, autonomous actions, unlock gestures", isSpecialIntent = true),
        PermissionItem("battery", "Battery — no optimization", "Keep Anu awake for background triggers", isSpecialIntent = true),
        PermissionItem("overlay", "Display over other apps", "Floating orb and visual head-up display", isSpecialIntent = true)
    )

    fun isPermissionGranted(item: PermissionItem): Boolean {
        return when (item.id) {
            "overlay" -> Settings.canDrawOverlays(context)
            "battery" -> {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            }
            "accessibility" -> {
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                enabledServices.contains(context.packageName)
            }
            "notif_listener" -> {
                val listeners = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ) ?: ""
                listeners.contains(context.packageName)
            }
            "assistant" -> true
            else -> {
                if (item.systemPermission == null) true
                else ContextCompat.checkSelfPermission(context, item.systemPermission) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Permissions",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp)
        ) {
            item {
                Text(
                    text = "Anu needs these permissions to do everything for you. Allow only what you want.",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    lineHeight = 16.5.sp
                )
                Spacer(Modifier.height(8.dp))
            }

            items(permissionsList, key = { it.id }) { item ->
                val granted = isPermissionGranted(item)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(1.dp, if (granted) colors.cardBorder else colors.accentPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = item.description,
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        if (granted) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Check, null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Granted", color = Color(0xFF10B981), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    when (item.id) {
                                        "overlay" -> {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                        }
                                        "accessibility" -> {
                                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                        }
                                        "notif_listener" -> {
                                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                        }
                                        "battery" -> {
                                            val intent = Intent(
                                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                        }
                                        "assistant" -> {
                                            context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                                        }
                                        else -> {
                                            item.systemPermission?.let { permissionLauncher.launch(it) }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Grant", fontSize = 11.5.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
