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

# Direct enhancement of the Tasks Time Input from screenshot
if "anuTimePickerDialog" not in s:
    imports = [
        "import android.app.TimePickerDialog\n",
        "import java.util.Calendar\n",
        "import java.util.Locale\n",
        "import androidx.compose.material.icons.filled.Schedule\n",
        "import androidx.compose.material3.ButtonDefaults\n",
        "import androidx.compose.foundation.layout.PaddingValues\n",
        "import androidx.compose.foundation.shape.RoundedCornerShape\n"
    ]
    for imp in imports:
        if imp not in s:
            s = imp + s

    marker = "Time (e.g. 7:00 PM)"
    pos = s.find(marker)
    if pos == -1:
        marker = "7:00 PM"
        pos = s.find(marker)
    
    if pos != -1:
        field_start = s.rfind("OutlinedTextField(", 0, pos)
        if field_start == -1:
            field_start = s.rfind("TextField(", 0, pos)
        
        if field_start != -1:
            brace_open = s.find("(", field_start)
            count = 1
            i = brace_open + 1
            while i < len(s) and count > 0:
                if s[i] == '(': count += 1
                elif s[i] == ')': count -= 1
                i += 1
            field_end = i
            field_text = s[field_start:field_end]

            time_var_m = re.search(r'value\s*=\s*([A-Za-z0-9_]+)', field_text)
            time_var = time_var_m.group(1) if time_var_m else "taskTime"

            replacement = f"""val anuContext = androidx.compose.ui.platform.LocalContext.current
                    val anuCalendar = java.util.Calendar.getInstance()
                    val anuTimePickerDialog = androidx.compose.runtime.remember {{
                        android.app.TimePickerDialog(
                            anuContext,
                            {{ _, h, m ->
                                val ampm = if (h >= 12) "PM" else "AM"
                                val h12 = if (h % 12 == 0) 12 else h % 12
                                {time_var} = String.format(java.util.Locale.US, "%d:%02d %s", h12, m, ampm)
                            }},
                            anuCalendar.get(java.util.Calendar.HOUR_OF_DAY),
                            anuCalendar.get(java.util.Calendar.MINUTE),
                            false
                        )
                    }}
                    OutlinedTextField(
                        value = {time_var},
                        onValueChange = {{ {time_var} = it }},
                        label = {{ Text("Time (e.g. 7:00 PM)") }},
                        trailingIcon = {{
                            IconButton(onClick = {{ anuTimePickerDialog.show() }}) {{
                                Icon(
                                    Icons.Filled.Schedule,
                                    contentDescription = "Pick Time",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }}
                        }},
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Quick Presets:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {{
                        listOf(15 to "+15m", 30 to "+30m", 60 to "+1h").forEach {{ (min, label) ->
                            Button(
                                onClick = {{
                                    val cal = java.util.Calendar.getInstance().apply {{ add(java.util.Calendar.MINUTE, min) }}
                                    val ampm = if (cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "AM" else "PM"
                                    val h = if (cal.get(java.util.Calendar.HOUR) == 0) 12 else cal.get(java.util.Calendar.HOUR)
                                    {time_var} = String.format(java.util.Locale.US, "%d:%02d %s", h, cal.get(java.util.Calendar.MINUTE), ampm)
                                }},
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                modifier = Modifier.height(32.dp)
                            ) {{ Text(label, fontSize = 11.sp) }}
                        }}
                        Button(
                            onClick = {{ anuTimePickerDialog.show() }},
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier.height(32.dp)
                        ) {{ Text("🕒 Clock", fontSize = 11.sp) }}
                    }}"""
            s = s[:field_start] + replacement + s[field_end:]

path.write_text(s, encoding="utf-8")
print("Tasks time input enhanced successfully.")
