package com.ghaniram.zoya.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ghaniram.zoya.ChatMessage
import com.ghaniram.zoya.ChatRole

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val role: String, // "USER", "ANU", "SYSTEM"
    val text: String,
    val timestamp: Long,
    val sessionId: String = "default_session"
) {
    fun toChatMessage(): ChatMessage {
        val chatRole = when (role.uppercase()) {
            "USER" -> ChatRole.USER
            "SYSTEM" -> ChatRole.SYSTEM
            else -> ChatRole.ANU
        }
        return ChatMessage(
            id = id,
            role = chatRole,
            text = text,
            timestampMillis = timestamp
        )
    }

    companion object {
        fun fromChatMessage(msg: ChatMessage, sessionId: String = "default_session"): ChatMessageEntity {
            return ChatMessageEntity(
                id = msg.id,
                role = msg.role.name,
                text = msg.text,
                timestamp = msg.timestampMillis,
                sessionId = sessionId
            )
        }
    }
}
