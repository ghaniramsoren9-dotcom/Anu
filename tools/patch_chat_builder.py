from pathlib import Path

p = Path("tools/complete_chat.py")
s = p.read_text(encoding="utf-8")
old = "needle3 = '    fun sendText(text: String) { if (!setupComplete || text.isBlank()) return; webSocket?.send(JSONObject().put(\"realtimeInput\", JSONObject().put(\"text\", text)).toString()) }\\n'"
new = '''needle3 = \"\"\"    fun sendText(text: String) {
        if (!setupComplete || text.isBlank()) return
        webSocket?.send(JSONObject().put(\\\"realtimeInput\\\", JSONObject().put(\\\"text\\\", text)).toString())
    }
\"\"\"'''
if old in s:
    p.write_text(s.replace(old, new), encoding="utf-8")
    print("Patched complete_chat.py Gemini insertion point for current GeminiLiveClient.kt")
elif "needle3 = \"\"\"" in s:
    print("complete_chat.py already patched")
else:
    raise SystemExit("Could not locate complete_chat.py Gemini insertion assignment")
