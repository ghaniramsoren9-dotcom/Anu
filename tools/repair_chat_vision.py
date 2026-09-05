from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "app/src/main/java/com/ghaniram/zoya"
session = PKG / "ZoyaSessionManager.kt"
vm = PKG / "ZoyaViewModel.kt"
main = PKG / "MainActivity.kt"
vision = PKG / "AnuSharedLiveVisionScreen.kt"
history = PKG / "ChatHistoryStore.kt"

# Replace the temporary preferences-only history with one durable JSON archive.
history.write_text('''package com.ghaniram.zoya

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

class ChatHistoryStore(private val context: Context) {
    private val file get() = context.filesDir.resolve("anu_conversations.json")
    private val maxMessages = 1000

    @Synchronized fun getAll(): List<ChatMessage> {
        val raw = runCatching { if (file.exists()) file.readText(Charsets.UTF_8) else "" }.getOrDefault("")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    val role = runCatching { ChatRole.valueOf(o.optString("role")) }.getOrNull() ?: continue
                    val text = o.optString("text").trim()
                    if (text.isNotBlank()) add(ChatMessage(o.optString("id"), role, text, o.optLong("time")))
                }
            }
        }.getOrElse { emptyList() }
    }

    @Synchronized fun append(message: ChatMessage) = save(getAll() + message)

    @Synchronized fun replaceAssistant(id: String, text: String) {
        val items = getAll().toMutableList()
        val i = items.indexOfFirst { it.id == id && it.role == ChatRole.ANU }
        if (i >= 0) { items[i] = items[i].copy(text = text); save(items) }
    }

    fun getRecentContext(limit: Int = 40): String {
        val items = getAll().takeLast(limit)
        if (items.isEmpty()) return ""
        return "\\n\\nPersistent conversation archive from previous sessions:\\n" + items.joinToString("\\n") { m ->
            val who = if (m.role == ChatRole.USER) "User" else if (m.role == ChatRole.ANU) "Anu" else "System"
            "$who: ${m.text.take(1600)}"
        }
    }

    @Synchronized fun clear() { runCatching { file.delete() } }

    @Synchronized fun exportToDownloads(): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "Anu_Conversations_${System.currentTimeMillis()}.json")
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Anu")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out -> out.write(if (file.exists()) file.readBytes() else "[]".toByteArray()) }
            uri
        }.getOrElse { context.contentResolver.delete(uri, null, null); null }
    }

    private fun save(items: List<ChatMessage>) {
        val a = JSONArray()
        items.takeLast(maxMessages).forEach { m -> a.put(JSONObject().apply { put("id",m.id); put("role",m.role.name); put("text",m.text); put("time",m.timestampMillis) }) }
        val tmp = context.filesDir.resolve("anu_conversations.json.tmp")
        runCatching { tmp.writeText(a.toString(), Charsets.UTF_8); if (!tmp.renameTo(file)) { file.writeText(a.toString(), Charsets.UTF_8); tmp.delete() } }
    }
}
''', encoding="utf-8")

s = session.read_text(encoding="utf-8")
if 'private var activeAssistantMessageId: String? = null' not in s:
    s = s.replace('private var modelSpeaking = false', 'private var modelSpeaking = false\n    private var activeAssistantMessageId: String? = null', 1)
if 'private val chatHistoryStore by lazy { ChatHistoryStore(app) }' not in s:
    s = s.replace('private val memoryStore by lazy { MemoryStore(app) }', 'private val memoryStore by lazy { MemoryStore(app) }\n    private val chatHistoryStore by lazy { ChatHistoryStore(app) }', 1)
if 'chatMessages = chatHistoryStore.getAll()' not in s:
    s = s.replace('memories = memoryStore.getAll(), quote = idleQuotes[savedLanguage]?.random() ?: ""', 'memories = memoryStore.getAll(), chatMessages = chatHistoryStore.getAll(), quote = idleQuotes[savedLanguage]?.random() ?: ""', 1)

# Voice input transcription is delivered as "You: ..." by GeminiLiveClient.
# Treat it as a real USER turn instead of appending it to the previous ANU bubble.
pat = re.compile(r'override fun onText\(text: String\) \{.*?\}; override fun onInterrupted', re.S)
m = pat.search(s)
if not m: raise SystemExit('Live onText callback not found')
cb = '''override fun onText(text: String) {
                val raw = text.trim()
                if (raw.isBlank()) return
                if (raw.startsWith("You: ")) {
                    activeAssistantMessageId = null
                    val value = raw.removePrefix("You: ").trim()
                    if (value.isNotBlank()) {
                        val msg = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, value, System.currentTimeMillis())
                        chatHistoryStore.append(msg)
                        _state.update { it.copy(chatMessages = it.chatMessages + msg) }
                    }
                    return
                }
                _state.update { state ->
                    val last = state.chatMessages.lastOrNull()
                    if (last?.role == ChatRole.ANU && activeAssistantMessageId == last.id) {
                        val updated = last.copy(text = last.text + raw)
                        chatHistoryStore.replaceAssistant(last.id, updated.text)
                        state.copy(chatMessages = state.chatMessages.dropLast(1) + updated)
                    } else {
                        val msg = ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, raw, System.currentTimeMillis())
                        activeAssistantMessageId = msg.id
                        chatHistoryStore.append(msg)
                        state.copy(chatMessages = state.chatMessages + msg)
                    }
                }
            }; override fun onInterrupted'''
