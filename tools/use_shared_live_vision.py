from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path, old, new):
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Patch target not found: {path}: {old[:120]}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")

# Shared Live session wiring.
replace_once(
    "app/src/main/java/com/ghaniram/zoya/ZoyaSessionManager.kt",
    "    fun connect() { ensureInitialized(); prefs.edit().putBoolean(\"active\", true).apply(); startForegroundService(); connectInternal() }",
    "    fun connect() { ensureInitialized(); prefs.edit().putBoolean(\"active\", true).apply(); startForegroundService(); connectInternal() }\n    fun startVisionSession() { ensureInitialized(); prefs.edit().putBoolean(\"active\", true).apply(); startForegroundService(); if (!isConnected()) connectInternal() }\n    fun sendVisionFrame(base64Jpeg: String) { ensureInitialized(); if (base64Jpeg.isNotBlank() && isConnected()) client?.sendVideoFrame(base64Jpeg) }"
)

replace_once(
    "app/src/main/java/com/ghaniram/zoya/ZoyaSessionManager.kt",
    "private fun buildSystemInstruction(): String { val languageInstruction = when (",
    "private fun buildSystemInstruction(): String { val visionInstruction = \" Camera input is Anu's eyes. Treat camera frames as live visual context for the same Anu conversation. Never start a separate Vision conversation and never respond only because a frame arrived. Answer the user's spoken/text turn using the newest available frame when relevant.\"; val languageInstruction = visionInstruction + when ("
)

replace_once(
    "app/src/main/java/com/ghaniram/zoya/ZoyaViewModel.kt",
    "    fun connect() = ZoyaSessionManager.connect()\n    fun disconnect() = ZoyaSessionManager.disconnect()",
    "    fun connect() = ZoyaSessionManager.connect()\n    fun startVisionSession() = ZoyaSessionManager.startVisionSession()\n    fun sendVisionFrame(base64Jpeg: String) = ZoyaSessionManager.sendVisionFrame(base64Jpeg)\n    fun disconnect() = ZoyaSessionManager.disconnect()"
)

# Improve the generated Live Vision UI: entering the Vision tab starts the shared
# session automatically after camera permission is available; no second Vision
# conversation or manual LIVE toggle is required.
vision_path = ROOT / "app/src/main/java/com/ghaniram/zoya/AnuSharedLiveVisionScreen.kt"
vision = vision_path.read_text(encoding="utf-8")
vision = vision.replace(
    "var live by remember { mutableStateOf(false) }",
    "var live by remember { mutableStateOf(true) }",
    1
)
vision = vision.replace(
    "val preview = remember { PreviewView(context) }",
    "val preview = remember { PreviewView(context) }\n\n    LaunchedEffect(Unit) {\n        if (!cameraGranted) cameraPermission.launch(Manifest.permission.CAMERA)\n    }",
    1
)
vision = vision.replace(
    "val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()",
    "val imageCapture = ImageCapture.Builder()\n                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)\n                    .setTargetResolution(android.util.Size(640, 480))\n                    .setJpegQuality(55)\n                    .build()",
    1
)
vision = vision.replace(
    "Text(if (live) \"Anu is using the camera as her eyes. Talk naturally — no separate Vision chat.\" else \"Let Anu see the real world and talk with you in the same conversation.\",",
    "Text(if (live) \"Anu is seeing through the camera and listening in the same conversation. Talk naturally.\" else \"Vision is paused.\",",
    1
)
vision = vision.replace(
    "Text(if (live) \"END LIVE VISION\" else \"ANU LIVE VISION\", fontSize = 10.sp)",
    "Text(if (live) \"PAUSE VISION\" else \"RESUME VISION\", fontSize = 10.sp)",
    1
)
vision_path.write_text(vision, encoding="utf-8")

print("Shared Live Vision is automatic on entry and camera frames are reduced for lower latency.")
