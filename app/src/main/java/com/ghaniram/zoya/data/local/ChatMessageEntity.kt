package com.ghaniram.zoya.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room-only representation of a chat message.
 *
 * Keep domain-model conversion code out of the Room entity itself. This avoids
 * exposing non-Room types to Room's KSP processor while keeping the persisted
 * schema unchanged.
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val role: String, // "USER", "ANU", "SYSTEM"
    val text: String,
    val timestamp: Long,
    val sessionId: String = "default_session"
) {
    companion object
}
