from pathlib import Path

# The Chat screen is now maintained directly in MainActivity.kt.
# This workflow step used to rewrite it using a brittle exact source pattern;
# that caused CI to fail whenever the surrounding UI was refactored.
# Keep the step as a safe, idempotent validation instead of mutating source.
main = Path("app/src/main/java/com/ghaniram/zoya/MainActivity.kt")
if not main.exists():
    raise SystemExit("MainActivity.kt not found")

text = main.read_text(encoding="utf-8")
if "AnuChatScreen" not in text and "AnuEnhancedChat" not in text:
    raise SystemExit("No supported chat screen found")

print("Chat implementation already present; skipped legacy source rewrite")
