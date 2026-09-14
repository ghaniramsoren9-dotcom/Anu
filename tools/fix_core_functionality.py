from pathlib import Path
import re
import subprocess

ROOT = Path("app/src/main/java/com/ghaniram/zoya")
MAIN = ROOT / "MainActivity.kt"
PHONE = ROOT / "PhoneControlManager.kt"
SESSION = ROOT / "ZoyaSessionManager.kt"

KNOWN_GOOD_MAIN = "f8eec1057d55ea87f693a17534cb349c26011b8a"
try:
    restored = subprocess.check_output(
        ["git", "show", f"{KNOWN_GOOD_MAIN}:app/src/main/java/com/ghaniram/zoya/MainActivity.kt"],
        text=True,
    )
    if "class MainActivity : ComponentActivity()" not in restored:
        raise RuntimeError("known-good MainActivity source is incomplete")
    MAIN.write_text(restored, encoding="utf-8")
    print("restored MainActivity from known-good commit", KNOWN_GOOD_MAIN)
except Exception as exc:
    raise SystemExit(f"ERROR: could not restore known-good MainActivity: {exc}")

def replace_once(path, old, new, label):
    s = path.read_text(encoding="utf-8")
    if old in s:
        path.write_text(s.replace(old, new, 1), encoding="utf-8")
        print("patched", label)
        return True
    print("skip", label, "(anchor not found)")
    return False

# 1) Dynamic time-based greeting and live user name.
s = MAIN.read_text(encoding="utf-8")
if 'fun anuTimeGreeting(' not in s:
    anchor = '\n// -------------------------------------------------------------\n// 1. HOME SCREEN\n// -------------------------------------------------------------\n'
    helper = '''\nprivate fun anuTimeGreeting(hour: Int): String = when (hour) {\n    in 5..11 -> "Good morning"\n    in 12..16 -> "Good afternoon"\n    in 17..20 -> "Good evening"\n    else -> "Good night"\n}\n\n'''
    if anchor in s:
        s = s.replace(anchor, helper + anchor, 1)

old = '''fun AnuHomeScreen(\n    state: ZoyaUiState,\n    onOrbClick: () -> Unit,\n    onSendPrompt: (String) -> Unit,\n    onQuickAction: (String) -> Unit\n) {\n    var searchInput by remember { mutableStateOf("") }\n    val isConnected = state.connectionState != ConnectionState.DISCONNECTED\n'''
new = '''fun AnuHomeScreen(\n    state: ZoyaUiState,\n    onOrbClick: () -> Unit,\n    onSendPrompt: (String) -> Unit,\n    onQuickAction: (String) -> Unit\n) {\n    var searchInput by remember { mutableStateOf("") }\n    val isConnected = state.connectionState != ConnectionState.DISCONNECTED\n    val context = LocalContext.current\n    val settingsStore = remember { AnuSettingsStore.getInstance(context) }\n    val userName = settingsStore.userName.trim().ifBlank { "Ghaniram" }\n    var currentHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }\n    LaunchedEffect(Unit) {\n        while (true) {\n            currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)\n            delay(30_000L)\n        }\n    }\n'''
if old in s:
    s = s.replace(old, new, 1)
    s = s.replace('text = "Good morning, Ghaniram 👋",', 'text = "${anuTimeGreeting(currentHour)}, $userName 👋",', 1)
else:
    print("skip home greeting anchor")

s = s.replace('.focusable()', '')
MAIN.write_text(s, encoding="utf-8")

# 2) Device controls.
s = PHONE.read_text(encoding="utf-8")
old = '''        if (service == null) {\n            return if (normalized in listOf("home", "gohome", "homescreen")) goHome()\n            else "Anu phone-control accessibility is not enabled. Open Accessibility settings and enable Anu."\n        }'''
new = '''        if (service == null) {\n            val needsAccessibility = normalized in listOf(\n                "back", "goback", "recents", "recentapps", "openrecentapps",\n                "notifications", "opennotifications", "quicksettings", "openquicksettings",\n                "power", "powerdialog", "lock", "lockscreen", "home", "gohome"\n            )\n            if (needsAccessibility) {\n                runCatching { openAccessibilitySettings() }\n                return "Anu phone-control accessibility is not enabled. I opened Accessibility settings; enable Anu, then retry the command."\n            }\n            return "Anu phone-control accessibility is not enabled. Open Accessibility settings and enable Anu."\n        }'''
if old in s:
    s = s.replace(old, new, 1)
else:
    print("skip accessibility fallback anchor")
