from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "app/src/main/java/com/ghaniram/zoya"

models = PKG / "Models.kt"
s = models.read_text(encoding="utf-8")
if "data class AnuConversationSummary" not in s:
    s = s.replace("enum class ChatRole { USER, ANU, SYSTEM }\n", """enum class ChatRole { USER, ANU, SYSTEM }

data class AnuConversationSummary(
    val id: String,
    val title: String,
    val messageCount: Int,
    val updatedAt: Long
)
""", 1)
if "val chatConversations: List<AnuConversationSummary>" not in s:
    s = s.replace("    val chatMessages: List<ChatMessage> = emptyList(),\n", "    val chatMessages: List<ChatMessage> = emptyList(),\n    val chatConversations: List<AnuConversationSummary> = emptyList(),\n    val activeConversationId: String = \"\",\n", 1)
models.write_text(s, encoding="utf-8")

session = PKG / "ZoyaSessionManager.kt"
s = session.read_text(encoding="utf-8")
if "CONVERSATION_MARKER" not in s:
    s = s.replace("    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())\n", "    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())\n    private const val CONVERSATION_MARKER = \"__ANU_CONVERSATION__\"\n    private const val ACTIVE_CONVERSATION_PREF = \"active_conversation_id\"\n", 1)
if "private fun splitConversations(" not in s:
    helper = r'''    private fun conversationMarker(id: String, title: String, time: Long = System.currentTimeMillis()) =
        ChatMessage(UUID.randomUUID().toString(), ChatRole.SYSTEM, "$CONVERSATION_MARKER|$id|$title", time)

    private fun parseConversationMarker(message: ChatMessage): Pair<String, String>? {
        if (message.role != ChatRole.SYSTEM || !message.text.startsWith("$CONVERSATION_MARKER|")) return null
        val parts = message.text.split("|", limit = 3)
        if (parts.size < 3) return null
        return parts[1] to parts[2]
    }

    private fun splitConversations(messages: List<ChatMessage>): LinkedHashMap<String, MutableList<ChatMessage>> {
        val result = linkedMapOf<String, MutableList<ChatMessage>>()
        var currentId = "legacy"
        result.getOrPut(currentId) { mutableListOf() }
        messages.sortedBy { it.timestampMillis }.forEach { message ->
            val marker = parseConversationMarker(message)
            if (marker != null) {
                currentId = marker.first
                result.getOrPut(currentId) { mutableListOf() }
            } else {
                result.getOrPut(currentId) { mutableListOf() }.add(message)
            }
        }
        if (result["legacy"].isNullOrEmpty()) result.remove("legacy")
        return result
    }

    private fun summaries(messages: List<ChatMessage>): List<AnuConversationSummary> =
        splitConversations(messages).map { (id, items) ->
            val firstUser = items.firstOrNull { it.role == ChatRole.USER }
            val title = firstUser?.text?.replace("\n", " ")?.trim()?.take(42)?.ifBlank { "Conversation" } ?: "Conversation"
            AnuConversationSummary(id, title, items.size, items.maxOfOrNull { it.timestampMillis } ?: 0L)
        }.filter { it.messageCount > 0 }.sortedByDescending { it.updatedAt }

    private fun activeConversationId(messages: List<ChatMessage>): String {
        val saved = prefs.getString(ACTIVE_CONVERSATION_PREF, null)
        val groups = splitConversations(messages)
        if (!saved.isNullOrBlank() && (groups.containsKey(saved) || messages.isEmpty())) return saved
        return groups.keys.lastOrNull() ?: UUID.randomUUID().toString()
    }

    private fun messagesForConversation(messages: List<ChatMessage>, id: String): List<ChatMessage> =
        splitConversations(messages)[id].orEmpty().filter { it.role != ChatRole.SYSTEM }

    fun newConversation() {
        ensureInitialized()
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(ACTIVE_CONVERSATION_PREF, id).apply()
        scope.launch { repository.saveChatMessage(conversationMarker(id, "Conversation")) }
        _state.update { it.copy(chatMessages = emptyList(), activeConversationId = id, isAnuResponding = false, error = null) }
    }

    fun selectConversation(id: String) {
        ensureInitialized()
        prefs.edit().putString(ACTIVE_CONVERSATION_PREF, id).apply()
        scope.launch {
            val all = repository.getAllChatMessages()
            _state.update { it.copy(chatMessages = messagesForConversation(all, id), activeConversationId = id, isAnuResponding = false) }
        }
    }

'''
    s = s.replace("    fun setLanguage(lang: ZoyaLanguage) {", helper + "    fun setLanguage(lang: ZoyaLanguage) {", 1)
