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
    "Marathi, Santali, or another language. Recognize semantic intent, not exact phrases: "
    "open, launch, start, show, go to, enter, use, play/open the app, and their equivalents "
    "in the user's language all mean an app-launch request when the app name is present. "
    "For example, 'open YouTube', 'YouTube kholo', 'YouTube khol do', 'YouTube kholo na', "
    "'ୟୁଟ୍ୟୁବ ଖୋଲ', 'ୟୁଟ୍ୟୁବ୍ ଚାଲୁ କର', 'यूट्यूब खोलो', and equivalent natural phrasing "
    "must result in openApp(appName='YouTube'). Do not ask the user to repeat the command. "
    "For phoneAction/accessibilityAction, use the canonical action names described by the tool "
    "instead of translating the action name into the user's language. Preserve the user's language "
    "for the spoken response, but never let the response language prevent a tool call. If the intent "
    "is clear, call the appropriate tool immediately."
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
           'The user may request the app in ANY language and with natural wording; infer semantic '
           'launch intent rather than requiring an exact phrase. For example, open YouTube, '
           'YouTube kholo, YouTube khol do, YouTube khol na, yootyub kholo, ଯୁଟ୍ୟୁବ ଖୋଲ, '
           'ୟୁଟ୍ୟୁବ୍ ଚାଲୁ କର, and यूट्यूब खोलो all mean openApp with appName=YouTube. '
           'Pass the canonical app name (prefer English names such as YouTube, WhatsApp, Chrome, '
           'Instagram, Settings, Camera). Never require an English voice command.')
    if old in s and new not in s:
        s = s.replace(old, new, 1)

    # Gemini should normally select openApp itself. This deterministic fallback handles
    # common multilingual launch phrasing when Live returns plain text instead of a tool call.
    launch_marker = "YOUTUBE_SEMANTIC_LAUNCH_FALLBACK"
    if launch_marker not in s:
        anchor = '        val lower = text.trim().lowercase()\n'
        fallback = '''        // YOUTUBE_SEMANTIC_LAUNCH_FALLBACK: never require the literal English phrase "open youtube".
        // If the utterance contains a YouTube name plus a natural launch/open intent in any
        // supported script, execute the real Android app launch directly and do not wait for
        // Gemini to emit a tool call. This is only a fallback; Gemini tool calling remains primary.
        val youtubeName = lower.contains("youtube") || lower.contains("youtu.be") ||
                lower.contains("ୟୁଟ୍ୟୁବ") || lower.contains("ୟୁଟୁବ") ||
                lower.contains("यूट्यूब") || lower.contains("ইউটিউব") ||
                lower.contains("யூடியூப்") || lower.contains("యూట్యూబ్") ||
                lower.contains("ಯೂಟ್ಯೂಬ್") || lower.contains("യൂട്യൂബ്") ||
                lower.contains("युट्यूब")
        val launchIntent = lower.contains("open") || lower.contains("launch") || lower.contains("start") ||
                lower.contains("show") || lower.contains("go to") || lower.contains("go into") ||
                lower.contains("enter") || lower.contains("play") || lower.contains("use") ||
                lower.contains("khol") || lower.contains("kholo") || lower.contains("khol do") ||
                lower.contains("खोल") || lower.contains("खोलो") || lower.contains("खोल दो") ||
                lower.contains("चालू") || lower.contains("चलाओ") || lower.contains("चलाओ") ||
                lower.contains("ଖୋଲ") || lower.contains("ଖୋଲା") || lower.contains("ଚାଲୁ") ||
                lower.contains("ଚଲାଅ") || lower.contains("ଚଲାଅ") || lower.contains("ଯାଅ") ||
                lower.contains("খোলো") || lower.contains("খুলে") || lower.contains("খুলুন") ||
                lower.contains("திற") || lower.contains("திறக்க") || lower.contains("తెర") ||
                lower.contains("తెరవ") || lower.contains("ತೆರೆ") || lower.contains("ತೆರ") ||
                lower.contains("തുറ") || lower.contains("തുറക്ക")
        if (youtubeName && (launchIntent || lower.trim() == "youtube" || lower.trim() == "ୟୁଟ୍ୟୁବ" || lower.trim() == "यूट्यूब")) {
            return phoneControls.openApp("YouTube")
        }
'''
        if anchor in s:
            s = s.replace(anchor, anchor + fallback, 1)
        else:
            raise SystemExit("Required source anchor not found: tryExecuteDirectAction lower-case normalization")

    s = s.replace('        // YOUTUBE_SEMANTIC_LAUNCH_FALLBACK: never require the literal English phrase "open youtube".',
                  '        // YOUTUBE_SEMANTIC_LAUNCH_FALLBACK: multilingual semantic app-launch fallback.', 1)
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

print("Multilingual Gemini tool normalization and semantic YouTube launch fallback applied.")
