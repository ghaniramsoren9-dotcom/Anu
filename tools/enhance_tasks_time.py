from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
main_path = ROOT / "app/src/main/java/com/ghaniram/zoya/MainActivity.kt"

if not main_path.exists():
    print("MainActivity.kt not found, skipping enhance_tasks_time.py")
    raise SystemExit(0)

text = main_path.read_text(encoding="utf-8")

if "AnuEnhancedAddTaskDialog" in text:
    print("AnuEnhancedAddTaskDialog already installed.")
    raise SystemExit(0)

# Add imports
imports = [
    "import android.app.TimePickerDialog",
    "import java.util.Calendar",
    "import java.util.Locale",
    "import androidx.compose.material.icons.filled.Schedule",
    "import androidx.compose.material3.AssistChip"
]
for imp in imports:
    if imp not in text:
        text = imp + "\n" + text

# Locate the dialog block containing addTask
idx = text.find("viewModel.addTask(")
if idx == -1:
    idx = text.find("addTask(")

if idx != -1:
    if_idx = text.rfind("if (", 0, idx)
    if if_idx != -1:
        brace_open = text.find("{", if_idx)
        if brace_open != -1 and brace_open < idx:
            count = 1
            i = brace_open + 1
            while i < len(text) and count > 0:
                if text[i] == '{': count += 1
                elif text[i] == '}': count -= 1
                i += 1
            if count == 0:
                block = text[if_idx:i]
                var_m = re.search(r'if\s*\(\s*([A-Za-z0-9_]+)\s*\)', block)
                if var_m:
                    dialog_var = var_m.group(1)
                    replacement_block = f"""if ({dialog_var}) {{
        AnuEnhancedAddTaskDialog(
            onDismiss = {{ {dialog_var} = false }},
            onAdd = {{ title, time -> viewModel.addTask(title, time) }}
        )
    }}"""
                    text = text[:if_idx] + replacement_block + text[i:]
                    print(f"Replaced dialog block controlled by {dialog_var}")

# Append the enhanced dialog composable
dialog_composable = """

@Composable
private fun AnuEnhancedAddTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var title by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val now = Calendar.getInstance().apply { add(Calendar.MINUTE, 30) }
    val initialTime = String.format(Locale.US, "%d:%02d %s",
        if (now.get(Calendar.HOUR) == 0) 12 else now.get(Calendar.HOUR),
        now.get(Calendar.MINUTE),
        if (now.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    )
    var time by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialTime) }

    val timePickerDialog = androidx.compose.runtime.remember {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val ampm = if (hourOfDay >= 12) "PM" else "AM"
                val h12 = when {
                    hourOfDay == 0 -> 12
                    hourOfDay > 12 -> hourOfDay - 12
                    else -> hourOfDay
                }
                time = String.format(Locale.US, "%d:%02d %s", h12, minute, ampm)
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            false
        )
    }

    fun setOffsetMinutes(minutes: Int) {
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, minutes) }
        val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        val h = if (cal.get(Calendar.HOUR) == 0) 12 else cal.get(Calendar.HOUR)
        time = String.format(Locale.US, "%d:%02d %s", h, cal.get(Calendar.MINUTE), ampm)
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Add Task Reminder", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { androidx.compose.material3.Text("Task Title") },
                    placeholder = { androidx.compose.material3.Text("e.g. Study, Drink water") },
                    singleLine = true,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )

                androidx.compose.material3.OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { androidx.compose.material3.Text("Reminder Time") },
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { timePickerDialog.show() }) {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Filled.Schedule,
                                contentDescription = "Pick Time",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )

                androidx.compose.material3.Text("Quick Presets:", fontSize = 11.sp, color = AnuMuted)

                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.material3.AssistChip(
                        onClick = { setOffsetMinutes(15) },
                        label = { androidx.compose.material3.Text("+15m", fontSize = 10.sp) }
                    )
                    androidx.compose.material3.AssistChip(
                        onClick = { setOffsetMinutes(30) },
                        label = { androidx.compose.material3.Text("+30m", fontSize = 10.sp) }
                    )
                    androidx.compose.material3.AssistChip(
                        onClick = { setOffsetMinutes(60) },
                        label = { androidx.compose.material3.Text("+1h", fontSize = 10.sp) }
                    )
                    androidx.compose.material3.AssistChip(
                        onClick = { timePickerDialog.show() },
                        label = { androidx.compose.material3.Text("🕒 Clock", fontSize = 10.sp) }
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    if (title.isNotBlank() && time.isNotBlank()) {
                        onAdd(title.trim(), time.trim())
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank() && time.isNotBlank()
            ) {
                androidx.compose.material3.Text("Add Reminder")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("Cancel")
            }
        }
    )
}
"""
text += dialog_composable
main_path.write_text(text, encoding="utf-8")
print("MainActivity.kt successfully enhanced with time picker and quick presets.")