old = '''        _state.value = ZoyaUiState(language = language, quote = idleQuotes[language]?.random().orEmpty(), tasks = loadTasks())
        scope.launch { runCatching { repository.allMemoriesFlow.collect { memories -> _state.update { it.copy(memories = memories) } } } }
        scope.launch { runCatching { repository.allChatMessagesFlow.collect { messages -> _state.update { it.copy(chatMessages = messages) } } } }'''
new = '''        val persisted = runBlocking(Dispatchers.IO) { repository.getAllChatMessages() }
        val activeId = activeConversationId(persisted)
        prefs.edit().putString(ACTIVE_CONVERSATION_PREF, activeId).apply()
        _state.value = ZoyaUiState(
            language = language,
            quote = idleQuotes[language]?.random().orEmpty(),
            tasks = loadTasks(),
            chatMessages = messagesForConversation(persisted, activeId),
            chatConversations = summaries(persisted),
            activeConversationId = activeId
        )
        scope.launch { runCatching { repository.allMemoriesFlow.collect { memories -> _state.update { it.copy(memories = memories) } } } }
        scope.launch { runCatching { repository.allChatMessagesFlow.collect { messages ->
            val id = prefs.getString(ACTIVE_CONVERSATION_PREF, activeId) ?: activeId
            _state.update { it.copy(chatMessages = messagesForConversation(messages, id), chatConversations = summaries(messages), activeConversationId = id) }
        } } }'''
if old not in s:
    raise SystemExit("ZoyaSessionManager initialize block not found")
s = s.replace(old, new, 1)
old = '''    fun clearChatHistory() {
        ensureInitialized()
        scope.launch { repository.clearChatMessages() }
        _state.update { it.copy(chatMessages = emptyList()) }
    }'''
new = '''    fun clearChatHistory() {
        ensureInitialized()
        scope.launch { repository.clearChatMessages() }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(ACTIVE_CONVERSATION_PREF, id).apply()
        _state.update { it.copy(chatMessages = emptyList(), chatConversations = emptyList(), activeConversationId = id) }
    }'''
s = s.replace(old, new, 1)
session.write_text(s, encoding="utf-8")

vm = PKG / "ZoyaViewModel.kt"
s = vm.read_text(encoding="utf-8")
if "fun newConversation()" not in s:
    s = s.replace("    fun clearMemories() = ZoyaSessionManager.clearMemories()\n", "    fun newConversation() = ZoyaSessionManager.newConversation()\n    fun selectConversation(id: String) = ZoyaSessionManager.selectConversation(id)\n    fun clearMemories() = ZoyaSessionManager.clearMemories()\n", 1)
vm.write_text(s, encoding="utf-8")

main = PKG / "MainActivity.kt"
s = main.read_text(encoding="utf-8")
if "onNewConversation = { viewModel.newConversation() }" not in s:
    s = s.replace("""                        AnuNavTab.CHAT -> AnuChatScreen(
                            state = state,
                            onSendMessage = { text -> viewModel.sendText(text) },
                            onVoiceClick = {""", """                        AnuNavTab.CHAT -> AnuChatScreen(
                            state = state,
                            onSendMessage = { text -> viewModel.sendText(text) },
                            onNewConversation = { viewModel.newConversation() },
                            onSelectConversation = { viewModel.selectConversation(it) },
                            onVoiceClick = {""", 1)
