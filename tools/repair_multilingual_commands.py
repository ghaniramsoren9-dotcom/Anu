from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "app/src/main/java/com/ghaniram/zoya"

# The Live model must translate the user's natural-language request into the
# canonical tool arguments expected by Android. This keeps tool calls stable
# even when the user speaks Odia, Hindi, English, or another supported language.
MULTILINGUAL_RULE = (
    " MULTILINGUAL TOOL RULE: Understand the user's request in any language. "
    "Never require the user to speak English for a device action. Before calling a tool, "
    "translate/interpret the requested app or action into the canonical value expected by "
    "that tool. For openApp, appName MUST be the canonical visible app name in simple English "
    "when possible (for example YouTube, WhatsApp, Chrome, Instagram, Settings, Camera), "
    "even if the user says it in Odia, Hindi, Bengali, Tamil, Telugu, Kannada, Malayalam, "
    "Marathi, Santali, or another language. For phoneAction/accessibilityAction, use the "
    "canonical action names described by the tool instead of translating the action name into "
    "the user's language. Preserve the user's language for the spoken response, but never let "
    "the response language prevent a tool call. If the intent is clear, call the appropriate "
    "tool immediately rather than asking the user to repeat it in English."
)

session = PKG / "ZoyaSessionManager.kt"
if session.exists():
    s = session.read_text(encoding="utf-8")
    if "MULTILINGUAL TOOL RULE:" not in s:
        # Inject into the final system-instruction string without depending on
        # the exact wording of the surrounding personality instructions.
        marker = "$memoryText$historyText"
        if marker in s:
            s = s.replace(marker, MULTILINGUAL_RULE + marker, 1)
        else:
            # Fallback: append to the first buildSystemInstruction return string.
            start = s.find("private fun buildSystemInstruction(): String")
            if start < 0:
                raise SystemExit("buildSystemInstruction not found")
            ret = s.find("return ", start)
            if ret < 0:
                raise SystemExit("buildSystemInstruction return not found")
            line_end = s.find("\n", ret)
            if line_end < 0:
                line_end = len(s)
            line = s[ret:line_end]
            if line.rstrip().endswith('"'):
                line = line.rstrip()[:-1] + MULTILINGUAL_RULE.replace('"', '\\"') + '"'
                s = s[:ret] + line + s[line_end:]

    # Strengthen the openApp function declaration itself. The model sees this
    # description when deciding whether/how to call the function.
    old = 'Actually launch an installed Android app by its visible name.'
    new = ('Actually launch an installed Android app by its visible name. '
           'The user may request the app in ANY language; interpret/translate the request and '
           'pass the canonical app name (prefer English names such as YouTube, WhatsApp, Chrome, '
           'Instagram, Settings, Camera). Never require an English voice command.')
    s = s.replace(old, new)
    session.write_text(s, encoding="utf-8")

# The conversation-history integration script can regenerate PhoneControlManager.
# Make its runtime normalizer Unicode-aware so native-script app names can also
# match Android's localized app labels.
phone = PKG / "PhoneControlManager.kt"
if phone.exists():
    s = phone.read_text(encoding="utf-8")
    old_norm = 'private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]+"), "").trim()'
    new_norm = 'private fun normalize(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }'
    s = s.replace(old_norm, new_norm)
    old_action = 'private fun normalizeAction(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")'
    new_action = 'private fun normalizeAction(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }'
    s = s.replace(old_action, new_action)
    phone.write_text(s, encoding="utf-8")

print("Multilingual Gemini tool normalization applied.")
