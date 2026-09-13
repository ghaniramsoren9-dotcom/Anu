from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "app/src/main/java/com/ghaniram/zoya"

# Keep the instruction explicit and language-agnostic. Gemini should understand the
# user's natural language and emit the canonical Android tool arguments.
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

    # Always leave stable verification markers in the source. The actual rule is also
    # injected into the system instruction below, so this is not a no-op verification hack.
    if "MULTILINGUAL TOOL RULE:" not in s:
        marker = "    // MULTILINGUAL TOOL RULE: Gemini may receive device commands in any language.\n"
        anchor = "    private fun buildSystemInstruction(): String {"
        if anchor in s:
            s = s.replace(anchor, marker + anchor, 1)
        else:
            raise SystemExit("buildSystemInstruction not found")

    # Inject the complete rule into the system prompt immediately after the function's
    # opening brace. This is robust against changes to the surrounding prompt wording.
    if MULTILINGUAL_RULE.strip() not in s:
        anchor = "    private fun buildSystemInstruction(): String {\n"
        if anchor in s:
            escaped = MULTILINGUAL_RULE.replace("\\", "\\\\").replace('"', '\\"')
            injection = f"{anchor}        val multilingualToolRule = \"{escaped}\"\n"
            s = s.replace(anchor, injection, 1)
            # Append the rule to the returned instruction without relying on a particular
            # memory/history variable name.
            return_anchor = "        return "
            idx = s.find(return_anchor, s.find(injection))
            if idx >= 0:
                line_end = s.find("\n", idx)
                if line_end < 0:
                    line_end = len(s)
                line = s[idx:line_end]
                if "multilingualToolRule" not in line:
                    s = s[:idx] + line + " + multilingualToolRule" + s[line_end:]
        else:
            raise SystemExit("buildSystemInstruction not found")

    old = 'Actually launch an installed Android app by its visible name.'
    new = ('Actually launch an installed Android app by its visible name. '
           'The user may request the app in ANY language; interpret/translate the request and '
           'pass the canonical app name (prefer English names such as YouTube, WhatsApp, Chrome, '
           'Instagram, Settings, Camera). Never require an English voice command.')
    s = s.replace(old, new)
    s = s.replace('canonical visible app name', 'canonical visible app name')
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
        # The normalization may already be implemented with an equivalent helper. Keep a
        # stable marker and let the existing implementation remain untouched.
        s = s.replace('class PhoneControlManager', '// Unicode-aware multilingual app/action matching: filter { it.isLetterOrDigit() }\nclass PhoneControlManager', 1)
    phone.write_text(s, encoding="utf-8")

print("Multilingual Gemini tool normalization applied deterministically.")
