from pathlib import Path
import re

ROOT = Path("app/src/main/java/com/ghaniram/zoya")


def remove_duplicate_function(text: str, signature: str, next_marker: str) -> str:
    pattern = re.compile(r"\n    " + re.escape(signature) + r".*?(?=\n    " + re.escape(next_marker) + r")", re.S)
    matches = list(pattern.finditer(text))
    if len(matches) <= 1:
        return text
    first_end = matches[0].end()
    prefix = text[:first_end]
    suffix = text[first_end:]
    for match in reversed(matches[1:]):
        start = match.start() - first_end
        end = match.end() - first_end
        if 0 <= start < len(suffix):
            suffix = suffix[:start] + suffix[end:]
    return prefix + suffix


session = ROOT / "ZoyaSessionManager.kt"
s = session.read_text(encoding="utf-8")
s = remove_duplicate_function(s, "private fun buildSystemInstruction(): String {", "private fun property")
s = remove_duplicate_function(s, "private fun buildToolDeclarations(): JSONArray = JSONArray().apply {", "private fun ensureInitialized")

# Guard against a previous patch stage deleting the helper while leaving its call site.
if "buildToolDeclarations()" in s and "private fun buildToolDeclarations(): JSONArray" not in s:
    marker = "    private fun ensureInitialized"
    if marker not in s:
        raise SystemExit("Cannot restore buildToolDeclarations: ensureInitialized marker missing")
    helper = '''    private fun property(type: String, description: String) = JSONObject().apply { put("type", type); put("description", description) }
    private fun functionDeclaration(name: String, description: String, properties: JSONObject, required: JSONArray) = JSONObject().apply { put("name", name); put("description", description); put("parameters", JSONObject().apply { put("type", "OBJECT"); put("properties", properties); put("required", required) }) }
    private fun buildToolDeclarations(): JSONArray = JSONArray().apply {
        put(functionDeclaration("openWebsite", "Open a website URL.", JSONObject().apply { put("url", property("STRING", "Full URL.")); put("name", property("STRING", "Friendly name.")) }, JSONArray().put("url").put("name")))
        put(functionDeclaration("openApp", "Actually launch an installed Android app by its visible name.", JSONObject().put("appName", property("STRING", "Visible app name.")), JSONArray().put("appName")))
        put(functionDeclaration("phoneAction", "Perform supported phone and system controls.", JSONObject().apply { put("action", property("STRING", "Supported phone action.")); put("level", property("INTEGER", "Percentage 0-100 where applicable.")); put("hour", property("INTEGER", "Alarm hour 0-23.")); put("minute", property("INTEGER", "Alarm minute 0-59.")); put("message", property("STRING", "Alarm message.")) }, JSONArray().put("action")))
        put(functionDeclaration("accessibilityAction", "Perform an Accessibility action.", JSONObject().apply { put("action", property("STRING", "Action name.")); put("text", property("STRING", "Visible text.")); put("value", property("STRING", "Text/value to set.")) }, JSONArray().put("action")))
        put(functionDeclaration("getScreenInfo", "Read the current screen accessibility structure.", JSONObject(), JSONArray()))
        put(functionDeclaration("getDeviceInfo", "Read fresh Android device telemetry.", JSONObject(), JSONArray()))
        put(functionDeclaration("readNotifications", "Read active notifications.", JSONObject(), JSONArray()))
        put(functionDeclaration("replyToNotification", "Reply to a notification.", JSONObject().apply { put("query", property("STRING", "Notification search text.")); put("replyText", property("STRING", "Reply text.")) }, JSONArray().put("query").put("replyText")))
        put(functionDeclaration("openNotification", "Open a notification.", JSONObject().put("query", property("STRING", "Notification search text.")), JSONArray().put("query")))
        put(functionDeclaration("dismissNotification", "Dismiss a notification.", JSONObject().put("packageName", property("STRING", "Optional package name.")), JSONArray()))
        put(functionDeclaration("openNotificationAccessSettings", "Open Notification Access settings.", JSONObject(), JSONArray()))
        put(functionDeclaration("openAccessibilitySettings", "Open Accessibility settings.", JSONObject(), JSONArray()))
        put(functionDeclaration("savePersonalFact", "Save an explicitly requested personal memory.", JSONObject().put("fact", property("STRING", "Fact to remember.")), JSONArray().put("fact")))
        put(functionDeclaration("forgetPersonalFact", "Forget an explicitly requested personal memory.", JSONObject().put("fact", property("STRING", "Fact to forget.")), JSONArray().put("fact")))
    }

'''
    s = s.replace(marker, helper + marker, 1)

for file_name in ("ZoyaViewModel.kt", "ZoyaSessionManager.kt", "MainActivity.kt"):
    path = ROOT / file_name
    text = path.read_text(encoding="utf-8")
    seen = set()
    lines = []
    for line in text.splitlines(keepends=True):
        if line.startswith("import "):
            if line in seen:
                continue
            seen.add(line)
        lines.append(line)
    path.write_text("".join(lines), encoding="utf-8")

session.write_text(s, encoding="utf-8")
print("Normalized generated Kotlin and verified the Live tool declaration helper is present before Gradle compile.")
