from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "app/src/main/java/com/ghaniram/zoya"

def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"Required source anchor not found: {label}")
    return text.replace(old, new, 1)

# Models: persistent conversation metadata.
p = PKG / "Models.kt"
s = p.read_text(encoding="utf-8")
if "data class AnuConversationSummary" not in s:
    s = replace_once(s, "enum class ChatRole { USER, ANU, SYSTEM }\n", """enum class ChatRole { USER, ANU, SYSTEM }

data class AnuConversationSummary(
    val id: String,
    val title: String,
    val messageCount: Int,
    val updatedAt: Long
)
""", "conversation model")
if "val chatConversations: List<AnuConversationSummary>" not in s:
    s = replace_once(s, "    val chatMessages: List<ChatMessage> = emptyList(),\n", "    val chatMessages: List<ChatMessage> = emptyList(),\n    val chatConversations: List<AnuConversationSummary> = emptyList(),\n    val activeConversationId: String = \"\",\n", "conversation state")
p.write_text(s, encoding="utf-8")

# Session manager: split the existing local message store into conversations using private system markers.
p = PKG / "ZoyaSessionManager.kt"
s = p.read_text(encoding="utf-8")
if "CONVERSATION_MARKER" not in s:
    s = replace_once(s, "    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())\n", "    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())\n    private const val CONVERSATION_MARKER = \"__ANU_CONVERSATION__\"\n    private const val ACTIVE_CONVERSATION_PREF = \"active_conversation_id\"\n", "conversation constants")
