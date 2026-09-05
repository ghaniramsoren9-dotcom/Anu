package com.ghaniram.zoya

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ZoyaBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED && intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val store = AnuSettingsStore.getInstance(context)
        // Do not start a microphone foreground service just because the phone booted.
        // Restore it only when the user had explicitly left Anu active.
        if (context.getSharedPreferences("anu_session", 0).getBoolean("active", false)) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ZoyaForegroundService::class.java).setAction(ZoyaForegroundService.ACTION_START)
                )
            }
        }
        // Proactive/event receivers are manifest-registered and therefore do not
        // require a microphone service to be running continuously.
        if (store.proactiveAnu && store.eventAnnouncementsMaster) {
            ZoyaSessionManager.initialize(context.applicationContext as android.app.Application)
        }
    }
}
