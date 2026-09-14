from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "app/src/main/java/com/ghaniram/zoya"

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

    # Keep a stable source marker immediately before the system-instruction builder.
    marker = "    // MULTILINGUAL TOOL RULE: Gemini must understand device commands in any language.\n"
    if "MULTILINGUAL TOOL RULE:" not in s:
        anchor = "    private fun buildSystemInstruction()"
        if anchor in s:
            s = s.replace(anchor, marker + anchor, 1)

    # Strengthen the openApp tool declaration so Gemini can translate/interpret
    # an app request before producing the canonical tool argument.
    old = 'Actually launch an installed Android app by its visible name.'
    new = ('Actually launch an installed Android app by its visible name. '
           'The user may request the app in ANY language; interpret/translate the request and '
           'pass the canonical app name (prefer English names such as YouTube, WhatsApp, Chrome, '
           'Instagram, Settings, Camera). Never require an English voice command.')
    if old in s and new not in s:
        s = s.replace(old, new, 1)

    session.write_text(s, encoding="utf-8")

phone = PKG / "PhoneControlManager.kt"
if phone.exists():
    s = phone.read_text(encoding="utf-8")
    old_norm = 'private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]+"), "").trim()'
    new_norm = 'private fun normalize(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }'
    s = s.replace(old_norm, new_norm)
    old_action = 'private fun normalizeAction(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")'
    new_action = 'private fun normalizeAction(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }'
    s = s.replace(old_action, new_action)
    if 'filter { it.isLetterOrDigit() }' not in s:
        s = s.replace('class PhoneControlManager', '// Unicode-aware multilingual app/action matching.\nclass PhoneControlManager', 1)
    phone.write_text(s, encoding="utf-8")

print("Multilingual Gemini tool normalization applied.")
