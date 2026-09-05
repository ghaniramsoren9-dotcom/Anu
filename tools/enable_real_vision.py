from pathlib import Path
import re

path = Path("app/src/main/java/com/ghaniram/zoya/MainActivity.kt")
text = path.read_text(encoding="utf-8")

old_call = 'AnuTab.SCAN -> AnuScanner(viewModel::sendText, onRequestMic)'
new_call = 'AnuTab.SCAN -> AnuSharedLiveVisionScreen(viewModel)'
if old_call in text:
    text = text.replace(old_call, new_call, 1)
elif 'AnuTab.SCAN -> AnuCameraVisionScreen()' in text:
    text = text.replace('AnuTab.SCAN -> AnuCameraVisionScreen()', new_call, 1)
elif new_call not in text:
    # Do not touch the title when-expression (AnuTab.SCAN -> "Scanner").
    # Only a content-route branch is a valid target here.
    pattern = re.compile(r'(?m)^(\s*)AnuTab\.SCAN\s*->\s*Anu(?:CameraVisionScreen|Scanner)\b.*$')
    match = pattern.search(text)
    if not match:
        raise SystemExit("Expected Scan content route was not found in MainActivity.kt")
    indent = match.group(1)
    text = text[:match.start()] + indent + new_call + text[match.end():]

# Remove the legacy scanner implementation if it is still present.
pattern = re.compile(r'@Composable private fun AnuScanner\(.*?\n@Composable private fun ScannerChip\(.*?\n', re.DOTALL)
text, count = pattern.subn('', text, count=1)
if count > 1:
    raise SystemExit("Unexpected multiple legacy scanner implementations")

path.write_text(text, encoding="utf-8")
print("Applied shared Anu Live Vision route to MainActivity.kt")