if "private fun splitConversations(" not in s:
    helper = '''    private fun conversationMarker(id: String) = ChatMessage(UUID.randomUUID().toString(), ChatRole.SYSTEM, "$CONVERSATION_MARKER|$id", System.currentTimeMillis())

    private fun parseConversationId(message: ChatMessage): String? {
        if (message.role != ChatRole.SYSTEM || !message.text.startsWith("$CONVERSATION_MARKER|")) return null
        return message.text.removePrefix("$CONVERSATION_MARKER|").trim().ifBlank { null }
    }

    private fun splitConversations(messages: List<ChatMessage>): LinkedHashMap<String, MutableList<ChatMessage>> {
        val result = linkedMapOf<String, MutableList<ChatMessage>>()
        var current = prefs.getString(ACTIVE_CONVERSATION_PREF, null) ?: "legacy"
        messages.sortedBy { it.timestampMillis }.forEach { message ->
            val marker = parseConversationId(message)
            if (marker != null) {
                current = marker
                result.getOrPut(current) { mutableListOf() }
            } else if (message.role != ChatRole.SYSTEM) {
                result.getOrPut(current) { mutableListOf() }.add(message)
            }
        }
        return result
    }

    private fun conversationSummaries(messages: List<ChatMessage>): List<AnuConversationSummary> =
        splitConversations(messages).map { (id, items) ->
            val first = items.firstOrNull { it.role == ChatRole.USER }
            val title = first?.text?.replace("\\n", " ")?.trim()?.take(42)?.ifBlank { "Conversation" } ?: "Conversation"
            AnuConversationSummary(id, title, items.size, items.maxOfOrNull { it.timestampMillis } ?: 0L)
        }.filter { it.messageCount > 0 }.sortedByDescending { it.updatedAt }

    private fun messagesForConversation(messages: List<ChatMessage>, id: String): List<ChatMessage> =
        splitConversations(messages)[id].orEmpty()

    fun newConversation() {
        ensureInitialized()
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(ACTIVE_CONVERSATION_PREF, id).apply()
        scope.launch { repository.saveChatMessage(conversationMarker(id)) }
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
    s = replace_once(s, "    fun setLanguage(lang: ZoyaLanguage) {", helper + "    fun setLanguage(lang: ZoyaLanguage) {", "session helpers")
old = '''        _state.value = ZoyaUiState(language = language, quote = idleQuotes[language]?.random().orEmpty(), tasks = loadTasks())
        scope.launch { runCatching { repository.allMemoriesFlow.collect { memories -> _state.update { it.copy(memories = memories) } } } }
        scope.launch { runCatching { repository.allChatMessagesFlow.collect { messages -> _state.update { it.copy(chatMessages = messages) } } } }'''
new = '''        val persisted = runBlocking(Dispatchers.IO) { repository.getAllChatMessages() }
        val activeId = prefs.getString(ACTIVE_CONVERSATION_PREF, null) ?: UUID.randomUUID().toString()
        prefs.edit().putString(ACTIVE_CONVERSATION_PREF, activeId).apply()
        _state.value = ZoyaUiState(
            language = language,
            quote = idleQuotes[language]?.random().orEmpty(),
            tasks = loadTasks(),
            chatMessages = messagesForConversation(persisted, activeId),
            chatConversations = conversationSummaries(persisted),
            activeConversationId = activeId
        )
        scope.launch { runCatching { repository.allMemoriesFlow.collect { memories -> _state.update { it.copy(memories = memories) } } } }
        scope.launch { runCatching { repository.allChatMessagesFlow.collect { messages ->
            val id = prefs.getString(ACTIVE_CONVERSATION_PREF, activeId) ?: activeId
            _state.update { it.copy(chatMessages = messagesForConversation(messages, id), chatConversations = conversationSummaries(messages), activeConversationId = id) }
        } } }'''
s = replace_once(s, old, new, "session initialization")
p.write_text(s, encoding="utf-8")

# ViewModel facade.
p = PKG / "ZoyaViewModel.kt"
s = p.read_text(encoding="utf-8")
if "fun newConversation()" not in s:
    s = replace_once(s, "    fun clearMemories() = ZoyaSessionManager.clearMemories()\n", "    fun newConversation() = ZoyaSessionManager.newConversation()\n    fun selectConversation(id: String) = ZoyaSessionManager.selectConversation(id)\n    fun clearMemories() = ZoyaSessionManager.clearMemories()\n", "viewmodel conversation facade")
p.write_text(s, encoding="utf-8")

# MainActivity: wire new/select conversation actions into the existing chat screen and history dialog.
p = PKG / "MainActivity.kt"
s = p.read_text(encoding="utf-8")
if "onNewConversation = { viewModel.newConversation() }" not in s:
    s = replace_once(s, """                        AnuNavTab.CHAT -> AnuChatScreen(
                            state = state,
                            onSendMessage = { text -> viewModel.sendText(text) },
                            onVoiceClick = {""", """                        AnuNavTab.CHAT -> AnuChatScreen(
                            state = state,
                            onSendMessage = { text -> viewModel.sendText(text) },
                            onNewConversation = { viewModel.newConversation() },
                            onSelectConversation = { id -> viewModel.selectConversation(id) },
                            onVoiceClick = {""", "chat screen callbacks")

s = replace_once(s, """fun AnuChatScreen(
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
) {""", "chat screen signature") if "onNewConversation: () -> Unit" not in s else s

# Add a visible New button beside the existing overflow menu without changing its nesting.
if "Text(\"New\", color = AnuPrimary" not in s:
    s = replace_once(s, """            Box {
                IconButton(onClick = { showMenu = true }) {""", """            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onNewConversation) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = AnuPrimary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(\"New\", color = AnuPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Box {
                IconButton(onClick = { showMenu = true }) {""", "new conversation button")
    # Close the Box and Row immediately before the chat empty/list section.
    s = replace_once(s, """            }
        }

        // Chat Message List or Empty Placeholder""", """                }
            }
        }

        // Chat Message List or Empty Placeholder""", "new button container closure")

# Existing dialog gets conversation summaries above its message list.
s = replace_once(s, """fun AnuChatHistoryDialog(
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {""", """fun AnuChatHistoryDialog(
    messages: List<ChatMessage>,
    conversations: List<AnuConversationSummary>,
    activeConversationId: String,
    onSelectConversation: (String) -> Unit,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {""", "history dialog signature") if "conversations: List<AnuConversationSummary>" not in s else s

if "conversations = state.chatConversations" not in s:
    s = replace_once(s, """        AnuChatHistoryDialog(
            messages = state.chatMessages,
            onDismiss = { showChatHistoryDialog = false },""", """        AnuChatHistoryDialog(
            messages = state.chatMessages,
            conversations = state.chatConversations,
            activeConversationId = state.activeConversationId,
            onSelectConversation = { id -> onSelectConversation(id) },
            onDismiss = { showChatHistoryDialog = false },""", "history dialog call")

if "val filteredConversations =" not in s:
    s = replace_once(s, """    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }
""", """    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }
    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
""", "conversation filtering")

if "Saved conversations" not in s:
    block = '''                    Text("Saved conversations", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AnuTextMuted)
                    Spacer(Modifier.height(6.dp))
                    if (filteredConversations.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredConversations, key = { it.id }) { conversation ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (conversation.id == activeConversationId) AnuLavenderBg else AnuBackground,
                                    border = BorderStroke(1.dp, if (conversation.id == activeConversationId) AnuPrimary.copy(alpha = 0.45f) else AnuBorder),
                                    modifier = Modifier.fillMaxWidth().clickable { onSelectConversation(conversation.id) }
                                ) {
                                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text(conversation.title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AnuTextDark, maxLines = 1)
                                        Text("${conversation.messageCount} messages", fontSize = 10.sp, color = AnuTextMuted)
                                    }
                                }
                            }
                        }
                    } else {
                        Text("No saved conversations yet.", fontSize = 11.5.sp, color = AnuTextMuted)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Messages in current conversation", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AnuTextMuted)
                    Spacer(Modifier.height(4.dp))

'''
    s = replace_once(s, """                    Spacer(Modifier.height(10.dp))

                    if (filteredMessages.isEmpty()) {""", """                    Spacer(Modifier.height(10.dp))

""" + block + """                    if (filteredMessages.isEmpty()) {""", "conversation list UI")

p.write_text(s, encoding="utf-8")
print("Conversation-wise history applied safely; no source rewrite should be needed after this build.")
