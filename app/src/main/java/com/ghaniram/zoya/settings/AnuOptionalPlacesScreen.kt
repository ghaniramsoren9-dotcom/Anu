package com.ghaniram.zoya.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NearMe
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
 * Optional Places & Integrations Screen matching Page 24 of the specification.
 * Maps API key, geofencing triggers, and weather provider with dynamic theme support.
 */
@Composable
fun AnuOptionalPlacesScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    var mapsApiKey by remember { mutableStateOf(store.mapsApiKey) }
    var geofencingEnabled by remember { mutableStateOf(store.smartGeofencingEnabled) }
    var weatherProvider by remember { mutableStateOf(store.weatherProvider) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Optional Integrations",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Map, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Maps & Places", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Text("Powers local queries, traffic, and place recommendations", fontSize = 11.5.sp, color = colors.textSecondary)

                    Spacer(Modifier.height(12.dp))
                    Text("Google Maps API key (optional)", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicInputField(
                        value = mapsApiKey,
                        onValueChange = {
                            mapsApiKey = it
                            store.mapsApiKey = it
                        },
                        placeholder = "AIzaSy...",
                        isPassword = true
                    )

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            store.mapsApiKey = mapsApiKey.trim()
                            Toast.makeText(context, "Maps configuration saved!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Save", fontSize = 12.sp, color = Color.White)
                    }
                }
            }

            item {
                SettingsCardContainer {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NearMe, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Geofencing & Weather", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    SettingsToggleRow(
                        title = "Smart geofencing triggers",
                        subtitle = "Notify when reaching home, office, or saved destinations",
                        checked = geofencingEnabled,
                        onCheckedChange = {
                            geofencingEnabled = it
                            store.smartGeofencingEnabled = it
                        }
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("Weather provider", fontSize = 11.5.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicInputField(
                        value = weatherProvider,
                        onValueChange = {
                            weatherProvider = it
                            store.weatherProvider = it
                        },
                        placeholder = "Open-Meteo (Free)"
                    )
                }
            }
        }
    }
}