old2 = '''            "home", "gohome", "back", "goback", "recents", "recentapps", "openrecentapps", "notifications", "opennotifications", "quicksettings", "openquicksettings", "power", "powerdialog", "lock", "lockscreen" -> service.globalAction(action)'''
new2 = '''            "home", "gohome", "back", "goback", "recents", "recentapps", "openrecentapps", "notifications", "opennotifications", "quicksettings", "openquicksettings", "power", "powerdialog", "lock", "lockscreen" -> service.globalAction(action)\n            "volumeup", "volume_up", "raisevolume", "soundup", "volumeincrease" -> { volumeUp(); true }\n            "volumedown", "volume_down", "lowervolume", "sounddown", "volumedecrease" -> { volumeDown(); true }\n            "mute", "mutevolume", "togglemute" -> { muteVolume(); true }'''
if old2 in s:
    s = s.replace(old2, new2, 1)
else:
    print("skip volume routing anchor")
PHONE.write_text(s, encoding="utf-8")

# 3) Personal settings in system context.
s = SESSION.read_text(encoding="utf-8")
old3 = '''        val memories = persistedMemories.joinToString("\\n") { it.take(700) }\n        buildString {\n            append("PERSISTENT MEMORY CONTEXT. This is stored history, NOT current sensory evidence.\\n")'''
new3 = '''        val memories = persistedMemories.joinToString("\\n") { it.take(700) }\n        val settings = AnuSettingsStore.getInstance(app)\n        val userName = settings.userName.trim()\n        val favoriteSong = settings.favoriteSong.trim()\n        val youtubeChannel = settings.youtubeChannel.trim()\n        buildString {\n            append("PERSISTENT MEMORY CONTEXT. This is stored history, NOT current sensory evidence.\\n")\n            if (userName.isNotBlank()) append("User's saved name: $userName\\n")\n            if (favoriteSong.isNotBlank()) append("User's saved favorite song: $favoriteSong\\n")\n            if (youtubeChannel.isNotBlank()) {\n                append("User's saved YouTube channel: $youtubeChannel\\n")\n                append("If the user mentions this saved YouTube channel by its name, URL, handle, or channel ID, treat it as their configured channel. When they ask to open it, use YouTube/openApp or a web URL as appropriate; do not ignore the saved channel setting.\\n")\n            }\n'''
if old3 in s:
    s = s.replace(old3, new3, 1)
else:
    print("skip personal context anchor")
SESSION.write_text(s, encoding="utf-8")

# 4) History ordering.
s = MAIN.read_text(encoding="utf-8")
s = s.replace('.focusable()', '')
s2 = re.sub(r'val filteredMessages = remember\(messages, searchQuery\) \{\s*if \(searchQuery\.isBlank\(\)\) messages\s*else messages\.filter', 'val filteredMessages = remember(messages, searchQuery) {\n        val orderedMessages = messages.sortedBy { it.timestampMillis }\n        if (searchQuery.isBlank()) orderedMessages\n        else orderedMessages.filter', s, count=1)
if s2 != s:
    MAIN.write_text(s2, encoding="utf-8")
    print("patched history ordering")

# 5) Defensive normalization: other repair scripts/workflow steps may have
# applied the same greeting declarations. Collapse repeated identical blocks
# so Kotlin never sees duplicate local declarations in AnuHomeScreen.
s = MAIN.read_text(encoding="utf-8")
block = '''    val context = LocalContext.current\n    val settingsStore = remember { AnuSettingsStore.getInstance(context) }\n    val userName = settingsStore.userName.trim().ifBlank { "Ghaniram" }\n    var currentHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }\n    LaunchedEffect(Unit) {\n        while (true) {\n            currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)\n            delay(30_000L)\n        }\n    }\n'''
while s.count(block) > 1:
    first = s.find(block)
    second = s.find(block, first + len(block))
    if second < 0:
        break
    s = s[:second] + s[second + len(block):]
# Remove any accidental repeated greeting expression while retaining one.
greeting = 'text = "${anuTimeGreeting(currentHour)}, $userName 👋",'
first = s.find(greeting)
if first >= 0:
    second = s.find(greeting, first + len(greeting))
    while second >= 0:
        s = s[:second] + s[second + len(greeting):]
        second = s.find(greeting, first + len(greeting))
MAIN.write_text(s, encoding="utf-8")

# Final guards.
final_main = MAIN.read_text(encoding="utf-8")
if ".focusable()" in final_main:
    raise SystemExit("ERROR: incompatible focusable() modifier still present")
if "class MainActivity : ComponentActivity()" not in final_main:
    raise SystemExit("ERROR: MainActivity class missing after repair")
if final_main.count(block) > 1:
    raise SystemExit("ERROR: duplicate AnuHomeScreen settings block remains")
print("core functionality repair complete")
