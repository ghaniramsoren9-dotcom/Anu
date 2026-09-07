package com.ghaniram.zoya

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.BatteryManager

/** Converts supported Android broadcasts into real Anu proactive responses. */
class ZoyaSystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val store = AnuSettingsStore.getInstance(context)
        if (!store.eventAnnouncementsMaster) return

        val event = when (action) {
            Intent.ACTION_POWER_CONNECTED -> if (store.triggerChargerPlugged) "The phone charger was just plugged in." else null
            Intent.ACTION_POWER_DISCONNECTED -> if (store.triggerChargerUnplugged) "The phone charger was just unplugged." else null
            Intent.ACTION_BATTERY_LOW -> if (store.triggerBatteryLow) "Android reports that the battery is low." else null
            Intent.ACTION_BATTERY_CHANGED -> batteryEvent(store, intent)
            AudioManager.ACTION_HEADSET_PLUG -> headsetEvent(store, intent)
            android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED -> if (store.triggerBluetoothConnected) "A Bluetooth device just connected." else null
            android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (store.triggerBluetoothDisconnected) "A Bluetooth device just disconnected." else null
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> airplaneEvent(store, intent)
            AudioManager.RINGER_MODE_CHANGED_ACTION -> ringerEvent(store, intent)
            // Connectivity is handled by ConnectivityManager.NetworkCallback in
            // ProactiveEventEngine; don't announce it twice from the deprecated broadcast.
            else -> null
        } ?: return

        ProactiveEventEngine.dispatch(context, event, "$action:${intent.data?.schemeSpecificPart ?: ""}")
    }

    private fun batteryEvent(store: AnuSettingsStore, intent: Intent): String? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = if (level >= 0) (level * 100 / scale) else -1
        if (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL && store.triggerBatteryFull) return "The battery is now full ($percent%)."
        if (percent in 0..9 && store.triggerBatteryCritical) return "The battery is critically low at $percent%."
        if (percent in 10..20 && store.triggerBatteryLow) return "The battery is low at $percent%."
        return null
    }

    private fun headsetEvent(store: AnuSettingsStore, intent: Intent): String? = when (intent.getIntExtra("state", -1)) {
        1 -> if (store.triggerHeadphonesPlugged) "Headphones were just connected." else null
        0 -> if (store.triggerHeadphonesUnplugged) "Headphones were just disconnected." else null
        else -> null
    }

    private fun airplaneEvent(store: AnuSettingsStore, intent: Intent): String? {
        val enabled = intent.getBooleanExtra("state", false)
        return if (enabled) {
            if (store.triggerAirplaneModeOn) "Airplane mode was turned on." else null
        } else {
            if (store.triggerAirplaneModeOff) "Airplane mode was turned off." else null
        }
    }

    private fun ringerEvent(store: AnuSettingsStore, intent: Intent): String? {
        val mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)
        return when (mode) {
            AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE -> if (store.triggerPhoneOnSilent) "The phone entered silent/vibrate mode." else null
            AudioManager.RINGER_MODE_NORMAL -> if (store.triggerRingerBackOn) "The phone ringer is back on." else null
            else -> null
        }
    }
}
