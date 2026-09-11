package com.ghaniram.zoya.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerWithPresetsDialog(
    initialHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    initialMinute: Int = Calendar.getInstance().get(Calendar.MINUTE),
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }
    var presetKey by remember { mutableIntStateOf(0) }

    val timePickerState = key(presetKey) {
        rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = false
        )
    }

    fun applyPreset(hour: Int, minute: Int) {
        selectedHour = hour % 24
        selectedMinute = minute % 60
        presetKey++
    }

    fun addMinutes(minutesToAdd: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
            set(Calendar.MINUTE, timePickerState.minute)
            add(Calendar.MINUTE, minutesToAdd)
        }
        applyPreset(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Time",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Quick Presets Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = { addMinutes(15) },
                        label = { Text("+15m") }
                    )
                    SuggestionChip(
                        onClick = { addMinutes(30) },
                        label = { Text("+30m") }
                    )
                    SuggestionChip(
                        onClick = { addMinutes(60) },
                        label = { Text("+1h") }
                    )
                    SuggestionChip(
                        onClick = { applyPreset(9, 0) },
                        label = { Text("09:00 AM") }
                    )
                    SuggestionChip(
                        onClick = { applyPreset(14, 0) },
                        label = { Text("02:00 PM") }
                    )
                    SuggestionChip(
                        onClick = { applyPreset(18, 0) },
                        label = { Text("06:00 PM") }
                    )
                    SuggestionChip(
                        onClick = { applyPreset(21, 0) },
                        label = { Text("09:00 PM") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TimePicker(state = timePickerState)
            }
        }
    )
}