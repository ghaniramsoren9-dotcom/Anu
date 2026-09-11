from pathlib import Path
import re

path = Path("app/src/main/java/com/ghaniram/zoya/MainActivity.kt")
s = path.read_text(encoding="utf-8")
original = s

s = s.replace(
    "import androidx.compose.animation.core.tween\n",
    "import androidx.compose.animation.core.tween\n"
    "import androidx.compose.animation.AnimatedVisibility\n"
    "import androidx.compose.animation.fadeIn\n"
    "import androidx.compose.animation.fadeOut\n"
    "import androidx.compose.animation.slideInHorizontally\n"
    "import androidx.compose.animation.slideOutHorizontally\n"
    "import androidx.compose.animation.core.animateDpAsState\n"
)

old = 'if (drawerOpen) AnuDrawer(selectedTab, { selectedTab = it; drawerOpen = false }, { drawerOpen = false; onControlCenter() }) { drawerOpen = false }'
new = """AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(tween(180)) + slideInHorizontally(tween(220), initialOffsetX = { -it / 2 }),
            exit = fadeOut(tween(140)) + slideOutHorizontally(tween(180), targetOffsetX = { -it / 2 })
        ) {
            AnuDrawer(
                selectedTab,
                { selectedTab = it; drawerOpen = false },
                { drawerOpen = false; onControlCenter() }
            ) { drawerOpen = false }
        }"""
if old in s:
    s = s.replace(old, new)

s = s.replace(
    'Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 14.dp),',
    'Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 12.dp),'
)

s = s.replace(
    'Box(Modifier.fillMaxWidth().height(182.dp),contentAlignment=Alignment.Center)',
    'Box(Modifier.fillMaxWidth().height(170.dp),contentAlignment=Alignment.Center)'
)

old_nav = """@Composable private fun RowScope.AnuNavItem(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,selected:Boolean,onClick:()->Unit){Column(Modifier.weight(1f).fillMaxHeight().clickable(onClick=onClick).padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(icon,label,tint=if(selected)MaterialTheme.colorScheme.primary else AnuMuted,modifier=Modifier.size(19.dp));Spacer(Modifier.height(3.dp));Text(label,color=if(selected)MaterialTheme.colorScheme.primary else AnuMuted,fontSize=8.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium)}}"""
new_nav = """@Composable private fun RowScope.AnuNavItem(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,selected:Boolean,onClick:()->Unit){
    val scale by animateFloatAsState(if(selected) 1.04f else 1f, animationSpec=tween(180), label="navScale")
    Column(
        Modifier.weight(1f).fillMaxHeight().clickable(onClick=onClick).padding(horizontal=5.dp, vertical=6.dp),
        horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.Center
    ){
        Surface(
            shape=RoundedCornerShape(18.dp),
            color=if(selected) MaterialTheme.colorScheme.primary.copy(alpha=.13f) else Color.Transparent
        ){
            Row(
                Modifier.padding(horizontal=12.dp, vertical=5.dp),
                verticalAlignment=Alignment.CenterVertically
            ){
                Icon(icon,label,tint=if(selected)MaterialTheme.colorScheme.primary else AnuMuted,modifier=Modifier.size(19.dp).scale(scale))
                Spacer(Modifier.width(5.dp))
                Text(label,color=if(selected)MaterialTheme.colorScheme.primary else AnuMuted,fontSize=8.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}"""
if old_nav in s:
    s = s.replace(old_nav, new_nav)

s = s.replace(
    'OutlinedTextField(value=draft,onValueChange=onDraft,modifier=Modifier.weight(1f),placeholder={Text("Ask Anu anything…",fontSize=11.sp)},singleLine=true,shape=RoundedCornerShape(21.dp));',
    'OutlinedTextField(value=draft,onValueChange=onDraft,modifier=Modifier.weight(1f).heightIn(min=48.dp),placeholder={Text("Ask Anu anything…",fontSize=11.sp)},singleLine=true,shape=RoundedCornerShape(21.dp));'
)

s = s.replace(
    'Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(13.dp), color = AnuSurface)',
    'Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(13.dp), color = AnuSurface, tonalElevation = 2.dp)'
)

# Robust Tasks Dialog replacement:
imports = [
    "import android.app.TimePickerDialog\n",
    "import java.util.Calendar\n",
    "import java.util.Locale\n",
    "import androidx.compose.material.icons.filled.Schedule\n",
    "import androidx.compose.material3.AssistChip\n"
]
for imp in imports:
    if imp not in s:
        s = imp + s

def replace_dialog_in_source(src):
    # Strategy 1: Find by Tasks screen marker
    for marker in ["Your reminders and scheduled actions.", "+ Add new task", "Add new task", "tasks planned"]:
        pos = src.find(marker)
        if pos != -1:
            alert_idx = src.find("AlertDialog", pos)
            if alert_idx != -1 and alert_idx - pos < 5000:
                if_idx = src.rfind("if (", 0, alert_idx)
                if if_idx != -1:
                    brace_open = src.find("{", if_idx)
                    if brace_open != -1 and brace_open < alert_idx:
                        count = 1
                        i = brace_open + 1
                        while i < len(src) and count > 0:
                            if src[i] == '{': count += 1
                            elif src[i] == '}': count -= 1
                            i += 1
                        if count == 0:
                            block = src[if_idx:i]
                            var_m = re.search(r'if\s*\(\s*([A-Za-z0-9_]+)\s*\)', block)
                            if var_m:
                                dialog_var = var_m.group(1)
                                cb_m = re.search(r'([A-Za-z0-9_\.]+\s*\(\s*[A-Za-z0-9_]+\s*,\s*[A-Za-z0-9_]+\s*\))', block)
                                cb_call = cb_m.group(1).split('(')[0].strip() if cb_m else "onAdd"
                                rep = f"""if ({dialog_var}) {{
        AnuEnhancedAddTaskDialog(
            onDismiss = {{ {dialog_var} = false }},
            onAdd = {{ t, tm -> {cb_call}(t, tm) }}
        )
    }}"""
                                print(f"Replaced Tasks Dialog (Strategy 1, var: {dialog_var}, callback: {cb_call})")
                                return src[:if_idx] + rep + src[i:]
    return src

s = replace_dialog_in_source(s)

if "AnuEnhancedAddTaskDialog" not in s:
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
    s += dialog_composable

path.write_text(s, encoding="utf-8")
print("Applied reference-inspired UI polish and enhanced task time picker to MainActivity.kt")
