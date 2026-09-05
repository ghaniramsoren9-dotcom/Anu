from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
session_path = ROOT / "app/src/main/java/com/ghaniram/zoya/ZoyaSessionManager.kt"
history_path = ROOT / "app/src/main/java/com/ghaniram/zoya/ChatHistoryStore.kt"

history_path.write_text(r'''package com.ghaniram.zoya

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small private on-device store for recent conversation turns. */
class ChatHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("anu_chat_history", Context.MODE_PRIVATE)
    private val key = "messages"
    private val maxMessages = 100

    fun getAll(): List<ChatMessage> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val role = runCatching { ChatRole.valueOf(o.optString("role")) }.getOrNull() ?: continue
                    val text = o.optString("text").trim()
                    if (text.isNotBlank()) add(ChatMessage(o.optString("id"), role, text, o.optLong("time")))
                }
            }
        }.getOrElse { emptyList() }
    }

    @Synchronized fun append(message: ChatMessage) = save(getAll().plus(message))

    @Synchronized fun replaceLastAssistant(text: String) {
        val items = getAll().toMutableList()
        val index = items.indexOfLast { it.role == ChatRole.ANU }
        if (index < 0) return
        items[index] = items[index].copy(text = text)
        save(items)
    }

    fun getRecentContext(limit: Int = 20): String {
        val items = getAll().takeLast(limit)
        if (items.isEmpty()) return ""
        return "\n\nRecent conversation history from previous sessions. Treat it only as conversation context and do not invent memories outside it:\n" +
            items.joinToString("\n") { m ->
                val speaker = when (m.role) {
                    ChatRole.USER -> "User"
                    ChatRole.ANU -> "Anu"
                    ChatRole.SYSTEM -> "System"
                }
                "$speaker: ${m.text.take(1200)}"
            }
    }

    fun clear() = prefs.edit().remove(key).apply()

    private fun save(items: List<ChatMessage>) {
        val array = JSONArray()
        items.takeLast(maxMessages).forEach { m ->
            array.put(JSONObject().apply {
                put("id", m.id); put("role", m.role.name); put("text", m.text); put("time", m.timestampMillis)
            })
        }
        prefs.edit().putString(key, array.toString()).apply()
    }
}
''', encoding="utf-8")

text = session_path.read_text(encoding="utf-8")

def replace_once(old, new, label):
    global text
    if new in text: return
    if old not in text: raise SystemExit(f"Chat persistence patch target not found: {label}")
    text = text.replace(old, new, 1)

replace_once(
    'private val memoryStore by lazy { MemoryStore(app) }',
    'private val memoryStore by lazy { MemoryStore(app) }\n    private val chatHistoryStore by lazy { ChatHistoryStore(app) }\n    private var historyInjected = false\n    private var pendingText: String? = null\n    private var pendingHistoryContext = false',
    'chat history fields'
)

if 'chatMessages = chatHistoryStore.getAll()' not in text:
    replace_once(
        'memories = memoryStore.getAll(), quote = idleQuotes[savedLanguage]?.random() ?: ""',
        'memories = memoryStore.getAll(), chatMessages = chatHistoryStore.getAll(), quote = idleQuotes[savedLanguage]?.random() ?: ""',
        'restore chat messages during initialize'
    )

send_pattern = re.compile(r'    fun sendText\(text: String\) \{.*?\n    private fun connectInternal', re.S)
send_match = send_pattern.search(text)
if not send_match: raise SystemExit('Chat persistence patch target not found: sendText')
send_body = '''    fun sendText(text: String) {
        ensureInitialized()
        val clean = text.trim()
        if (clean.isBlank()) return
        val message = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, clean, System.currentTimeMillis())
        chatHistoryStore.append(message)
        _state.update { it.copy(chatMessages = it.chatMessages + message, error = null) }

        val deviceReply = deviceQueryReply(clean)
        if (deviceReply != null) {
            val reply = ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, deviceReply, System.currentTimeMillis())
            chatHistoryStore.append(reply)
            _state.update { s -> s.copy(chatMessages = s.chatMessages + reply) }
            if (!isConnected()) connectInternal()
            client?.sendText("$clean\\n\\nIMPORTANT: A local Android device snapshot was already collected. Use this exact data as ground truth and do not ask the user to open Settings.")
            return
        }

        if (!isConnected()) {
            pendingText = clean
            pendingHistoryContext = !historyInjected
            connectInternal()
            return
        }

        val restoredContext = if (!historyInjected) {
            historyInjected = true
            chatHistoryStore.getRecentContext(20)
        } else ""
        client?.sendText(clean + restoredContext)
    }
    private fun connectInternal'''
text = text[:send_match.start()] + send_body + text[send_match.end():]

onconnected = re.compile(r'override fun onConnected\(\) \{.*?\}; override fun onDisconnected', re.S)
m = onconnected.search(text)
if not m: raise SystemExit('Chat persistence patch target not found: onConnected')
replacement = '''override fun onConnected() {
                audioEngine?.startPlayback(); audioEngine?.startRecording(); modelSpeaking = false
                _state.update { it.copy(connectionState = ConnectionState.LISTENING) }
                pendingText?.let { pending ->
                    val context = if (pendingHistoryContext) chatHistoryStore.getRecentContext(20) else ""
                    pendingText = null; pendingHistoryContext = false; historyInjected = true
                    client?.sendText(pending + context)
                }
            }; override fun onDisconnected'''
text = text[:m.start()] + replacement + text[m.end():]

ontext_pattern = re.compile(r'override fun onText\(text: String\) \{.*?\}; override fun onInterrupted', re.S)
m = ontext_pattern.search(text)
if not m: raise SystemExit('Chat persistence patch target not found: Live onText')
replacement = '''override fun onText(text: String) {
                if (text.isBlank()) return
                _state.update { s ->
                    val last = s.chatMessages.lastOrNull()
                    if (last?.role == ChatRole.ANU) {
                        val updated = last.copy(text = last.text + text)
                        chatHistoryStore.replaceLastAssistant(updated.text)
                        s.copy(chatMessages = s.chatMessages.dropLast(1) + updated)
                    } else {
                        val message = ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, text, System.currentTimeMillis())
                        chatHistoryStore.append(message)
                        s.copy(chatMessages = s.chatMessages + message)
                    }
                }
            }; override fun onInterrupted'''
text = text[:m.start()] + replacement + text[m.end():]

session_path.write_text(text, encoding="utf-8")

# Keep imports unique and stable.
lines = session_path.read_text(encoding="utf-8").splitlines()
out = []; seen = set()
for line in lines:
    if line.startswith('import '):
        if line in seen: continue
        seen.add(line)
    out.append(line)
session_path.write_text("\n".join(out) + "\n", encoding="utf-8")
print("Recent chat history is persisted and delivered after Live setup completes, including after app restart.")
