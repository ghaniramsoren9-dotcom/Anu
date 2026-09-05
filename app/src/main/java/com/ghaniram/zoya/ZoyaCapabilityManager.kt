package com.ghaniram.zoya

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Opens only the Android-controlled settings/consent screens needed by Zoya capabilities. */
object ZoyaCapabilityManager {
    fun openAccessibility(context: Context) = open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    fun openOverlay(context: Context) = open(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
    fun openWriteSettings(context: Context) = open(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
    fun openNotificationAccess(context: Context) = open(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    fun openNotificationPolicy(context: Context) = open(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    fun openExactAlarms(context: Context) = if (Build.VERSION.SDK_INT >= 31) open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) else false
    fun openAllFiles(context: Context) = if (Build.VERSION.SDK_INT >= 30) open(context, Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))) else false
    fun openDeviceAdmin(context: Context) {
        val component = ComponentName(context, ZoyaDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Allows Zoya to perform supported device-management actions that you explicitly approve.")
        }
        open(context, intent)
    }

    fun hasAccessibility(context: Context): Boolean = AccessibilityControlService.instance != null
    fun hasOverlay(context: Context): Boolean = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)
    fun hasWriteSettings(context: Context): Boolean = Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(context)
    fun hasNotifications(context: Context): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33
    fun hasNotificationAccess(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.activeNotifications != null && android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) == true
    }
    fun hasNotificationPolicy(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 23) context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted else true
    fun hasExactAlarms(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() else true
    fun hasAllFiles(context: Context): Boolean = Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager()
    fun hasDeviceAdmin(context: Context): Boolean = context.getSystemService(DevicePolicyManager::class.java).isAdminActive(ComponentName(context, ZoyaDeviceAdminReceiver::class.java))

    private fun open(context: Context, intent: Intent): Boolean = try { context.startActivity(intent); true } catch (_: Exception) { false }
}
