from pathlib import Path
import re

ROOT = Path("app/src/main/java/com/ghaniram/zoya")
MAIN = ROOT / "MainActivity.kt"
PHONE = ROOT / "PhoneControlManager.kt"
SESSION = ROOT / "ZoyaSessionManager.kt"


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

# BasicTextField must own focus and remain directly editable on modern Android/Compose.
s = s.replace('modifier = Modifier.fillMaxWidth()\n                        )\n                    }\n\n                    Spacer(Modifier.width(8.dp))', 'modifier = Modifier.fillMaxWidth().focusable()\n                        )\n                    }\n\n                    Spacer(Modifier.width(8.dp))', 1)
MAIN.write_text(s, encoding="utf-8")

# 2) Device controls: volume was implemented in PhoneControlManager but was not
# reachable through accessibilityAction. Add canonical aliases and a reliable
# fallback for the accessibility service being disabled.
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

# 3) Persisted personal settings must be part of Anu's system context so a
# saved YouTube channel name/URL/ID is actually understood when mentioned.
s = SESSION.read_text(encoding="utf-8")
old3 = '''        val memories = persistedMemories.joinToString("\\n") { it.take(700) }\n        buildString {\n            append("PERSISTENT MEMORY CONTEXT. This is stored history, NOT current sensory evidence.\\n")'''
new3 = '''        val memories = persistedMemories.joinToString("\\n") { it.take(700) }\n        val settings = AnuSettingsStore.getInstance(app)\n        val userName = settings.userName.trim()\n        val favoriteSong = settings.favoriteSong.trim()\n        val youtubeChannel = settings.youtubeChannel.trim()\n        buildString {\n            append("PERSISTENT MEMORY CONTEXT. This is stored history, NOT current sensory evidence.\\n")\n            if (userName.isNotBlank()) append("User's saved name: $userName\\n")\n            if (favoriteSong.isNotBlank()) append("User's saved favorite song: $favoriteSong\\n")\n            if (youtubeChannel.isNotBlank()) {\n                append("User's saved YouTube channel: $youtubeChannel\\n")\n                append("If the user mentions this saved YouTube channel by its name, URL, handle, or channel ID, treat it as their configured channel. When they ask to open it, use YouTube/openApp or a web URL as appropriate; do not ignore the saved channel setting.\\n")\n            }\n'''
if old3 in s:
    s = s.replace(old3, new3, 1)
else:
    print("skip personal context anchor")
SESSION.write_text(s, encoding="utf-8")

# 4) History UI: keep the complete persisted list in chronological order and
# avoid accidental UI-side truncation if an earlier generator inserted one.
# Storage remains bounded by the repository's explicit persistence policy, but
# the UI must never take only a small recent slice.
s = MAIN.read_text(encoding="utf-8")
s2 = re.sub(r'val filteredMessages = remember\(messages, searchQuery\) \{\s*if \(searchQuery\.isBlank\(\)\) messages\s*else messages\.filter', 'val filteredMessages = remember(messages, searchQuery) {\n        val orderedMessages = messages.sortedBy { it.timestampMillis }\n        if (searchQuery.isBlank()) orderedMessages\n        else orderedMessages.filter', s, count=1)
if s2 != s:
    MAIN.write_text(s2, encoding="utf-8")
    print("patched history ordering")

print("core functionality repair complete")
