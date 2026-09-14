from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "app/src/main/java/com/ghaniram/zoya"

MULTILINGUAL_RULE = (
    " MULTILINGUAL TOOL RULE: Understand the user's request in any language and infer semantic intent, not exact phrases. "
    "Never require the user to speak English for a device action. Before calling a tool, translate/interpret the requested app or action into the canonical value expected by that tool. "
    "For openApp, identify the intended installed app from its visible name, common alias, transliteration, or natural multilingual wording, then pass the canonical app name when possible. "
    "Recognize natural launch/open intent such as open, launch, start, show, go to, enter, use, play/open the app, and their equivalents in the user's language. "
    "Examples include 'open YouTube', 'YouTube kholo', 'YouTube khol do', 'ୟୁଟ୍ୟୁବ ଖୋଲ', 'ୟୁଟ୍ୟୁବ୍ ଚାଲୁ କର', and 'यूट्यूब खोलो'. "
    "The same semantic rule applies to WhatsApp, Chrome, Instagram, Facebook, Maps, Gmail, Camera, Settings, Phone, Messages and any other installed launcher app. "
    "For phoneAction/accessibilityAction, translate the user's natural-language request to the canonical action name and arguments. "
    "If the intent is clear, call the appropriate tool immediately; do not ask the user to repeat a command merely because its wording or language differs. Preserve the user's language for the spoken response."
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

    # Strengthen the openApp tool declaration so Gemini uses semantic app intent rather than
    # matching a small list of exact English phrases.
    old = 'Actually launch an installed Android app by its visible name.'
    new = ('Actually launch an installed Android app by its visible name. '
           'The user may request ANY installed app in ANY language, script, transliteration, alias, or natural sentence. '
           'Infer semantic launch intent rather than requiring an exact phrase. Examples: open YouTube, YouTube kholo, '
           'YouTube khol do, WhatsApp kholo, Chrome kholo, Instagram खोलो, ୟୁଟ୍ୟୁବ ଖୋଲ, ହ୍ୱାଟ୍ସଆପ୍ ଖୋଲ, '
           'and equivalent natural wording all mean an app-launch request. Resolve the requested app to the installed '
           'launcher app and pass its canonical visible name whenever possible. Never require an English voice command.')
    if old in s and new not in s:
        s = s.replace(old, new, 1)

    # Add the complete semantic rule to the actual Gemini system instruction when the builder
    # uses a StringBuilder. This is intentionally guarded so repeated builds stay idempotent.
    rule_marker = "ANU_MULTILINGUAL_SEMANTIC_RULE"
    if rule_marker not in s:
        anchor = '    private fun buildSystemInstruction()'
        injection = '''    // ANU_MULTILINGUAL_SEMANTIC_RULE: injected into Gemini's real system instruction.
    private val anuMultilingualSemanticRule = """%s"""

''' % MULTILINGUAL_RULE.replace('"""', "\\\"\\\"\\\"")
        if anchor in s:
            s = s.replace(anchor, injection + anchor, 1)

        # Append the rule to the builder return value without assuming a particular prompt layout.
        # The common implementation returns a StringBuilder.toString(); replace that expression once.
        if 'return prompt.toString()' in s:
            s = s.replace('return prompt.toString()', 'prompt.append("\\n\\n").append(anuMultilingualSemanticRule)\n        return prompt.toString()', 1)
        elif 'return builder.toString()' in s:
            s = s.replace('return builder.toString()', 'builder.append("\\n\\n").append(anuMultilingualSemanticRule)\n        return builder.toString()', 1)

    # Generic deterministic fallback: when Gemini returns plain text instead of a tool call,
    # resolve the requested installed app from the user's natural sentence. This is NOT limited
    # to YouTube. Gemini tool calling remains primary; the fallback protects all launcher apps.
    generic_marker = "GENERIC_MULTILINGUAL_APP_LAUNCH_FALLBACK"
    if generic_marker not in s:
        anchor = '        val lower = text.trim().lowercase()\n'
        fallback = '''        // GENERIC_MULTILINGUAL_APP_LAUNCH_FALLBACK: semantic fallback for ANY installed app.
        // If Live returns plain text instead of a tool call, PhoneControlManager resolves the app
        // name/alias from the whole natural-language request and launches the matching launcher app.
        val directAppLaunch = phoneControls.openAppFromUserRequest(text)
        if (directAppLaunch != null) return directAppLaunch
'''
        if anchor in s:
            s = s.replace(anchor, anchor + fallback, 1)
        else:
            raise SystemExit("Required source anchor not found: tryExecuteDirectAction lower-case normalization")

    # Remove the old YouTube-only fallback if this script is being applied to a source tree that
    # still contains it, preventing duplicate launch handling.
    start = s.find('        // YOUTUBE_SEMANTIC_LAUNCH_FALLBACK:')
    if start >= 0:
        end = s.find('        if (', start)
        # The old fallback ends immediately before its if statement. Remove the full block only
        # when the marker is present; otherwise leave the source untouched.
        if end > start:
            # Find the end of the old if block by the first matching return line and closing brace.
            close = s.find('        }\n', end)
            if close > end:
                s = s[:start] + s[close + len('        }\n'):]

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

    # Add a generic semantic app resolver before openApp(). It works from installed launcher labels,
    # known aliases, and multilingual launch verbs. It intentionally refuses obvious negative requests.
    resolver_marker = "GENERIC_MULTILINGUAL_APP_RESOLVER"
    if resolver_marker not in s:
        anchor = '    fun openApp(appName: String): String {'
        resolver = '''    // GENERIC_MULTILINGUAL_APP_RESOLVER: resolve natural-language app requests for any launcher app.
    fun openAppFromUserRequest(request: String): String? {
        val raw = request.trim()
        if (raw.isBlank()) return null
        val lower = raw.lowercase()
        val compact = normalize(raw)
        if (compact.isBlank()) return null

        // Do not steal a request that explicitly says not to open/launch an app.
        val negative = listOf(
            "dontopen", "donotopen", "dontlaunch", "donotlaunch", "notopen", "notlaunch",
            "ଖୋଲନାହିଁ", "ଖୋଲନାହି", "खोलो मत", "मत खोलो", "খুলো না", "খুলবেন না"
        ).any { lower.contains(it) || compact.contains(normalize(it)) }
        if (negative) return null

        // A bare app name is also a valid launch request.
        val launchVerb = listOf(
            "open", "launch", "start", "run", "show", "go to", "go into", "enter", "use", "play",
            "khol", "kholo", "kholna", "khol do", "खोल", "खोलो", "खोल दो", "चालू", "चलाओ",
            "ଖୋଲ", "ଖୋଲା", "ଚାଲୁ", "ଚଲାଅ", "ଯାଅ", "খোলো", "খুলে", "খুলুন",
            "திற", "திறக்க", "తెర", "తెరవ", "తెరువు", "ತೆರೆ", "ತೆರ", "തുറ", "തുറക്ക"
        )
        val hasVerb = launchVerb.any { lower.contains(it) }

        // Keep aliases in sync with openApp(). This also handles common transliterations that
        // Android's installed label cannot match directly.
        val aliases = mapOf(
            "youtube" to "YouTube", "yt" to "YouTube", "ୟୁଟ୍ୟୁବ" to "YouTube", "ୟୁଟୁବ" to "YouTube", "यूट्यूब" to "YouTube",
            "whatsapp" to "WhatsApp", "wa" to "WhatsApp", "ହ୍ଵାଟ୍ସଆପ୍" to "WhatsApp", "ହ୍ୱାଟ୍ସଆପ୍" to "WhatsApp", "व्हाट्सएप" to "WhatsApp",
            "instagram" to "Instagram", "insta" to "Instagram", "ଇନଷ୍ଟାଗ୍ରାମ୍" to "Instagram", "इंस्टाग्राम" to "Instagram",
            "facebook" to "Facebook", "fb" to "Facebook", "ଫେସବୁକ୍" to "Facebook", "फेसबुक" to "Facebook",
            "messenger" to "Messenger", "chrome" to "Chrome", "browser" to "Chrome", "କ୍ରୋମ" to "Chrome", "क्रोम" to "Chrome",
            "gmail" to "Gmail", "email" to "Gmail", "ମେଲ୍" to "Gmail", "maps" to "Maps", "google maps" to "Maps", "ମ୍ୟାପ୍" to "Maps",
            "photos" to "Photos", "gallery" to "Gallery", "ଫଟୋ" to "Photos", "ଗ୍ୟାଲେରି" to "Gallery",
            "spotify" to "Spotify", "telegram" to "Telegram", "snapchat" to "Snapchat", "netflix" to "Netflix",
            "play store" to "Play Store", "playstore" to "Play Store", "clock" to "Clock", "calculator" to "Calculator",
            "keep" to "Keep", "notes" to "Notes", "docs" to "Docs", "termux" to "Termux", "acode" to "Acode",
            "camera" to "Camera", "କ୍ୟାମେରା" to "Camera", "कैमरा" to "Camera", "settings" to "Settings", "setting" to "Settings",
            "ସେଟିଙ୍ଗ୍" to "Settings", "ସେଟିଂ" to "Settings", "सेटिंग्स" to "Settings", "phone" to "Phone", "dialer" to "Phone",
            "ଫୋନ୍" to "Phone", "फोन" to "Phone", "messages" to "Messages", "sms" to "Messages", "ମେସେଜ୍" to "Messages", "मैसेज" to "Messages"
        )
        for ((alias, canonical) in aliases) {
            if (compact.contains(normalize(alias))) {
                if (hasVerb || compact == normalize(alias)) return openApp(canonical)
            }
        }

        if (!hasVerb) return null

        // Match against every installed launcher label, so newly installed apps do not need a
        // hard-coded package name or alias. This works for app labels in any Unicode script.
        val pm = app.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = try { pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL) } catch (_: Exception) { emptyList() }
        val candidates = matches.mapNotNull { info ->
            val label = info.loadLabel(pm).toString().trim()
            val normalizedLabel = normalize(label)
            if (normalizedLabel.length < 2) null else Triple(info, label, normalizedLabel)
        }

        // Prefer the longest label match to avoid choosing a short app name contained in another.
        val matched = candidates
            .filter { (_, _, label) -> compact.contains(label) || label.contains(compact) }
            .sortedByDescending { it.third.length }
            .firstOrNull()
        if (matched != null) {
            val launch = pm.getLaunchIntentForPackage(matched.first.activityInfo.packageName)
            if (launch != null) {
                return try {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    app.startActivity(launch)
                    "opened ${matched.second}"
                } catch (_: Exception) { null }
            }
        }

        // If the sentence contains an unambiguous final app phrase, pass it through the existing
        // resolver, which also has package aliases and settings/camera special cases.
        val stripped = raw
            .replace(Regex("(?i)\\b(open|launch|start|run|show|go to|go into|enter|use|play)\\b"), " ")
            .trim()
        if (stripped.isNotBlank() && stripped.length <= 80) return openApp(stripped)
        return null
    }

'''
        if anchor in s:
            s = s.replace(anchor, resolver + anchor, 1)

    phone.write_text(s, encoding="utf-8")

print("General multilingual semantic app/device command handling applied.")
