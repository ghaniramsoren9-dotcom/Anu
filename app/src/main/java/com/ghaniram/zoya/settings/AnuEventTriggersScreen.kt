package com.ghaniram.zoya.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.AnuSettingsStore
import com.ghaniram.zoya.ui.theme.LocalAnuColors

/**
 * Event Triggers Screen matching Pages 26 & 27 of the specification.
 * System event triggers and voice announcements configuration with dynamic theme support.
 */
@Composable
fun AnuEventTriggersScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val colors = LocalAnuColors.current
    var masterOn by remember { mutableStateOf(store.eventAnnouncementsMaster) }
    var speakWhileAsleep by remember { mutableStateOf(store.speakWhileAsleep) }
    var speakOnSilent by remember { mutableStateOf(store.speakOnSilentToo) }

    // Power & battery
    var trigChargerPlugged by remember { mutableStateOf(store.triggerChargerPlugged) }
    var trigChargerUnplugged by remember { mutableStateOf(store.triggerChargerUnplugged) }
    var trigBatteryFull by remember { mutableStateOf(store.triggerBatteryFull) }
    var trigBatteryLow by remember { mutableStateOf(store.triggerBatteryLow) }
    var trigBatteryCritical by remember { mutableStateOf(store.triggerBatteryCritical) }
    var trigBatterySaverOn by remember { mutableStateOf(store.triggerBatterySaverOn) }
    var trigBatterySaverOff by remember { mutableStateOf(store.triggerBatterySaverOff) }

    // Headphones & Bluetooth
    var trigHeadphonesPlugged by remember { mutableStateOf(store.triggerHeadphonesPlugged) }
    var trigHeadphonesUnplugged by remember { mutableStateOf(store.triggerHeadphonesUnplugged) }
    var trigBluetoothConn by remember { mutableStateOf(store.triggerBluetoothConnected) }
    var trigBluetoothDisconn by remember { mutableStateOf(store.triggerBluetoothDisconnected) }

    // Network
    var trigWifiConn by remember { mutableStateOf(store.triggerWifiConnected) }
    var trigWifiLost by remember { mutableStateOf(store.triggerWifiLost) }
    var trigAirplaneOn by remember { mutableStateOf(store.triggerAirplaneModeOn) }
    var trigAirplaneOff by remember { mutableStateOf(store.triggerAirplaneModeOff) }

    // System
    var trigSilent by remember { mutableStateOf(store.triggerPhoneOnSilent) }
    var trigRingerOn by remember { mutableStateOf(store.triggerRingerBackOn) }
    var trigAppInstall by remember { mutableStateOf(store.triggerAppInstalled) }
    var trigAppUninstall by remember { mutableStateOf(store.triggerAppUninstalled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Event triggers",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Master Controls
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsActive, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Announcements", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Anu speaks up when the phone does something", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(12.dp))

                    SettingsToggleRow(
                        title = "Event announcements",
                        subtitle = "Master switch — off means she never brings any of this up",
                        checked = masterOn,
                        onCheckedChange = {
                            masterOn = it
                            store.eventAnnouncementsMaster = it
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Speak while she's asleep",
                        checked = speakWhileAsleep,
                        onCheckedChange = {
                            speakWhileAsleep = it
                            store.speakWhileAsleep = it
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Speak on silent too",
                        checked = speakOnSilent,
                        onCheckedChange = {
                            speakOnSilent = it
                            store.speakOnSilentToo = it
                        }
                    )
                }
            }

            // Power & Battery
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.BatteryChargingFull, null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Power & battery", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    SettingsToggleRow(
                        title = "Charger plugged in",
                        checked = trigChargerPlugged,
                        onCheckedChange = {
                            trigChargerPlugged = it
                            store.triggerChargerPlugged = it
                        }
                    )
                    SettingsToggleRow(
                        title = "Charger unplugged",
                        checked = trigChargerUnplugged,
                        onCheckedChange = {
                            trigChargerUnplugged = it
                            store.triggerChargerUnplugged = it
                        }
                    )
                    SettingsToggleRow(
                        title = "Battery full",
                        checked = trigBatteryFull,
                        onCheckedChange = {
                            trigBatteryFull = it
                            store.triggerBatteryFull = it
                        }
                    )
                    SettingsToggleRow(
                        title = "Battery low (20%)",
                        checked = trigBatteryLow,
                        onCheckedChange = {
                            trigBatteryLow = it
                            store.triggerBatteryLow = it
                        }
                    )
                    SettingsToggleRow(
                        title = "Battery critical (10%)",
                        checked = trigBatteryCritical,
                        onCheckedChange = {
                            trigBatteryCritical = it
                            store.triggerBatteryCritical = it
                        }
                    )
                    SettingsToggleRow(
                        title = "Battery saver on",
                        checked = trigBatterySaverOn,
                        onCheckedChange = {
                            trigBatterySaverOn = it
                            store.triggerBatterySaverOn = it
                        }
                    )
                    SettingsToggleRow(
                        title = "Battery saver off",
                        checked = trigBatterySaverOff,
                        onCheckedChange = {
                            trigBatterySaverOff = it
                            store.triggerBatterySaverOff = it
                        }
                    )
                }
            }

            // Headphones & Bluetooth
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Headphones, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Headphones & Bluetooth", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    SettingsToggleRow(
                        title = "Headphones plugged in",
                        checked = trigHeadphonesPlugged,
                        onCheckedChange = {
                            trigHeadphonesPlugged = it
                            store.triggerHeadphonesPlugged = it
                        }
                    )
                    SettingsToggleRow(
                        title = "Headphones unplugged",
                        checked = trigHeadphonesUnplugged,
                        onCheckedChange = {
                            trigHeadphonesUnplugged = it
                            store.triggerHeadphonesUnplugged = it
                        }
                    )
                    SettingsToggleRow(
                        title = "Bluetooth device connected",
                        checked = trigBluetoothConn,
                        onCheckedChange = {
                            trigBluetoothConn = it
                            store.triggerBluetoothConnected = it
                        }
                    )
                    SettingsToggleRow(
                        title = "Bluetooth device disconnected",
                        checked = trigBluetoothDisconn,
                        onCheckedChange = {
                            trigBluetoothDisconn = it
                            store.triggerBluetoothDisconnected = it
                        }
                    )
                }
            }

            // Network
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Wifi, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Network", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    SettingsToggleRow(
                        title = "Wi-Fi connected",
                        checked = trigWifiConn,
                        onCheckedChange = {
                            trigWifiConn = it
                            store.triggerWifiConnected = it
                        }
                    )
                    SettingsToggleRow(
                        title = "Wi-Fi lost",
                        checked = trigWifiLost,
                        onCheckedChange = {
                            trigWifiLost = it
                            store.triggerWifiLost = it
                        }
                    )
                    SettingsToggleRow(
                        title = "Airplane mode on",
                        checked = trigAirplaneOn,
                        onCheckedChange = {
                            trigAirplaneOn = it
                            store.triggerAirplaneModeOn = it
                        }
                    )
                    SettingsToggleRow(
                        title = "Airplane mode off",
                        checked = trigAirplaneOff,
                        onCheckedChange = {
                            trigAirplaneOff = it
                            store.triggerAirplaneModeOff = it
                        }
                    )
                }
            }

            // System
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Smartphone, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("System", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    SettingsToggleRow(
                        title = "Phone put on silent",
                        checked = trigSilent,
                        onCheckedChange = {
                            trigSilent = it
                            store.triggerPhoneOnSilent = it
                        }
                    )
                    SettingsToggleRow(
                        title = "Ringer back on",
                        checked = trigRingerOn,
                        onCheckedChange = {
                            trigRingerOn = it
                            store.triggerRingerBackOn = it
                        }
                    )
                    SettingsToggleRow(
                        title = "App installed",
                        checked = trigAppInstall,
                        onCheckedChange = {
                            trigAppInstall = it
                            store.triggerAppInstalled = it
                        }
                    )
                    SettingsToggleRow(
                        title = "App uninstalled",
                        checked = trigAppUninstall,
                        onCheckedChange = {
                            trigAppUninstall = it
                            store.triggerAppUninstalled = it
                        }
                    )
                }
            }
        }
    }
}
