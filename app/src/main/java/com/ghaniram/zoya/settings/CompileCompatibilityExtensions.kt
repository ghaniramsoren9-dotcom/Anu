package com.ghaniram.zoya.settings

import com.ghaniram.zoya.AnuSettingsStore
import org.json.JSONArray
import org.json.JSONObject

private fun AnuSettingsStore.prefsForCompatibility(): android.content.SharedPreferences =
    javaClass.getDeclaredField("prefs").let { field ->
        field.isAccessible = true
        field.get(this) as android.content.SharedPreferences
    }

var AnuSettingsStore.triggerWifiLost: Boolean
    get() = prefsForCompatibility().getBoolean("trig_wifi_lost", false)
    set(value) { prefsForCompatibility().edit().putBoolean("trig_wifi_lost", value).apply() }

var AnuSettingsStore.triggerAirplaneModeOn: Boolean
    get() = prefsForCompatibility().getBoolean("trig_airplane_on", true)
    set(value) { prefsForCompatibility().edit().putBoolean("trig_airplane_on", value).apply() }

var AnuSettingsStore.triggerAirplaneModeOff: Boolean
    get() = prefsForCompatibility().getBoolean("trig_airplane_off", true)
    set(value) { prefsForCompatibility().edit().putBoolean("trig_airplane_off", value).apply() }

var AnuSettingsStore.triggerPhoneOnSilent: Boolean
    get() = prefsForCompatibility().getBoolean("trig_phone_silent", false)
    set(value) { prefsForCompatibility().edit().putBoolean("trig_phone_silent", value).apply() }

var AnuSettingsStore.triggerRingerBackOn: Boolean
    get() = prefsForCompatibility().getBoolean("trig_ringer_back_on", false)
    set(value) { prefsForCompatibility().edit().putBoolean("trig_ringer_back_on", value).apply() }

var AnuSettingsStore.triggerAppInstalled: Boolean
    get() = prefsForCompatibility().getBoolean("trig_app_installed", false)
    set(value) { prefsForCompatibility().edit().putBoolean("trig_app_installed", value).apply() }

var AnuSettingsStore.triggerAppUninstalled: Boolean
    get() = prefsForCompatibility().getBoolean("trig_app_uninstalled", false)
    set(value) { prefsForCompatibility().edit().putBoolean("trig_app_uninstalled", value).apply() }

var AnuSettingsStore.mapsApiKey: String
    get() = prefsForCompatibility().getString("maps_api_key", "") ?: ""
    set(value) { prefsForCompatibility().edit().putString("maps_api_key", value).apply() }

var AnuSettingsStore.smartGeofencingEnabled: Boolean
    get() = prefsForCompatibility().getBoolean("smart_geofencing_enabled", false)
    set(value) { prefsForCompatibility().edit().putBoolean("smart_geofencing_enabled", value).apply() }

var AnuSettingsStore.weatherProvider: String
    get() = prefsForCompatibility().getString("weather_provider", "Open-Meteo") ?: "Open-Meteo"
    set(value) { prefsForCompatibility().edit().putString("weather_provider", value).apply() }

fun AnuSettingsStore.exportBackupJson(memories: List<String>, messagesCount: Int): String {
    val root = JSONObject()
    root.put("app", "Anu")
    root.put("version", "4.0.0")
    root.put("timestamp", System.currentTimeMillis())
    root.put("memories", JSONArray().apply { memories.forEach { put(it) } })
    root.put("conversations_count", messagesCount)
    root.put("settings", JSONObject(exportSettingsJson()))
    return root.toString(2)
}
