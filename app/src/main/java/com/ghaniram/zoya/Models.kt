enum class ConnectionState { DISCONNECTED, CONNECTING, IDLE, LISTENING, SPEAKING }

enum class ZoyaLanguage(val code: String, val label: String) {
    ODIA("odia", "ଓଡ଼ିଆ"), ENGLISH("english", "English"), HINDI("hindi", "हिंदी"), SANTALI("santali", "ᱥᱟᱱᱛᱟᱲᱤ")
}

data class WebsiteCall(val id: String, val url: String, val name: String, val timestampMillis: Long)

data class ChatMessage(val id: String, val role: ChatRole, val text: String, val timestampMillis: Long)

enum class ChatRole { USER, ANU, SYSTEM }

data class AnuTask(
    val id: String,
    val title: String,
    val timeLabel: String,
    val isCompleted: Boolean = false
)

data class ZoyaUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val language: ZoyaLanguage = ZoyaLanguage.ODIA,
    val inputLevel: Float = 0f,
    val outputLevel: Float = 0f,
    val error: String? = null,
    val calls: List<WebsiteCall> = emptyList(),
    val memories: List<String> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val quote: String = "",
    val tasks: List<AnuTask> = listOf(
        AnuTask("1", "Study", "7:00 PM"),
        AnuTask("2", "Call", "8:30 PM"),
        AnuTask("3", "Reminder", "9:00 PM")
    ),
    val visionDescription: String = "",
    val isVisionActive: Boolean = false,
    val isAnuResponding: Boolean = false,
    val dynamicUi: DynamicUiSpec? = null,
    val voiceTone: VoiceToneSnapshot = VoiceToneSnapshot()
)