if "onNewConversation: () -> Unit" not in s[s.find("fun AnuChatScreen("):s.find("fun AnuChatScreen(")+500]:
    s = s.replace("""fun AnuChatScreen(
    state: ZoyaUiState,
    onSendMessage: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onClearChat: () -> Unit
) {""", """fun AnuChatScreen(
    state: ZoyaUiState,
    onSendMessage: (String) -> Unit,
    onNewConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onClearChat: () -> Unit
) {""", 1)
if "conversations = state.chatConversations" not in s:
    s = s.replace("""        AnuChatHistoryDialog(
            messages = state.chatMessages,
            onDismiss = { showChatHistoryDialog = false },""", """        AnuChatHistoryDialog(
            messages = state.chatMessages,
            conversations = state.chatConversations,
            activeConversationId = state.activeConversationId,
            onSelectConversation = { onSelectConversation(it) },
            onNewConversation = { onNewConversation(); showChatHistoryDialog = false },
            onDismiss = { showChatHistoryDialog = false },""", 1)
if 'Text("New", color = AnuPrimary' not in s:
    s=s.replace("""            Box {
                IconButton(onClick = { showMenu = true }) {""", """            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onNewConversation) {
                    Icon(Icons.Outlined.Add, null, tint = AnuPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("New", color = AnuPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Box {
                IconButton(onClick = { showMenu = true }) {""", 1)
    s=s.replace("""                )
            }
        }

        // Chat Message List or Empty Placeholder""", """                )
                }
            }
        }

        // Chat Message List or Empty Placeholder""", 1)
s=s.replace("""fun AnuChatHistoryDialog(
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {""", """fun AnuChatHistoryDialog(
    messages: List<ChatMessage>,
    conversations: List<AnuConversationSummary>,
    activeConversationId: String,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {""", 1)
if "val filteredConversations" not in s:
    s=s.replace("""    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }""", """    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }
    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }""", 1)
if 'Text("Conversations", fontSize = 12.sp' not in s:
    insert="""                    Text("Conversations", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AnuTextMuted)
                    Spacer(Modifier.height(6.dp))
                    if (filteredConversations.isEmpty()) {
                        Text("No saved conversations yet.", fontSize = 12.sp, color = AnuTextMuted, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(filteredConversations, key = { it.id }) { conversation ->
                                Surface(shape = RoundedCornerShape(12.dp), color = if (conversation.id == activeConversationId) AnuLavenderBg else AnuBackground, border = BorderStroke(1.dp, if (conversation.id == activeConversationId) AnuPrimary.copy(alpha = 0.45f) else AnuBorder), modifier = Modifier.fillMaxWidth().clickable { onSelectConversation(conversation.id) }) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = AnuPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(conversation.title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AnuTextDark, maxLines = 1)
                                            Text("${conversation.messageCount} messages • ${timeFormatter.format(Date(conversation.updatedAt))}", fontSize = 9.5.sp, color = AnuTextMuted, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onNewConversation) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp), tint = AnuPrimary)
                            Spacer(Modifier.width(4.dp)); Text("New conversation", color = AnuPrimary, fontSize = 11.5.sp)
                        }
                    }
                    Text("Messages in selected conversation", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AnuTextMuted)
                    Spacer(Modifier.height(6.dp))

"""
    s=s.replace("""                    Spacer(Modifier.height(10.dp))

                    if (filteredMessages.isEmpty()) {""", """                    Spacer(Modifier.height(10.dp))

"""+insert+"""                    if (filteredMessages.isEmpty()) {""", 1)
# Compiler reported EOF at line 3098: add the missing final Kotlin brace to the generated file.
s = s.rstrip() + "\n}"
main.write_text(s, encoding="utf-8")

print("Conversation-wise chat history applied and MainActivity brace repaired")
