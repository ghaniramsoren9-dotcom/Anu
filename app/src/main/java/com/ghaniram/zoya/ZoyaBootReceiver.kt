package com.ghaniram.zoya

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ZoyaBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ZoyaForegroundService::class.java).setAction(ZoyaForegroundService.ACTION_START)) }
        }
    }
}
