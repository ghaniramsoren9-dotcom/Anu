from pathlib import Path

ROOT = Path("app/src/main/java/com/ghaniram/zoya")
main = ROOT / "MainActivity.kt"
s = main.read_text(encoding="utf-8")
original = s

# Add imports used by the real attachment picker.
s = s.replace(
    "import android.content.Intent\n",
    "import android.content.Intent\nimport android.net.Uri\n"
)
s = s.replace(
    "import androidx.activity.result.contract.ActivityResultContracts\n",
    "import androidx.activity.result.contract.ActivityResultContracts\nimport androidx.activity.compose.rememberLauncherForActivityResult\n"
)
s = s.replace(
    "import androidx.compose.ui.graphics.Brush\n",
    "import androidx.compose.ui.graphics.Brush\nimport androidx.compose.ui.platform.LocalContext\n"
)

old_call = '''AnuTab.CHAT -> AnuChat(state.chatMessages, chatListState, draft, { draft = it }) {
                            if (it.isNotBlank()) { viewModel.sendText(it); draft = "" }
                        }'''
new_call = '''AnuTab.CHAT -> AnuEnhancedChat(
                            messages = state.chatMessages,
                            listState = chatListState,
                            draft = draft,
                            onDraft = { draft = it },
                            onSend = { if (it.isNotBlank()) { viewModel.sendText(it); draft = "" } },
                            onAttach = { uri, mime, name, bytes -> viewModel.sendAttachment(uri, mime, name, bytes) }
                        )'''
if old_call not in s:
    raise SystemExit("Chat host pattern not found")
s = s.replace(old_call, new_call)

# Append the complete chat implementation while keeping the existing chat composable intact.
chat = r'''

@Composable
private fun AnuEnhancedChat(
    messages: List<ChatMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: (String) -> Unit,
    onAttach: (Uri, String, String, ByteArray) -> Unit
) {
    val context = LocalContext.current
    var attachmentName by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "attachment"
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes != null && bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024) {
            attachmentName = name
            onAttach(uri, mime, name, bytes)
        } else {
            attachmentName = ""
            onSend("I selected '$name', but the file is empty or larger than the 8 MB chat attachment limit.")
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (attachmentName.isNotBlank()) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AttachFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(attachmentName, color = AnuText, fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("ATTACHED", color = MaterialTheme.colorScheme.primary, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.height(7.dp))
        }

        if (messages.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                    Icon(Icons.Filled.SmartToy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(18.dp).size(30.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Chat with Anu", color = AnuText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("Ask anything, attach a file, or continue your voice conversation.", color = AnuMuted, fontSize = 10.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AssistChip(onClick = { onSend("Explain this for me") }, label = { Text("Explain", fontSize = 8.sp) })
                    AssistChip(onClick = { onSend("Help me study") }, label = { Text("Study", fontSize = 8.sp) })
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages, key = { it.id }) { message ->
                    val user = message.role == ChatRole.USER
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                        Surface(shape = RoundedCornerShape(16.dp), color = if (user) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else AnuSurface, tonalElevation = 1.dp, modifier = Modifier.widthIn(max = 310.dp)) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Text(if (user) "YOU" else "ANU", color = if (user) MaterialTheme.colorScheme.primary else AnuMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(message.text, color = AnuText, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            IconButton(onClick = { picker.launch(arrayOf("image/*", "application/pdf", "text/*", "application/msword", "application/vnd.openxmlformats-officedocument.*", "video/*")) }, modifier = Modifier.size(46.dp)) {
                Icon(Icons.Filled.AttachFile, "Attach document, photo, PDF or video", tint = AnuMuted)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraft,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                placeholder = { Text("Message Anu…", fontSize = 11.sp) },
                maxLines = 5,
                shape = RoundedCornerShape(22.dp)
            )
            FilledIconButton(onClick = { onSend(draft) ; attachmentName = "" }, modifier = Modifier.size(46.dp), enabled = draft.isNotBlank()) {
                Icon(Icons.Filled.Send, "Send", modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Attachments up to 8 MB • Anu receives the selected file as chat context", color = AnuMuted, fontSize = 7.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}
'''
s += chat

if s == original:
    raise SystemExit("No MainActivity changes applied")
main.write_text(s, encoding="utf-8")

# ViewModel bridge
vm = ROOT / "ZoyaViewModel.kt"
v = vm.read_text(encoding="utf-8")
v_original = v
v = v.replace("import android.app.Application\n", "import android.app.Application\nimport android.net.Uri\n")
needle = "    fun clearMemories() = ZoyaSessionManager.clearMemories()\n"
insert = '''    fun sendAttachment(uri: Uri, mimeType: String, fileName: String, bytes: ByteArray) {
        ZoyaSessionManager.sendAttachment(uri, mimeType, fileName, bytes)
    }
'''
if needle not in v:
    raise SystemExit("ViewModel insertion point not found")
v = v.replace(needle, insert + needle)
vm.write_text(v, encoding="utf-8")

# Session bridge
sm = ROOT / "ZoyaSessionManager.kt"
m = sm.read_text(encoding="utf-8")
m_original = m
needle2 = "    fun clearMemories() { ensureInitialized(); memoryStore.clear(); _state.update { it.copy(memories = emptyList()) } }\n"
insert2 = '''    fun sendAttachment(uri: Uri, mimeType: String, fileName: String, bytes: ByteArray) {
        ensureInitialized()
        if (bytes.isEmpty()) return
        val prompt = "I attached a file named '$fileName'. Analyze the attached file and answer my questions about it. If it is an image, use its visual contents. If it is a PDF/document, use the supplied file data."
        _state.update { it.copy(chatMessages = it.chatMessages + ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, "📎 $fileName\\n$prompt", System.currentTimeMillis()), error = null) }
        if (!isConnected()) connectInternal()
        client?.sendFile(mimeType, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP), prompt)
    }
'''
if needle2 not in m:
    raise SystemExit("Session insertion point not found")
m = m.replace(needle2, insert2 + needle2)
m = m.replace("import android.content.Intent\n", "import android.content.Intent\nimport android.net.Uri\n")
sm.write_text(m, encoding="utf-8")

# Gemini Live bridge: send file data as a client-content user turn.
gc = ROOT / "GeminiLiveClient.kt"
g = gc.read_text(encoding="utf-8")
g_original = g
needle3 = '    fun sendText(text: String) { if (!setupComplete || text.isBlank()) return; webSocket?.send(JSONObject().put("realtimeInput", JSONObject().put("text", text)).toString()) }\n'
insert3 = '''    fun sendFile(mimeType: String, base64Data: String, prompt: String) {
        if (!setupComplete || base64Data.isBlank()) return
        val part = JSONObject().apply {
            put("inlineData", JSONObject().apply { put("mimeType", mimeType); put("data", base64Data) })
        }
        val promptPart = JSONObject().put("text", prompt)
        val parts = JSONArray().put(promptPart).put(part)
        webSocket?.send(JSONObject().put("clientContent", JSONObject().apply {
            put("turns", JSONArray().put(JSONObject().apply { put("role", "user"); put("parts", parts) }))
            put("turnComplete", true)
        }).toString())
    }
'''
if needle3 not in g:
    raise SystemExit("Gemini insertion point not found")
g = g.replace(needle3, needle3 + insert3)
gc.write_text(g, encoding="utf-8")

print("Chat completion changes prepared: real attachment picker, file data transport, polished chat UI")
