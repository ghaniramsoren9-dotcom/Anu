package com.ghaniram.zoya

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.ui.theme.AnuBorder
import com.ghaniram.zoya.ui.theme.AnuCardSurface
import com.ghaniram.zoya.ui.theme.AnuLavenderBg
import com.ghaniram.zoya.ui.theme.AnuPrimary
import com.ghaniram.zoya.ui.theme.AnuTextDark
import com.ghaniram.zoya.ui.theme.AnuTextMuted

/** Safe model-selected UI description. The model selects data/actions, never Compose code. */
data class DynamicUiAction(
    val id: String,
    val label: String,
    val type: String,
    val value: String = ""
)

data class DynamicUiSpec(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String = "",
    val body: String = "",
    val icon: String = "sparkles",
    val actions: List<DynamicUiAction> = emptyList()
)

@Composable
fun DynamicUiCard(
    spec: DynamicUiSpec,
    onAction: (DynamicUiAction) -> Unit
) {
    val icon = remember(spec.icon, spec.kind) { dynamicUiIcon(spec.icon, spec.kind) }
    val visibleAlpha by animateFloatAsState(1f, label = "dynamicUiAlpha")
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 5 })
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().alpha(visibleAlpha),
            shape = RoundedCornerShape(22.dp),
            color = AnuCardSurface,
            border = BorderStroke(1.dp, AnuBorder),
            tonalElevation = 2.dp,
            shadowElevation = 3.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(AnuLavenderBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = AnuPrimary, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(spec.title, color = AnuTextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (spec.subtitle.isNotBlank()) Text(spec.subtitle, color = AnuTextMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
                if (spec.body.isNotBlank()) {
                    Spacer(Modifier.size(12.dp))
                    Text(spec.body, color = AnuTextDark, fontSize = 13.5.sp, lineHeight = 20.sp)
                }
                if (spec.actions.isNotEmpty()) {
                    Spacer(Modifier.size(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        spec.actions.take(2).forEachIndexed { index, action ->
                            if (index == 0) {
                                Button(
                                    onClick = { onAction(action) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = AnuPrimary)
                                ) { Text(action.label, maxLines = 1) }
                            } else {
                                OutlinedButton(onClick = { onAction(action) }, modifier = Modifier.weight(1f)) {
                                    Text(action.label, maxLines = 1)
                                }
                            }
                        }
                    }
                    if (spec.actions.size > 2) {
                        Spacer(Modifier.size(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            spec.actions.drop(2).take(2).forEach { action ->
                                OutlinedButton(onClick = { onAction(action) }, modifier = Modifier.weight(1f)) {
                                    Text(action.label, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dynamicUiIcon(iconName: String, kind: String): ImageVector = when ((iconName.ifBlank { kind }).lowercase()) {
    "weather", "sun", "wb_sunny" -> Icons.Filled.WbSunny
    "task", "tasks" -> Icons.Filled.AssignmentTurnedIn
    "reminder", "event" -> Icons.Filled.Event
    "map", "navigation", "location" -> Icons.Filled.LocationOn
    "search" -> Icons.Filled.Search
    "check", "confirmation" -> Icons.Filled.CheckCircle
    "progress" -> Icons.Filled.TaskAlt
    "error" -> Icons.Filled.ErrorOutline
    else -> Icons.Filled.AutoAwesome
}
