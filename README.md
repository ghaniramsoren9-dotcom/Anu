# Anu — Native Android AI Assistant

A native Kotlin/Jetpack Compose Android AI voice assistant with Gemini Live, phone controls, Accessibility actions, persistent memory, and a futuristic Anu interface.

## What's included
- Futuristic Anu Core UI and live status indicators
- Voice conversation through Gemini Live API over WebSocket
- Text chat with the same live Gemini session
- Android Accessibility controls: Home, Back, Recents, notifications, quick settings, click, long-click and scrolling
- App launching and phone controls
- Persistent user-approved memory
- Foreground voice service and background session ownership
- Control Center for special Android access
- Notification access, overlay, exact alarms, system settings, storage, DND/Modes and device-admin entry points

## Setup
1. Copy `local.properties.example` to `local.properties`.
2. Set `GEMINI_API_KEY` and your Android SDK path.
3. Open the project in Android Studio/AndroidIDE and build.

## Important
Android special accesses must be explicitly granted by the user. Anu does not silently bypass Android security restrictions. Some capabilities depend on Android version, OEM policy, default-app roles, or supported APIs.

The internal Java/Kotlin package and class names currently retain the historical `com.ghaniram.zoya` namespace to preserve installation/update compatibility with the existing app. Changing the application/package ID would make Android treat it as a different app and would prevent seamless updates.
