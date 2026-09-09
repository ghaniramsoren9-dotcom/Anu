package com.ghaniram.zoya

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import android.net.Uri
import android.widget.Toast

/** Safe, user-visible phone controls available to Anu through Gemini tool calls. */
class PhoneControlManager(private val app: Application) {
    private val audioManager = app.getSystemService(AudioManager::class.java)
    private val cameraManager = app.getSystemService(CameraManager::class.java)
    private val deviceActions = DeviceContactLocationManager(app)
    fun openApp(appName: String): String {
        if (appName.isBlank()) return "app name is missing"; val pm = app.packageManager; val wanted = normalize(appName)
        val knownPackages = mapOf("youtube" to "com.google.android.youtube", "youtubeapp" to "com.google.android.youtube", "whatsapp" to "com.whatsapp", "instagram" to "com.instagram.android", "facebook" to "com.facebook.katana", "messenger" to "com.facebook.orca", "chrome" to "com.android.chrome", "googlechrome" to "com.android.chrome", "gmail" to "com.google.android.gm", "maps" to "com.google.android.apps.maps", "googlemaps" to "com.google.android.apps.maps", "spotify" to "com.spotify.music", "telegram" to "org.telegram.messenger", "snapchat" to "com.snapchat.android", "netflix" to "com.netflix.mediaclient", "amazon" to "in.amazon.mShop.android.shopping", "camera" to "com.android.camera2", "googlecamera" to "com.google.android.GoogleCamera", "playstore" to "com.android.vending", "clock" to "com.google.android.deskclock", "calculator" to "com.google.android.calculator", "files" to "com.google.android.documentsui", "gallery" to "com.google.android.apps.photos", "photos" to "com.google.android.apps.photos", "settings" to "com.android.settings", "phone" to "com.google.android.dialer", "messages" to "com.google.android.apps.messaging")
        val packageName = knownPackages[wanted]; if (packageName != null && launchPackage(pm, packageName)) return "opened $appName"
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER); val matches = try { pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL) } catch (_: Exception) { emptyList() }
        val exact = matches.firstOrNull { normalize(it.loadLabel(pm).toString()) == wanted }; val fuzzy = exact ?: matches.firstOrNull { info -> val label = normalize(info.loadLabel(pm).toString()); label.contains(wanted) || wanted.contains(label) }
        if (fuzzy != null) return startActivityInfo(fuzzy.activityInfo.packageName, fuzzy.activityInfo.name, fuzzy.loadLabel(pm).toString(), appName)
        if (wanted == "youtube" || wanted == "youtubeapp") return try { val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/")).apply { setPackage("com.google.android.youtube"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }; if (intent.resolveActivity(pm) != null) { app.startActivity(intent); "opened $appName" } else "I couldn't find an installed YouTube app" } catch (e: Exception) { "I couldn't open YouTube: ${e.message ?: "unknown error"}" }
        return "I couldn't find an installed app named $appName"
    }
    private fun launchPackage(pm: PackageManager, packageName: String): Boolean {
        val launchIntent = pm.getLaunchIntentForPackage(packageName); if (launchIntent != null) return try { launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED); app.startActivity(launchIntent); true } catch (_: Exception) { false }
        return try { val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName); val activity = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL).firstOrNull()?.activityInfo ?: return false; startActivityInfo(activity.packageName, activity.name, packageName, packageName).startsWith("opened") } catch (_: Exception) { false }
    }
    private fun startActivityInfo(packageName: String, activityName: String, displayName: String, requestedName: String): String = try { val explicit = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER); component = ComponentName(packageName, activityName); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) }; app.startActivity(explicit); "opened $displayName" } catch (e: Exception) { "I couldn't open $requestedName: ${e.message ?: "unknown error"}" }
    fun openCamera(): String = try { val pm = app.packageManager; val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); if (cameraIntent.resolveActivity(pm) != null) { app.startActivity(cameraIntent); "opened camera" } else { val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); if (capture.resolveActivity(pm) != null) { app.startActivity(capture); "opened camera" } else { "I couldn't find an installed camera app" } } } catch (e: Exception) { "I couldn't open camera: ${e.message ?: "unknown error"}" }
    /** Opens camera then tries Accessibility clicks for flip + shutter so multi-step selfie works better. */
    fun takeSelfie(): String {
        val openResult = openCamera()
        if (!openResult.startsWith("opened")) return openResult
        Thread.sleep(900)
        val service = AccessibilityControlService.instance
            ?: return "$openResult — Accessibility not enabled, cannot click shutter"
        // Prefer front camera for selfie
        val flipped = service.clickByText("flip") || service.clickByText("switch") || service.clickByText("front")
        if (flipped) Thread.sleep(700)
        val shutter = service.clickByText("shutter") || service.clickByText("capture") || service.clickByText("photo") || service.clickByText("take photo")
        return if (shutter) "opened camera, flipped if possible, and clicked shutter for selfie"
        else "$openResult — camera opened but shutter click could not be verified; try saying click shutter"
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
    fun setBrightness(percent: Int): String { val level = percent.coerceIn(1, 100); return try { if (!Settings.System.canWrite(app)) "I need Modify system settings permission to control brightness" else { Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (level * 255 / 100).coerceIn(1, 255)); "brightness set to $level percent" } } catch (e: Exception) { "could not set brightness: ${e.message ?: "unknown error"}" } }
    fun changeBrightness(deltaPercent: Int): String = try { if (!Settings.System.canWrite(app)) "I need Modify system settings permission to control brightness" else { val current = Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) * 100 / 255; setBrightness(current + deltaPercent) } } catch (e: Exception) { "could not change brightness: ${e.message ?: "unknown error"}" }
    fun volumeUp(): String { audioManager?.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); return "volume increased" }
    fun volumeDown(): String { audioManager?.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); return "volume decreased" }
    fun muteVolume(): String { audioManager?.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); return "volume mute toggled" }
    fun mediaAction(action: String): String { val key = when (normalizeAction(action)) { "play", "pause", "playpause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE; "next", "nexttrack" -> KeyEvent.KEYCODE_MEDIA_NEXT; "previous", "prev", "previoustrack" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS; "stop" -> KeyEvent.KEYCODE_MEDIA_STOP; else -> return "unsupported media action" }; return try { audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)); audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key)); "completed media action $action" } catch (e: Exception) { "media action failed: ${e.message ?: "unknown error"}" } }
    fun setMediaVolume(percent: Int): String { val manager = audioManager ?: return "audio service unavailable"; val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC); manager.setStreamVolume(AudioManager.STREAM_MUSIC, (percent.coerceIn(0, 100) * max / 100).coerceIn(0, max), AudioManager.FLAG_SHOW_UI); return "media volume set to ${percent.coerceIn(0, 100)} percent" }
    fun flashlight(on: Boolean): String = try { val manager = cameraManager ?: return "camera service unavailable"; val cameraId = manager.cameraIdList.firstOrNull { id -> manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return "no flashlight is available"; manager.setTorchMode(cameraId, on); if (on) "flashlight turned on" else "flashlight turned off" } catch (e: Exception) { "could not change flashlight: ${e.message ?: "unknown error"}" }
    fun setAlarm(hour: Int, minute: Int, message: String = "Anu alarm"): String { val h = hour.coerceIn(0, 23); val m = minute.coerceIn(0, 59); return try { val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply { putExtra(AlarmClock.EXTRA_HOUR, h); putExtra(AlarmClock.EXTRA_MINUTES, m); putExtra(AlarmClock.EXTRA_MESSAGE, message); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }; if (intent.resolveActivity(app.packageManager) == null) "no alarm app can handle this request" else { app.startActivity(intent); "opened alarm setup for %02d:%02d".format(h, m) } } catch (e: Exception) { "could not set alarm: ${e.message ?: "unknown error"}" } }
    fun accessibilityAction(action: String, text: String = "", value: String = ""): String { val normalized = normalizeAction(action); if (normalized == "call" || normalized == "callcontact" || normalized == "callbyname" || normalized == "directcall") return deviceActions.directCall(text); val service = AccessibilityControlService.instance ?: return "Anu phone-control accessibility is not enabled. Open Accessibility settings and enable Anu."; val ok = when (normalized) { "home", "gohome", "back", "goback", "recents", "recentapps", "openrecentapps", "notifications", "opennotifications", "quicksettings", "openquicksettings", "power", "powerdialog", "lock", "lockscreen" -> service.globalAction(action); "click", "clicktext" -> service.clickByText(text); "longclick", "longclicktext" -> service.clickByText(text, longClick = true); "settext", "settextbytext" -> service.setTextByText(text, value); "typetext", "type" -> service.typeText(value); "scrollforward", "scrolldown" -> service.scroll(true); "scrollbackward", "scrollup" -> service.scroll(false); else -> false }; val suffix = if (text.isNotBlank()) " on $text" else ""; return if (ok) "completed $action" else "could not complete $action$suffix" }
    fun openAccessibilitySettings(): String = runAction("opened Accessibility settings") { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    private fun runPanel(success: String, panelAction: String, fallbackAction: String): String = try { val panel = Intent(panelAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); if (panel.resolveActivity(app.packageManager) != null) { app.startActivity(panel); success } else runAction(success) { Intent(fallbackAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } } catch (_: Exception) { runAction(success) { Intent(fallbackAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } }
    private fun runAction(success: String, intentProvider: () -> Intent): String = try { app.startActivity(intentProvider()); success } catch (_: Exception) { Toast.makeText(app, "Couldn't open that", Toast.LENGTH_SHORT).show(); "I couldn't perform that action" }
    private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]+"), "").trim()
    private fun normalizeAction(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")
}