s = s[:m.start()] + cb + s[m.end():]

# Make restored history part of every new Live setup, including Live Vision sessions.
start = s.find('    private fun buildSystemInstruction(): String {')
if start >= 0:
    end = s.find('\n    }', start)
    if end >= 0:
        fn = '''    private fun buildSystemInstruction(): String {
        val language = when (_state.value.language) {
            ZoyaLanguage.ODIA -> "Respond primarily in natural fluent Odia."
            ZoyaLanguage.SANTALI -> "Respond primarily in natural fluent Santali."
            ZoyaLanguage.HINDI -> "Respond primarily in natural fluent Hindi."
            ZoyaLanguage.ENGLISH -> "Respond primarily in natural fluent English."
        }
        val memories = memoryStore.getAll()
        val memoryText = if (memories.isEmpty()) "" else "\\nApproved saved memories:\\n" + memories.joinToString("\\n") { "- $it" }
        val historyText = chatHistoryStore.getRecentContext(40)
        return "You are Anu, a continuous AI assistant. $language Maintain conversation continuity using the archive below. Do not claim memories that are not present. Camera input is Anu's eyes and belongs to this same conversation; never start a separate Vision conversation. When asked for distance, give only a clearly labelled visual estimate unless reliable depth data exists; never invent an exact physical measurement. $memoryText$historyText"
    }'''
        s = s[:start] + fn + s[end+6:]

# Backup/clear APIs.
marker = '    fun clearMemories() { ensureInitialized(); memoryStore.clear(); _state.update { it.copy(memories = emptyList()) } }'
if 'fun exportChatBackup(): Uri?' not in s:
    s = s.replace(marker, marker + '\n    fun exportChatBackup(): Uri? { ensureInitialized(); return chatHistoryStore.exportToDownloads() }\n    fun clearChatHistory() { ensureInitialized(); chatHistoryStore.clear(); _state.update { it.copy(chatMessages = emptyList()) } }', 1)
session.write_text(s, encoding="utf-8")

# ViewModel bridge for backup/clear.
v = vm.read_text(encoding="utf-8")
if 'fun exportChatBackup(): Uri?' not in v:
    v = v.replace('import android.app.Application\n', 'import android.app.Application\nimport android.net.Uri\n', 1)
    v = v.replace('    fun clearMemories() = ZoyaSessionManager.clearMemories()', '    fun exportChatBackup(): Uri? = ZoyaSessionManager.exportChatBackup()\n    fun clearChatHistory() = ZoyaSessionManager.clearChatHistory()\n    fun clearMemories() = ZoyaSessionManager.clearMemories()', 1)
vm.write_text(v, encoding="utf-8")

# Generated chat UI: add backup/share/clear controls without changing the message renderer.
m = main.read_text(encoding="utf-8")
if 'onBackupChat' not in m and 'AnuEnhancedChat(' in m:
    m = m.replace('onAttach = { uri, mime, name, bytes -> viewModel.sendAttachment(uri, mime, name, bytes) }', 'onAttach = { uri, mime, name, bytes -> viewModel.sendAttachment(uri, mime, name, bytes) },\n                            onBackupChat = { viewModel.exportChatBackup() },\n                            onClearChat = viewModel::clearChatHistory', 1)
    m = m.replace('    onAttach: (Uri, String, String, ByteArray) -> Unit\n)', '    onAttach: (Uri, String, String, ByteArray) -> Unit,\n    onBackupChat: () -> Uri?,\n    onClearChat: () -> Unit\n)', 1)
    needle = '        Spacer(Modifier.height(6.dp))\n        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom'
    controls = '''        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onBackupChat() }, modifier = Modifier.weight(1f).height(34.dp), shape = RoundedCornerShape(17.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Filled.Save, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(3.dp)); Text("BACKUP", fontSize = 8.sp)
            }
            OutlinedButton(onClick = { val uri = onBackupChat(); if (uri != null) context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share Anu conversations")) }, modifier = Modifier.weight(1f).height(34.dp), shape = RoundedCornerShape(17.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Filled.Share, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(3.dp)); Text("SHARE", fontSize = 8.sp)
            }
            OutlinedButton(onClick = onClearChat, modifier = Modifier.weight(1f).height(34.dp), shape = RoundedCornerShape(17.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Filled.DeleteSweep, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(3.dp)); Text("CLEAR", fontSize = 8.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom'''
    if needle not in m: raise SystemExit('Chat input row not found')
    m = m.replace(needle, controls, 1)
main.write_text(m, encoding="utf-8")

# Keep shared Live Vision running while flipping lenses and sample a little faster.
v = vision.read_text(encoding="utf-8")
v = v.replace('onClick = { if (live) live = false; lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }', 'onClick = { lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }', 1)
v = v.replace('delay(1000L)', 'delay(650L)', 1)
vision.write_text(v, encoding="utf-8")

print("Anu conversation archive repaired: persistent JSON history, Live input/user turn separation, restored Live Vision context, backup/share/clear controls, and non-pausing camera flip.")
