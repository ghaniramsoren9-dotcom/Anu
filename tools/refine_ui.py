from pathlib import Path

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
if old not in s:
    raise SystemExit("drawer host pattern not found")
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
if old_nav not in s:
    raise SystemExit("nav pattern not found")
s = s.replace(old_nav, new_nav)

s = s.replace(
    'OutlinedTextField(value=draft,onValueChange=onDraft,modifier=Modifier.weight(1f),placeholder={Text("Ask Anu anything…",fontSize=11.sp)},singleLine=true,shape=RoundedCornerShape(21.dp));',
    'OutlinedTextField(value=draft,onValueChange=onDraft,modifier=Modifier.weight(1f).heightIn(min=48.dp),placeholder={Text("Ask Anu anything…",fontSize=11.sp)},singleLine=true,shape=RoundedCornerShape(21.dp));'
)

s = s.replace(
    'Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(13.dp), color = AnuSurface)',
    'Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(13.dp), color = AnuSurface, tonalElevation = 2.dp)'
)

if s == original:
    raise SystemExit("no changes applied")
path.write_text(s, encoding="utf-8")
print("Applied reference-inspired UI polish to MainActivity.kt")
