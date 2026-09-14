import re, subprocess
from pathlib import Path

ROOT = Path("app/src/main/java/com/ghaniram/zoya")


def remove_duplicate_function(text: str, signature: str, next_marker: str) -> str:
    pattern = re.compile(r"\n    " + re.escape(signature) + r".*?(?=\n    " + re.escape(next_marker) + r")", re.S)
    matches = list(pattern.finditer(text))
    if len(matches) <= 1:
        return text
    first = matches[0]
    prefix = text[: first.end()]
    suffix = text[first.end() :]
    for m in matches[1:]:
        rel_start = m.start() - first.end()
        rel_end = m.end() - first.end()
        suffix = suffix[:rel_start] + suffix[rel_end:]
    return prefix + suffix


session = ROOT / "ZoyaSessionManager.kt"
if session.exists():
    s = session.read_text(encoding="utf-8")
    s = remove_duplicate_function(s, "private fun buildSystemInstruction(): String {", "private fun property")
    s = remove_duplicate_function(s, "private fun buildToolDeclarations(): JSONArray = JSONArray().apply {", "private fun ensureInitialized")
    session.write_text(s, encoding="utf-8")

main = ROOT / "MainActivity.kt"
if main.exists():
    m = main.read_text(encoding="utf-8")
    new_call = """    if (showChatHistoryDialog) {
        AnuChatHistoryDialog(
            messages = state.chatMessages,
            conversations = state.chatConversations,
            onSelectConversation = { id -> onSelectConversation(id); showChatHistoryDialog = false },
            onNewConversation = { onNewConversation(); showChatHistoryDialog = false },
            onDismiss = { showChatHistoryDialog = false },
            onClear = {
                onClearChat()
                showChatHistoryDialog = false
            }
        )
    }"""
    m = re.sub(
        r'if\s*\(showChatHistoryDialog\)\s*\{.*?AnuChatHistoryDialog\(.*?\n\s*\)\s*\}',
        new_call.strip(),
        m, count=1, flags=re.DOTALL
    )

    if "fun AnuChatHistoryDialog" in m:
        start = m.find("@Composable\nfun AnuChatHistoryDialog")
        next_match = re.search(r"\n@Composable\nfun [A-Za-z0-9_]+", m[start + 1:]) if start >= 0 else None
        end = start + 1 + next_match.start() if next_match else len(m)
        history = m[start:end]
        history = re.sub(r"(?<![A-Za-z0-9_.])Surface\(", "androidx.compose.material3.Surface(", history)
        m = m[:start] + history + m[end:]

    main.write_text(m, encoding="utf-8")
    print("repair_compile.py completed")

# Diagnostic check
try:
    res = subprocess.run(["./gradlew", "compileDebugKotlin", "--stacktrace"], capture_output=True, text=True)
    if res.returncode != 0:
        print("=== COMPILE FAILED IN repair_compile.py ===")
        all_lines = (res.stdout + "\n" + res.stderr).splitlines()
        e_lines = [l for l in all_lines if l.strip().startswith("e:") or "error:" in l.lower() or "exception" in l.lower() or "failed" in l.lower()]
        summary = "\n".join(e_lines[:50])
        error_log = f"Exit code: {res.returncode}\n\nERRORS:\n{summary}\n\nFULL_STDOUT_TAIL:\n{res.stdout[-2000:]}\n\nFULL_STDERR_TAIL:\n{res.stderr[-2000:]}"
        with open("COMPILE_ERROR.txt", "w") as f:
            f.write(error_log)
        subprocess.run(["git", "config", "user.name", "github-actions[bot]"])
        subprocess.run(["git", "config", "user.email", "github-actions[bot]@users.noreply.github.com"])
        subprocess.run(["git", "checkout", "-B", "ci-compile-error"])
        subprocess.run(["git", "add", "COMPILE_ERROR.txt"])
        subprocess.run(["git", "commit", "-m", "ci: capture compilation error log"])
        subprocess.run(["git", "push", "-f", "origin", "ci-compile-error"])
except Exception as e:
    print("Diagnostic hook error:", e)
