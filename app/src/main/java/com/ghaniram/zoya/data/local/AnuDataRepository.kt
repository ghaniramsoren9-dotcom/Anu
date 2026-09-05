package com.ghaniram.zoya.data.local

import android.content.Context
import com.ghaniram.zoya.ChatMessage
import com.ghaniram.zoya.ChatRole
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AnuDataRepository(
    private val chatMessageDao: ChatMessageDao,
    private val userMemoryDao: UserMemoryDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val allChatMessagesFlow: Flow<List<ChatMessage>> = chatMessageDao.getAllMessagesFlow()
        .map { entities -> entities.map { it.toChatMessage() } }

    val allMemoriesFlow: Flow<List<String>> = userMemoryDao.getAllMemoriesFlow()
        .map { entities -> entities.map { it.fact } }

    suspend fun getAllChatMessages(): List<ChatMessage> = withContext(ioDispatcher) {
        chatMessageDao.getAllMessages().map { it.toChatMessage() }
    }

    suspend fun saveChatMessage(message: ChatMessage) = withContext(ioDispatcher) {
        chatMessageDao.insertMessage(ChatMessageEntity.fromChatMessage(message))
    }

    suspend fun updateChatMessage(message: ChatMessage) = withContext(ioDispatcher) {
        chatMessageDao.updateMessage(ChatMessageEntity.fromChatMessage(message))
    }

    suspend fun clearChatMessages() = withContext(ioDispatcher) {
        chatMessageDao.clearAll()
    }

    suspend fun getAllMemories(): List<String> = withContext(ioDispatcher) {
        userMemoryDao.getAllMemories().map { it.fact }
    }

    suspend fun saveMemory(fact: String): Boolean = withContext(ioDispatcher) {
        val clean = fact.trim()
        if (clean.isBlank()) return@withContext false
        val count = userMemoryDao.countMatchingFact(clean)
        if (count > 0) return@withContext true
        userMemoryDao.insertMemory(UserMemoryEntity(fact = clean))
        true
    }

    suspend fun removeMemory(query: String): Boolean = withContext(ioDispatcher) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext false
        val deleted = userMemoryDao.deleteMatching(clean)
        deleted > 0
    }

    suspend fun clearMemories() = withContext(ioDispatcher) {
        userMemoryDao.clearAll()
    }

    /**
     * Builds previous session context to supply to the AI model so it
     * retains conversation history and user knowledge across different app sessions.
     */
    suspend fun getRecentContextSummary(limit: Int = 12): String = withContext(ioDispatcher) {
        val recent = chatMessageDao.getRecentMessages(limit).reversed()
        if (recent.isEmpty()) return@withContext ""
        buildString {
            append("Recent prior conversation context from previous session:\n")
            recent.forEach { msg ->
                val speaker = if (msg.role.equals("USER", ignoreCase = true)) "User" else "Anu"
                append("- $speaker: ${msg.text}\n")
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AnuDataRepository? = null

        fun getInstance(context: Context): AnuDataRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AnuDatabase.getDatabase(context)
                val repo = AnuDataRepository(db.chatMessageDao(), db.userMemoryDao())
                INSTANCE = repo
                repo
            }
        }
    }
}
