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
        ZoyaSessionManager.initialize(this)
        settingsObserver = AnuSettingsRuntimeObserver(this)

        // Event Triggers are independent of Proactive Anu/screen awareness. Register the
        // process receiver and the modern network callback as soon as the app process exists.
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
                addAction("android.net.conn.CONNECTIVITY_CHANGE")
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(ZoyaSystemEventReceiver(), filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(ZoyaSystemEventReceiver(), filter)
            }
        }
        ProactiveEventEngine.startSystemEventMonitoring(this)
        LiveConnectionWatchdog.start(this)
    }
}
