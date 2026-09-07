package com.ghaniram.zoya

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build

/** Application bootstrap for Anu runtime services. */
class AnuApplication : Application() {
    private lateinit var settingsObserver: AnuSettingsRuntimeObserver

    override fun onCreate() {
        super.onCreate()
        // Initialize the process-wide session before background receivers can use it.
        ZoyaSessionManager.initialize(this)
        settingsObserver = AnuSettingsRuntimeObserver(this)

        // Manifest receivers are restricted for many implicit broadcasts on modern
        // Android. Keep a process-wide dynamic receiver as well so charger, headset,
        // Bluetooth and battery events are delivered while Anu's runtime is alive.
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction("android.bluetooth.device.action.ACL_CONNECTED")
                addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(ZoyaSystemEventReceiver(), filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(ZoyaSystemEventReceiver(), filter)
            }
        }

        // Gemini Live rotates its WebSocket connection roughly every 10 minutes.
        // Keep an explicitly active Anu voice session automatically recovered.
        LiveConnectionWatchdog.start(this)
    }
}
