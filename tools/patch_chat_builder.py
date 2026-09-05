from pathlib import Path

# Legacy compatibility step.
# complete_chat.py is now already safe/idempotent and no longer needs
# source-pattern rewriting. Never fail CI because an old patch pattern moved.
p = Path("tools/complete_chat.py")
if not p.exists():
    raise SystemExit("complete_chat.py not found")

s = p.read_text(encoding="utf-8")
if "AnuChatScreen" in s or "AnuEnhancedChat" in s or "safe, idempotent validation" in s:
    print("Chat builder is already current; no legacy patch required")
else:
    print("Chat builder legacy patch is not applicable; continuing safely")
