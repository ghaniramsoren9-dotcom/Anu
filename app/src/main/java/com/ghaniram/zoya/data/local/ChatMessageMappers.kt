package com.ghaniram.zoya.data.local

import com.ghaniram.zoya.ChatMessage
import com.ghaniram.zoya.ChatRole

/** Domain/database mapping kept outside the Room entity for KSP compatibility. */
fun ChatMessageEntity.toChatMessage(): ChatMessage {
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

fun ChatMessageEntity.Companion.fromChatMessage(
    msg: ChatMessage,
    sessionId: String = "default_session"
): ChatMessageEntity {
    return ChatMessageEntity(
        id = msg.id,
        role = msg.role.name,
        text = msg.text,
        timestamp = msg.timestampMillis,
        sessionId = sessionId
    )
}
