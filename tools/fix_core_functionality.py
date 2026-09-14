from pathlib import Path
import subprocess

ROOT = Path("app/src/main/java/com/ghaniram/zoya")
MAIN = ROOT / "MainActivity.kt"
PHONE = ROOT / "PhoneControlManager.kt"

KNOWN_GOOD_MAIN = "f8eec1057d55ea87f693a17534cb349c26011b8a"

try:
    restored = subprocess.check_output(
        ["git", "show", f"{KNOWN_GOOD_MAIN}:app/src/main/java/com/ghaniram/zoya/MainActivity.kt"],
        text=True,
    )
except Exception:
    subprocess.run(["git", "fetch", "--depth=1", "origin", KNOWN_GOOD_MAIN], check=False)
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

greeting_block = '''    val context = LocalContext.current
    val settingsStore = remember { AnuSettingsStore.getInstance(context) }
    val userName = settingsStore.userName.trim().ifBlank { "Ghaniram" }
    var currentHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(Unit) {
        while (true) {
            currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            delay(30_000L)
        }
    }
'''
while restored.count(greeting_block) > 1:
    first = restored.find(greeting_block)
    second = restored.find(greeting_block, first + len(greeting_block))
    if second < 0:
        break
    restored = restored[:second] + restored[second + len(greeting_block):]

MAIN.write_text(restored, encoding="utf-8")

final_main = MAIN.read_text(encoding="utf-8")
if "class MainActivity : ComponentActivity()" not in final_main:
    raise SystemExit("ERROR: MainActivity class missing after repair")
if ".focusable()" in final_main:
    raise SystemExit("ERROR: incompatible focusable() modifier still present")

if PHONE.exists():
    phone_text = PHONE.read_text(encoding="utf-8")
    dup_block = """            "volumeup", "volume_up", "raisevolume", "soundup", "volumeincrease" -> { volumeUp(); true }
            "volumedown", "volume_down", "lowervolume", "sounddown", "volumedecrease" -> { volumeDown(); true }
            "mute", "mutevolume", "togglemute" -> { muteVolume(); true }
"""
    if phone_text.count(dup_block) > 1:
        while phone_text.count(dup_block) > 1:
            first = phone_text.find(dup_block)
            second = phone_text.find(dup_block, first + len(dup_block))
            if second < 0:
                break
            phone_text = phone_text[:second] + phone_text[second + len(dup_block):]
        PHONE.write_text(phone_text, encoding="utf-8")
        print("PhoneControlManager deduplicated successfully")

print("core functionality repair complete: canonical MainActivity restored and validated")
