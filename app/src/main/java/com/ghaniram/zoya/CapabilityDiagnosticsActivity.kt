package com.ghaniram.zoya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.ui.theme.ZoyaTheme

class CapabilityDiagnosticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent { ZoyaTheme { CapabilityDiagnosticsScreen(onBack = { finish() }) } }
    }
}

@Composable
private fun CapabilityDiagnosticsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }
    val statuses = remember(refreshKey) { CapabilityRegistry.snapshot(context) }
    val ordered = statuses.values.toList()
    val healthy = ordered.count { it.ready }
    val partial = ordered.count { it.state == CapabilityRegistry.State.PARTIAL }
    val unavailable = ordered.size - healthy

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Capability Diagnostics", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Live runtime health", fontSize = 11.sp) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { refreshKey++ }) { Icon(Icons.Default.Refresh, "Refresh") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Runtime readiness", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("$healthy ready  •  $partial partial  •  $unavailable unavailable", fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("This screen reports the actual prerequisites Anu can verify on this device. A setting being ON does not automatically mean its executor is ready.", fontSize = 11.sp)
                    }
                }
            }
            items(ordered, key = { it.id }) { status -> CapabilityStatusCard(status) }
            item {
                Button(onClick = { refreshKey++ }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text("Refresh diagnostics")
                }
            }
        }
    }
}

@Composable
private fun CapabilityStatusCard(status: CapabilityRegistry.Status) {
    val stateText = when (status.state) {
        CapabilityRegistry.State.TRUE -> "TRUE"
        CapabilityRegistry.State.PARTIAL -> "PARTIAL"
        CapabilityRegistry.State.UI_ONLY -> "UI ONLY"
        CapabilityRegistry.State.DISABLED -> "DISABLED"
    }
    val stateColor = when (status.state) {
        CapabilityRegistry.State.TRUE -> Color(0xFF16A34A)
        CapabilityRegistry.State.PARTIAL -> Color(0xFFF59E0B)
        CapabilityRegistry.State.UI_ONLY -> Color(0xFF7C3AED)
        CapabilityRegistry.State.DISABLED -> Color(0xFFDC2626)
    }
    Card {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(status.reason.substringBefore(": "), fontWeight = FontWeight.SemiBold)
                Text(status.reason.substringAfter(": ", status.reason), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stateText, color = stateColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}
