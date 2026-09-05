# Anu Roadmap #2 — Real Camera + Vision

Implementation target: real camera input must be the source for Vision actions, not Accessibility screen text.

Required behavior:
- Camera preview with runtime CAMERA permission handling.
- Front/back camera flip.
- Still image capture.
- Video capture with lifecycle-safe start/stop.
- Vision actions: Read text, Identify, Explain, Describe.
- Captured image/video must be routed to the Vision/Gemini pipeline and results rendered in the app.
- Read text must OCR/analyze the captured camera frame; it must not read the current screen/window as a substitute.
- Clear loading, success, empty-result, permission-denied, and error states.
- Preserve the stable #172 Chat implementation and existing voice/memory/accessibility behavior.
- Camera resources must be released on leaving Scan and restored safely on return.

Build acceptance:
- assembleDebug succeeds.
- APK signature verification succeeds.
- artifact name remains anu-debug-apk.

## Build checkpoint
The current source includes the CameraX preview/capture/recording screen in `AnuCameraVision.kt`; the Gradle pre-build integration routes the Scan tab to that screen. This checkpoint intentionally triggers CI without changing the stable Chat baseline.