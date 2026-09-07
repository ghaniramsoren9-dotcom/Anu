package com.ghaniram.zoya

import android.app.Application
import android.content.Context

/** Sends proactive text directly to the Live client without creating a chat message. */
object ProactiveVoiceBridge {
    fun dispatch(context: Context, prompt: String) {
        if (prompt.isBlank()) return
        val app = context.applicationContext as? Application ?: return
        ZoyaSessionManager.initialize(app)
        if (ZoyaSessionManager.state.value.connectionState == ConnectionState.DISCONNECTED) {
            ZoyaSessionManager.connect()
        }

        // GeminiLiveClient.sendText() already queues until setupComplete. The session
        // manager intentionally keeps its Live client private, so this bridge invokes
        // the public sendText method without routing the proactive prompt through the
        // user-chat persistence path. If the implementation changes, fail safely.
        runCatching {
            val field = ZoyaSessionManager::class.java.getDeclaredField("client")
            field.isAccessible = true
            val client = field.get(ZoyaSessionManager) as? GeminiLiveClient ?: return
            client.sendText(prompt)
        }
    }
}
