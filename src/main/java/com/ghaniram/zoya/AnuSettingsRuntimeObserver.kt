package com.ghaniram.zoya

import android.content.Context
import android.content.SharedPreferences

/**
 * Bridges persisted Settings changes to the running assistant session.
 * Settings screens persist through AnuSettingsStore, so this observer keeps
 * an already-running Gemini/voice session in sync even when a setting is
 * changed from a screen that does not explicitly call onSettingsUpdated().
 */
object AnuSettingsRuntimeObserver {
    private var registered = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runCatching { ZoyaSessionManager.onSettingsUpdated() }
    }

    fun register(context: Context) {
        if (registered) return
        synchronized(this) {
            if (registered) return
            val prefs = context.applicationContext.getSharedPreferences(
                "anu_settings_preferences",
                Context.MODE_PRIVATE
            )
            prefs.registerOnSharedPreferenceChangeListener(listener)
            registered = true
        }
    }
}
