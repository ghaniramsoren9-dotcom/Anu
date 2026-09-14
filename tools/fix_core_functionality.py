from pathlib import Path
import subprocess

ROOT = Path("app/src/main/java/com/ghaniram/zoya")
MAIN = ROOT / "MainActivity.kt"

# This commit already contains the complete MainActivity implementation,
# including the dynamic greeting/history work. Always restore it as the
# canonical base instead of layering the same patch repeatedly.
KNOWN_GOOD_MAIN = "f8eec1057d55ea87f693a17534cb349c26011b8a"

try:
    restored = subprocess.check_output(
        ["git", "show", f"{KNOWN_GOOD_MAIN}:app/src/main/java/com/ghaniram/zoya/MainActivity.kt"],
        text=True,
    )
except Exception as exc:
    raise SystemExit(f"ERROR: could not read known-good MainActivity: {exc}")

if "class MainActivity : ComponentActivity()" not in restored:
    raise SystemExit("ERROR: known-good MainActivity source is incomplete")

# The known-good source contains the intended greeting/history implementation.
# The only compiler-incompatible change required for this Compose setup is
# removing the unsupported Modifier.focusable() call.
restored = restored.replace(".focusable()", "")
MAIN.write_text(restored, encoding="utf-8")

# Defensive checks: fail before Gradle if a future edit reintroduces the
# previous corruption/duplicate-declaration class of failures.
final_main = MAIN.read_text(encoding="utf-8")
if "class MainActivity : ComponentActivity()" not in final_main:
    raise SystemExit("ERROR: MainActivity class missing after repair")
if ".focusable()" in final_main:
    raise SystemExit("ERROR: incompatible focusable() modifier still present")

# These declarations must occur exactly once inside AnuHomeScreen.
markers = [
    'val context = LocalContext.current',
    'val settingsStore = remember { AnuSettingsStore.getInstance(context) }',
    'val userName = settingsStore.userName.trim().ifBlank { "Ghaniram" }',
    'var currentHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }',
]
for marker in markers:
    if final_main.count(marker) > 1:
        raise SystemExit(f"ERROR: duplicate MainActivity declaration detected: {marker}")

print("core functionality repair complete: canonical MainActivity restored and validated")