from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
session = ROOT / "app/src/main/java/com/ghaniram/zoya/ZoyaSessionManager.kt"
vision = ROOT / "app/src/main/java/com/ghaniram/zoya/AnuSharedLiveVisionScreen.kt"

# The earlier build step creates ChatHistoryStore and basic persistence. This final
# pass fixes the remaining turn-boundary bug so a new Anu response never appends
# to the previous restored Anu bubble.
s = session.read_text(encoding="utf-8")
if "private var activeAssistantMessageId: String? = null" not in s:
    s = s.replace(
        "private var pendingHistoryContext = false",
        "private var pendingHistoryContext = false\n    private var activeAssistantMessageId: String? = null",
        1,
    )

# Start every user turn with a fresh assistant bubble.
s = s.replace(
    "val message = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, clean, System.currentTimeMillis())\n        chatHistoryStore.append(message)",
    "activeAssistantMessageId = null\n        val message = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, clean, System.currentTimeMillis())\n        chatHistoryStore.append(message)",
    1,
)

# Replace only the generated Live callback's append condition.
s = s.replace(
    "if (last?.role == ChatRole.ANU) {\n                        val updated = last.copy(text = last.text + text)",
    "if (last?.role == ChatRole.ANU && activeAssistantMessageId == last.id) {\n                        val updated = last.copy(text = last.text + text)",
    1,
)
s = s.replace(
    "val message = ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, text, System.currentTimeMillis())\n                        chatHistoryStore.append(message)\n                        s.copy(chatMessages = s.chatMessages + message)",
    "val message = ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, text, System.currentTimeMillis())\n                        activeAssistantMessageId = message.id\n                        chatHistoryStore.append(message)\n                        s.copy(chatMessages = s.chatMessages + message)",
    1,
)

# Vision session must survive camera lens switching. CameraX may recreate the
# camera use-cases, but the Gemini Live session remains owned by the ViewModel.
v = vision.read_text(encoding="utf-8")
v = v.replace(
    "onClick = { if (live) live = false; lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }",
    "onClick = { lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }",
    1,
)
v = v.replace("delay(1000L)", "delay(650L)", 1)

# Strong visual behavior prompt: distance is an estimate unless depth data is
# available; never invent an exact measurement. Keep the response concise.
needle = "Answer the user's spoken/text turn using the newest available frame when relevant."
replacement = needle + " When asked about distance, give a clearly labelled visual estimate (near/medium/far or approximate metres) only when visual cues support it; never present a guessed exact measurement as a physical measurement. Mention that it is an estimate when no depth sensor data is available. Describe object position, count, color, scene changes, visible text, and relative motion when relevant."
v = v.replace(needle, replacement, 1)

vision.write_text(v, encoding="utf-8")
session.write_text(s, encoding="utf-8")
print("Final Anu polish applied: persistent chat turn boundaries, non-pausing camera flip, faster vision sampling, and safer distance/scene guidance.")
