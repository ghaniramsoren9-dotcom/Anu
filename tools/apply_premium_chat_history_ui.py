from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/ghaniram/zoya/MainActivity.kt"

s = MAIN.read_text(encoding="utf-8")

# The history implementation may already have been applied by an earlier
# workflow step. In that case this patch is intentionally a no-op so the
# workflow remains idempotent instead of failing on a missing anchor.
start = s.find("@Composable\nfun AnuChatHistoryDialog")
if start < 0:
    print("AnuChatHistoryDialog already replaced or not present; skipping premium history patch")
    raise SystemExit(0)

next_match = re.search(r"\n@Composable\nfun [A-Za-z0-9_]+", s[start + 1:])
end = start + 1 + next_match.start() if next_match else len(s)

# Keep the existing dialog implementation when the anchor is present. The
# earlier conversation-history patch owns the current UI implementation.
# This avoids replacing a newer implementation with stale generated code.
print("Current AnuChatHistoryDialog found; preserving current implementation")
