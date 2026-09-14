from pathlib import Path
import subprocess

ROOT = Path("app/src/main/java/com/ghaniram/zoya")
MAIN = ROOT / "MainActivity.kt"
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

# Restore the canonical source on every build. Do not layer patches onto it;
# that was the cause of the repeated duplicate-declaration failures.
restored = restored.replace(".focusable()", "")
MAIN.write_text(restored, encoding="utf-8")

final_main = MAIN.read_text(encoding="utf-8")
if "class MainActivity : ComponentActivity()" not in final_main:
    raise SystemExit("ERROR: MainActivity class missing after repair")
if ".focusable()" in final_main:
    raise SystemExit("ERROR: incompatible focusable() modifier still present")

print("core functionality repair complete: canonical MainActivity restored and validated")
