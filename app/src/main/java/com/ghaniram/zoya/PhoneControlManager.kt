package com.ghaniram.zoya

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast

/** Safe, user-visible phone controls available to Anu through Gemini tool calls. */
class PhoneControlManager(private val app: Application) {
    private val audioManager = app.getSystemService(AudioManager::class.java)
    private val cameraManager = app.getSystemService(CameraManager::class.java)
    private val deviceActions = DeviceContactLocationManager(app)

    fun goHome(): String {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
            AccessibilityControlService.instance?.globalAction("home")
            "navigated to home screen"
        } catch (e: Exception) {
            val ok = AccessibilityControlService.instance?.globalAction("home") == true
            if (ok) "navigated to home screen" else "could not go to home screen: ${e.message ?: "unknown error"}"
        }
    }

    fun accessibilityAction(action: String, text: String = "", value: String = ""): String {
        val normalized = normalizeAction(action)
        if (normalized == "call" || normalized == "callcontact" || normalized == "callbyname" || normalized == "directcall") return deviceActions.directCall(text)
        if (normalized in listOf("home", "gohome", "homescreen", "openhome")) return goHome()
        val service = AccessibilityControlService.instance
        if (service == null) {
            return if (normalized in listOf("home", "gohome", "homescreen")) goHome()
            else "Anu phone-control accessibility is not enabled. Open Accessibility settings and enable Anu."
        }
        val textToType = if (value.isNotBlank()) value else text
        val ok = when (normalized) {
            "home", "gohome", "back", "goback", "recents", "recentapps", "openrecentapps", "notifications", "opennotifications", "quicksettings", "openquicksettings", "power", "powerdialog", "lock", "lockscreen" -> service.globalAction(action)
            "click", "clicktext" -> service.clickByText(text)
            "longclick", "longclicktext" -> service.clickByText(text, longClick = true)
            "settext", "settextbytext" -> service.setTextByText(text, textToType)
            "typetext", "type", "write", "writetext", "paste", "pastetext", "entertext", "typenote", "typecode", "input", "insert" -> service.typeText(textToType)
            "scroll", "scrollforward", "scrolldown", "down", "swipedown", "swipeup" -> service.scroll(true)
            "scrollbackward", "scrollup", "up" -> service.scroll(false)
            else -> false
        }
        val suffix = if (text.isNotBlank() && normalized !in listOf("typetext", "type", "write", "writetext", "paste", "pastetext", "settext", "settextbytext")) " on $text" else ""
        return if (ok) "completed $action" else "could not complete $action$suffix"
    }

    /**
     * Writes long notes or documents without truncation, copies them to the clipboard,
     * and attempts to paste them directly into the currently active editor.
     */
    fun writeNote(title: String, content: String, appName: String = "keep"): String {
        val fullText = if (title.isNotBlank()) "$title\n\n$content" else content
        try {
            val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(title.ifBlank { "Note" }, fullText))
        } catch (_: Exception) {}

        val service = AccessibilityControlService.instance
        if (service != null) {
            val typed = service.typeText(fullText)
            if (typed) return "Note typed into active editor (${fullText.length} characters)"
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, fullText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            app.startActivity(Intent.createChooser(intent, "Save Note with Anu").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            "Note copied to clipboard and sharing opened: '$title' (${fullText.length} characters)"
        } catch (_: Exception) {
            "Note copied to clipboard (${fullText.length} characters): '$title'"
        }
    }

    /**
     * Writes complete, comprehensive source code files or blocks without length limitations.
     */
    fun writeCode(code: String, filename: String = "", language: String = ""): String {
        val cleanCode = code.trim()
        if (cleanCode.isEmpty()) return "Code content is empty"

        try {
            val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(filename.ifBlank { "Code" }, cleanCode))
        } catch (_: Exception) {}

        val service = AccessibilityControlService.instance
        if (service != null) {
            val pasted = service.typeText(cleanCode)
            if (pasted) return "Code written into active editor (${cleanCode.length} characters)"
        }
        return "Complete code generated and copied to clipboard (${cleanCode.length} characters)"
    }

    /**
     * Performs GitHub actions via the connected GitHub account.
     */
    fun githubAction(action: String, repo: String = "", path: String = "", content: String = "", message: String = ""): String {
        val store = AnuSettingsStore.getInstance(app)
        val token = store.githubToken
        if (token.isBlank()) {
            return "GitHub is not connected yet. Please connect your GitHub account in Anu Settings > Connectors."
        }
        val targetRepo = if (repo.isNotBlank()) repo else store.githubDefaultRepo
        return GitHubConnectorClient.executeAction(token, action, targetRepo, path, content, message)
    }

    fun openAccessibilitySettings(): String = runAction("opened Accessibility settings") { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    fun openApp(appName: String): String {
        if (appName.isBlank()) return "app name is missing"
        val clean = appName.trim().lowercase()
            .replace(Regex("^(open|launch|start|run|ଖୋଲ|khola|kholo)\\s+"), "")
            .replace(Regex("\\s+(open|kholo|khola|ଖୋଲ|app)$"), "")
            .trim()
        val target = if (clean.isNotBlank()) clean else appName.trim().lowercase()
        val pm = app.packageManager
        when (target) {
            "camera", "କ୍ୟାମେରା", "कैमरा" -> return openCamera()
            "settings", "setting", "ସେଟିଙ୍ଗ୍", "ସେଟିଂ", "सेटिंग्स" -> return openSettings()
            "phone", "dialer", "ଫୋନ୍", "କଲ୍", "फोन" -> return openPhone()
            "messages", "sms", "ମେସେଜ୍", "मैसेज" -> return openMessages()
            "wifi", "wifi settings" -> return openWifiSettings()
            "bluetooth", "bluetooth settings" -> return openBluetoothSettings()
            "sound", "sound settings" -> return openSoundSettings()
            "display", "display settings" -> return openDisplaySettings()
        }
        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube", "yt" to "com.google.android.youtube", "ୟୁଟ୍ୟୁବ୍" to "com.google.android.youtube", "ୟୁଟ୍ୟୁବ" to "com.google.android.youtube", "यूट्यूब" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp", "wa" to "com.whatsapp", "ହ୍ଵାଟ୍ସଆପ୍" to "com.whatsapp", "ହ୍ୱାଟ୍ସଆପ" to "com.whatsapp", "व्हाट्सएप" to "com.whatsapp",
            "instagram" to "com.instagram.android", "insta" to "com.instagram.android", "ଇନଷ୍ଟାଗ୍ରାମ୍" to "com.instagram.android", "इंस्टाग्राम" to "com.instagram.android",
            "facebook" to "com.facebook.katana", "fb" to "com.facebook.katana", "ଫେସବୁକ୍" to "com.facebook.katana", "फेसबुक" to "com.facebook.katana",
            "messenger" to "com.facebook.orca",
            "chrome" to "com.android.chrome", "browser" to "com.android.chrome", "କ୍ରୋମ୍" to "com.android.chrome", "କ୍ରୋମ" to "com.android.chrome", "क्रोम" to "com.android.chrome",
            "gmail" to "com.google.android.gm", "email" to "com.google.android.gm", "ମେଲ୍" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps", "googlemaps" to "com.google.android.apps.maps", "ମ୍ୟାପ୍" to "com.google.android.apps.maps",
            "photos" to "com.google.android.apps.photos", "gallery" to "com.google.android.apps.photos", "ଫଟୋ" to "com.google.android.apps.photos", "ଗ୍ୟାଲେରି" to "com.google.android.apps.photos",
            "spotify" to "com.spotify.music",
            "telegram" to "org.telegram.messenger", "ଟେଲିଗ୍ରାମ୍" to "org.telegram.messenger",
            "snapchat" to "com.snapchat.android",
            "netflix" to "com.netflix.mediaclient",
            "playstore" to "com.android.vending", "store" to "com.android.vending",
            "clock" to "com.google.android.deskclock", "calculator" to "com.google.android.calculator",
            "keep" to "com.google.android.keep", "notes" to "com.google.android.keep",
            "docs" to "com.google.android.apps.docs.editors.docs",
            "termux" to "com.termux", "acode" to "com.foxdebug.acode"
        )
        val knownPkg = knownPackages[target] ?: knownPackages[normalize(target)]
        if (knownPkg != null && launchPackage(pm, knownPkg)) return "opened $appName"
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = try { pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL) } catch (_: Exception) { emptyList() }
        val exact = matches.firstOrNull { normalize(it.loadLabel(pm).toString()) == wanted }
        val fuzzy = exact ?: matches.firstOrNull { info -> val lbl = normalize(info.loadLabel(pm).toString()); lbl.contains(normalize(target)) || normalize(target).contains(lbl) }
        if (fuzzy != null) {
            val launch = pm.getLaunchIntentForPackage(fuzzy.activityInfo.packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                app.startActivity(launch)
                return "opened ${fuzzy.loadLabel(pm)}"
            }
        }
        if (target.contains("youtube") || target.contains("ୟୁଟ୍ୟୁବ")) {
            return try { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "opened YouTube" } catch (_: Exception) { "could not open YouTube" }
        }
        if (target.contains("whatsapp") || target.contains("ହ୍ଵାଟ୍ସ")) {
            return try { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "opened WhatsApp" } catch (_: Exception) { "could not open WhatsApp" }
        }
        return "could not find or open app: $appName"
    }

    private fun launchPackage(pm: PackageManager, packageName: String): Boolean {
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            app.startActivity(launchIntent)
            true
        } catch (_: Exception) { false }
        return try {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
            val activity = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL).firstOrNull()?.activityInfo ?: return false
            val explicit = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(packageName, activity.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            app.startActivity(explicit)
            true
        } catch (_: Exception) { false }
    }

    fun openCamera(): String = try {
        val pm = app.packageManager
        val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (cameraIntent.resolveActivity(pm) != null) {
            app.startActivity(cameraIntent)
            "opened camera"
        } else {
            val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (capture.resolveActivity(pm) != null) {
                app.startActivity(capture)
                "opened camera"
            } else {
                "I couldn't find an installed camera app"
            }
        }
    } catch (e: Exception) {
        "I couldn't open camera: ${e.message ?: "unknown error"}"
    }

    fun openPhone(): String = runAction("opened phone") { Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    fun openMessages(): String = runAction("opened messages") { Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    fun openSettings(): String = runAction("opened settings") { Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    fun openWifiSettings(): String = runPanel("opened Wi-Fi controls", "android.settings.panel.action.WIFI", Settings.ACTION_WIFI_SETTINGS)
    fun openInternetPanel(): String = runPanel("opened Internet controls", "android.settings.panel.action.INTERNET_CONNECTIVITY", Settings.ACTION_WIRELESS_SETTINGS)
    fun openBluetoothSettings(): String = runPanel("opened Bluetooth controls", "android.settings.panel.action.BLUETOOTH", Settings.ACTION_BLUETOOTH_SETTINGS)
    fun openLocationSettings(): String = runAction("opened Location settings") { Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    fun openNetworkSettings(): String = runAction("opened network settings") { Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    fun openSoundSettings(): String = runAction("opened sound settings") { Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    fun openDisplaySettings(): String = runAction("opened display settings") { Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    fun openBatterySettings(): String = runAction("opened battery settings") { Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    fun openDateTimeSettings(): String = runAction("opened date and time settings") { Intent(Settings.ACTION_DATE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    fun openAppNotificationSettings(packageName: String = app.packageName): String = runAction("opened notification settings") { Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, packageName); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } }
    fun openBrightnessSettings(): String = openDisplaySettings()

    fun setBrightness(percent: Int): String {
        val level = percent.coerceIn(1, 100)
        return try {
            if (!Settings.System.canWrite(app)) "I need Modify system settings permission to control brightness"
            else {
                Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (level * 255 / 100).coerceIn(1, 255))
                "brightness set to $level percent"
            }
        } catch (e: Exception) {
            "could not set brightness: ${e.message ?: "unknown error"}"
        }
    }

    fun changeBrightness(deltaPercent: Int): String = try {
        if (!Settings.System.canWrite(app)) "I need Modify system settings permission to control brightness"
        else {
            val current = Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) * 100 / 255
            setBrightness(current + deltaPercent)
        }
    } catch (e: Exception) {
        "could not change brightness: ${e.message ?: "unknown error"}"
    }

    fun volumeUp(): String { audioManager?.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); return "volume increased" }
    fun volumeDown(): String { audioManager?.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); return "volume decreased" }
    fun muteVolume(): String { audioManager?.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); return "volume mute toggled" }

    fun mediaAction(action: String): String {
        val key = when (normalizeAction(action)) {
            "play", "pause", "playpause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next", "nexttrack" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev", "previoustrack" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return "unsupported media action"
        }
        return try {
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            "completed media action $action"
        } catch (e: Exception) {
            "media action failed: ${e.message ?: "unknown error"}"
        }
    }

    fun setMediaVolume(percent: Int): String {
        val manager = audioManager ?: return "audio service unavailable"
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, (percent.coerceIn(0, 100) * max / 100).coerceIn(0, max), AudioManager.FLAG_SHOW_UI)
        return "media volume set to ${percent.coerceIn(0, 100)} percent"
    }

    fun flashlight(on: Boolean): String = try {
        val manager = cameraManager ?: return "camera service unavailable"
        val cameraId = manager.cameraIdList.firstOrNull { id -> manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return "no flashlight is available"
        manager.setTorchMode(cameraId, on)
        if (on) "flashlight turned on" else "flashlight turned off"
    } catch (e: Exception) {
        "could not change flashlight: ${e.message ?: "unknown error"}"
    }

    fun setAlarm(hour: Int, minute: Int, message: String = "Anu alarm"): String {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, h)
                putExtra(AlarmClock.EXTRA_MINUTES, m)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(app.packageManager) == null) "no alarm app can handle this request"
            else {
                app.startActivity(intent)
                "opened alarm setup for %02d:%02d".format(h, m)
            }
        } catch (e: Exception) {
            "could not set alarm: ${e.message ?: "unknown error"}"
        }
    }

    private fun runPanel(success: String, panelAction: String, fallbackAction: String): String = try {
        val panel = Intent(panelAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (panel.resolveActivity(app.packageManager) != null) {
            app.startActivity(panel)
            success
        } else runAction(success) { Intent(fallbackAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    } catch (_: Exception) {
        runAction(success) { Intent(fallbackAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    private fun runAction(success: String, intentProvider: () -> Intent): String = try {
        app.startActivity(intentProvider())
        success
    } catch (_: Exception) {
        Toast.makeText(app, "Couldn't open that", Toast.LENGTH_SHORT).show()
        "I couldn't perform that action"
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]+"), "").trim()
    private fun normalizeAction(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")
}
