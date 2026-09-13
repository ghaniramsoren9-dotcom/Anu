from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/ghaniram/zoya/MainActivity.kt"

s = MAIN.read_text(encoding="utf-8")
start = s.find("@Composable\nfun AnuChatHistoryDialog")
if start < 0:
    raise SystemExit("Premium history anchor not found: AnuChatHistoryDialog")

next_match = re.search(r"\n@Composable\nfun [A-Za-z0-9_]+", s[start + 1:])
end = start + 1 + next_match.start() if next_match else len(s)

new_function = r'''@Composable
fun AnuChatHistoryDialog(
    messages: List<ChatMessage>,
    conversations: List<AnuConversationSummary>,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, conversations) {
        val q = query.trim().lowercase()
        conversations
            .filter { q.isBlank() || it.title.lowercase().contains(q) }
            .sortedByDescending { it.updatedAt }
    }

    fun groupLabel(time: Long): String {
        val now = Calendar.getInstance()
        val date = Calendar.getInstance().apply { timeInMillis = time }
        val sameDay = now.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return "Today"
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = yesterday.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) return "Yesterday"
        return "Earlier"
    }

    fun relativeTime(time: Long): String {
        val delta = (System.currentTimeMillis() - time).coerceAtLeast(0L)
        return when {
            delta < 60_000L -> "Just now"
            delta < 3_600_000L -> "${delta / 60_000L} min ago"
            delta < 86_400_000L -> "${delta / 3_600_000L} hr ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(time))
        }
    }

    val groups = listOf("Today", "Yesterday", "Earlier")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalAnuColors.current.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFF1EDFF),
                                    LocalAnuColors.current.background
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(50.dp),
                                shape = CircleShape,
                                color = Color(0xFFE8E0FF)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.ChatBubble,
                                        contentDescription = null,
                                        tint = Color(0xFF6339E8),
                                        modifier = Modifier.size(27.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Anu", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4E28D7))
                                Text("Assistant", fontSize = 13.sp, color = Color(0xFF64748B))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFEDE7FF)) {
                                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.History, null, tint = Color(0xFF6339E8), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text("History", color = Color(0xFF6339E8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = onNewConversation) {
                                Icon(Icons.Default.Add, null, tint = Color(0xFF6339E8), modifier = Modifier.size(21.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("New", color = Color(0xFF6339E8), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Chat History", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, color = LocalAnuColors.current.textPrimary)
                            Spacer(Modifier.height(3.dp))
                            Text("Your past conversations, always here when you need them.", fontSize = 13.sp, color = LocalAnuColors.current.textSecondary)
                        }
                        Surface(shape = CircleShape, color = Color(0xFFEDE7FF)) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, "Close", tint = Color(0xFF64748B))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = LocalAnuColors.current.cardBackground,
                        border = BorderStroke(1.dp, Color(0xFFD9D1F7))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 15.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = Color(0xFF71809A), modifier = Modifier.size(23.dp))
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.weight(1f).padding(horizontal = 11.dp, vertical = 14.dp),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 15.sp, color = LocalAnuColors.current.textPrimary),
                                cursorBrush = SolidColor(Color(0xFF6339E8)),
                                decorationBox = { inner ->
                                    if (query.isBlank()) Text("Search conversations...", color = Color(0xFF71809A), fontSize = 15.sp)
                                    inner()
                                }
                            )
                            Icon(Icons.Outlined.Tune, null, tint = Color(0xFF6339E8), modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groups.forEach { group ->
                        val itemsForGroup = filtered.filter { groupLabel(it.updatedAt) == group }
                        if (itemsForGroup.isNotEmpty()) {
                            item(key = "header-$group") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            when (group) {
                                                "Today" -> Icons.Outlined.WbSunny
                                                "Yesterday" -> Icons.Outlined.NightsStay
                                                else -> Icons.Outlined.CalendarMonth
                                            }, null, tint = Color(0xFF6339E8), modifier = Modifier.size(21.dp)
                                        )
                                        Spacer(Modifier.width(9.dp))
                                        Text(group, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LocalAnuColors.current.textPrimary)
                                    }
                                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF0EBFF)) {
                                        Text("${itemsForGroup.size} conversation${if (itemsForGroup.size == 1) "" else "s"}", modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6339E8))
                                    }
                                }
                            }

                            items(itemsForGroup, key = { it.id }) { conversation ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onSelectConversation(conversation.id) },
                                    shape = RoundedCornerShape(18.dp),
                                    color = LocalAnuColors.current.cardBackground,
                                    border = BorderStroke(1.dp, Color(0xFFE1DCF2)),
                                    shadowElevation = 1.dp
                                ) {
                                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = Color(0xFFF0EBFF)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Color(0xFF6339E8), modifier = Modifier.size(23.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(13.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(conversation.title.ifBlank { "New conversation" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LocalAnuColors.current.textPrimary, maxLines = 1)
                                            Spacer(Modifier.height(3.dp))
                                            Text("Conversation history is saved on this device.", fontSize = 12.sp, color = LocalAnuColors.current.textSecondary, maxLines = 1)
                                            Spacer(Modifier.height(6.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Outlined.Schedule, null, tint = Color(0xFF7A879B), modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(relativeTime(conversation.updatedAt), fontSize = 11.sp, color = Color(0xFF71809A))
                                                Spacer(Modifier.width(10.dp))
                                                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Color(0xFF7A879B), modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("${conversation.messageCount} messages", fontSize = 11.sp, color = Color(0xFF71809A))
                                            }
                                        }
                                        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF71809A))
                                    }
                                }
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.SearchOff, null, tint = Color(0xFF8B7BBF), modifier = Modifier.size(42.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text("No conversations found", fontWeight = FontWeight.Bold, color = LocalAnuColors.current.textPrimary)
                                    Text("Try a different search", fontSize = 13.sp, color = LocalAnuColors.current.textSecondary)
                                }
                            }
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = onNewConversation,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Chat", fontWeight = FontWeight.Bold) },
                containerColor = Color(0xFF6339E8),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 28.dp)
            )
        }
    }
}
'''

MAIN.write_text(s[:start] + new_function + s[end:], encoding="utf-8")
print("Premium English conversation-wise Chat History UI applied")